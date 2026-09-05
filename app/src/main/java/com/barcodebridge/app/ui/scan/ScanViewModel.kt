package com.barcodebridge.app.ui.scan

import android.content.Context
import android.net.Uri
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.barcodebridge.app.data.repository.ScanRepository
import com.barcodebridge.app.data.repository.SessionRepository
import com.barcodebridge.app.data.settings.FeedbackSettings
import com.barcodebridge.app.data.settings.ScanMode
import com.barcodebridge.app.data.settings.SettingsRepository
import com.barcodebridge.app.data.settings.TransferMethod
import com.barcodebridge.app.domain.model.BarcodeFormat
import com.barcodebridge.app.domain.model.ScanRecord
import com.barcodebridge.app.scanner.DetectedBarcode
import com.barcodebridge.app.scanner.ImageFileScanner
import com.barcodebridge.app.scanner.ScanFeedbackPlayer
import com.barcodebridge.app.transport.TransportResult
import com.barcodebridge.app.transport.TransportSender
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scanRepository: ScanRepository,
    private val sessionRepository: SessionRepository,
    private val settingsRepository: SettingsRepository,
    private val feedbackPlayer: ScanFeedbackPlayer,
    private val imageFileScanner: ImageFileScanner,
    private val transportSender: TransportSender,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ScanEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<ScanEvent> = _events.asSharedFlow()

    /**
     * The analyzer delivers ~30 frames/second and each detection would
     * otherwise start its own coroutine, so several would race past the
     * pause/duplicate checks before the first one finished writing to the
     * database - inserting the same barcode several times. Only touched from
     * the main thread (analyzer callback + viewModelScope), so a plain flag
     * is enough.
     */
    private var scanInFlight = false

    init {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            _uiState.value = _uiState.value.copy(
                scanMode = settings.defaultScanMode,
                flashFeedbackEnabled = settings.feedback.flashEnabled,
            )
        }
        viewModelScope.launch {
            sessionRepository.observeAll().collect { sessions ->
                val state = _uiState.value
                // A session deleted elsewhere must not stay selected here.
                val stillExists = sessions.any { it.id == state.activeSessionId }
                _uiState.value = state.copy(
                    sessions = sessions,
                    activeSessionId = state.activeSessionId?.takeIf { stillExists },
                    activeSessionName = state.activeSessionName?.takeIf { stillExists },
                )
            }
        }
    }

    fun switchScanMode(mode: ScanMode) {
        _uiState.value = _uiState.value.copy(scanMode = mode, isPaused = false, batchCount = 0)
    }

    fun toggleTorch(cameraControl: CameraControl?) {
        val newState = !_uiState.value.torchOn
        cameraControl?.enableTorch(newState)
        _uiState.value = _uiState.value.copy(torchOn = newState)
    }

    fun setZoomRatio(cameraControl: CameraControl?, ratio: Float) {
        cameraControl?.setZoomRatio(ratio)
        _uiState.value = _uiState.value.copy(zoomRatio = ratio)
    }

    /** Picks up the device's real zoom range instead of assuming a fixed one. */
    fun onCameraReady(camera: Camera) {
        val zoomState = camera.cameraInfo.zoomState.value ?: return
        _uiState.value = _uiState.value.copy(
            minZoomRatio = zoomState.minZoomRatio,
            maxZoomRatio = zoomState.maxZoomRatio,
            zoomRatio = zoomState.zoomRatio,
        )
    }

    fun resumeScanning() {
        _uiState.value = _uiState.value.copy(isPaused = false)
    }

    fun setActiveSession(sessionId: Long?, sessionName: String?) {
        _uiState.value = _uiState.value.copy(
            activeSessionId = sessionId,
            activeSessionName = sessionName,
            sessionPickerVisible = false,
        )
    }

    fun showSessionPicker(show: Boolean) {
        _uiState.value = _uiState.value.copy(sessionPickerVisible = show)
    }

    /** Creates a session and immediately makes it the target for subsequent scans. */
    fun createAndSelectSession(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = sessionRepository.create(name.trim())
            _uiState.value = _uiState.value.copy(
                activeSessionId = id,
                activeSessionName = name.trim(),
                sessionPickerVisible = false,
            )
        }
    }

    fun showManualEntry(show: Boolean) {
        _uiState.value = _uiState.value.copy(manualEntryVisible = show)
    }

    /** Called on the main thread from the camera analyzer's callback executor. */
    fun onBarcodesDetected(detected: List<DetectedBarcode>) {
        val first = detected.firstOrNull() ?: return
        if (_uiState.value.isPaused || scanInFlight) return
        scanInFlight = true
        viewModelScope.launch {
            try {
                handleNewScan(first.content, first.format)
            } finally {
                scanInFlight = false
            }
        }
    }

    fun scanImageFromGallery(context: Context, uri: Uri) {
        viewModelScope.launch {
            val results = runCatching { imageFileScanner.scan(context, uri) }.getOrDefault(emptyList())
            val first = results.firstOrNull()
            if (first == null) {
                _events.emit(ScanEvent.NoBarcodeInImage)
            } else {
                handleNewScan(first.content, first.format)
            }
        }
    }

    fun submitManualEntry(content: String, format: BarcodeFormat) {
        if (content.isBlank()) return
        viewModelScope.launch {
            handleNewScan(content, format)
            _uiState.value = _uiState.value.copy(manualEntryVisible = false)
        }
    }

    private suspend fun handleNewScan(content: String, format: BarcodeFormat) {
        val settings = settingsRepository.settings.first()
        val state = _uiState.value
        val now = Clock.System.now()

        if (state.scanMode == ScanMode.CONTINUOUS) {
            val isDuplicate = scanRepository.isDuplicate(
                content = content,
                format = format,
                sessionId = state.activeSessionId,
                now = now,
                withinWindow = settings.duplicateWindowMillis.milliseconds,
            )
            if (isDuplicate) {
                _events.emit(ScanEvent.DuplicateSkipped)
                return
            }
        }

        val record = ScanRecord(
            content = content,
            format = format,
            timestamp = now,
            sessionId = state.activeSessionId,
        )
        val id = scanRepository.insert(record)
        val saved = record.copy(id = id)

        playFeedback(settings.feedback)

        val current = _uiState.value
        _uiState.value = when (current.scanMode) {
            ScanMode.SINGLE -> current.copy(isPaused = true, lastScan = saved)
            ScanMode.CONTINUOUS -> current.copy(batchCount = current.batchCount + 1, lastScan = saved)
        }.copy(flashFeedbackEnabled = settings.feedback.flashEnabled)
        _events.emit(ScanEvent.BarcodeSaved(saved))

        if (settings.transferMethod != TransferMethod.NONE) {
            when (val result = transportSender.send(content)) {
                is TransportResult.Failure -> {
                    val unmappable = result.unmappableChars
                    if (unmappable != null) {
                        _events.emit(ScanEvent.HidUnmappableChars(unmappable, result.layoutLabel.orEmpty()))
                    } else {
                        _events.emit(ScanEvent.TransportFailed(result.message ?: "transport error"))
                    }
                }
                else -> Unit
            }
        }
    }

    /** Called from the "switch to Wi-Fi companion app" action offered on the HID unmappable-characters dialog. */
    fun switchToWifiTransfer() {
        viewModelScope.launch {
            settingsRepository.update { it.copy(transferMethod = TransferMethod.WIFI_TCP) }
        }
    }

    private fun playFeedback(feedback: FeedbackSettings) {
        if (feedback.soundEnabled) feedbackPlayer.playSuccessTone()
        if (feedback.vibrationEnabled) feedbackPlayer.vibrateSuccess()
        // The visual flash is driven by lastScan changing, gated on
        // ScanUiState.flashFeedbackEnabled in the UI layer.
    }
}
