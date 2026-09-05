package com.barcodebridge.app.ui.scan

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.barcodebridge.app.data.repository.ScanRepository
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
import kotlin.time.Duration.Companion.milliseconds
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scanRepository: ScanRepository,
    private val settingsRepository: SettingsRepository,
    private val feedbackPlayer: ScanFeedbackPlayer,
    private val imageFileScanner: ImageFileScanner,
    private val transportSender: TransportSender,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ScanEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<ScanEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            val defaultMode = settingsRepository.settings.first().defaultScanMode
            _uiState.value = _uiState.value.copy(scanMode = defaultMode)
        }
    }

    fun switchScanMode(mode: ScanMode) {
        _uiState.value = _uiState.value.copy(scanMode = mode, isPaused = false, batchCount = 0)
    }

    fun toggleTorch(camera: androidx.camera.core.CameraControl?) {
        val newState = !_uiState.value.torchOn
        camera?.enableTorch(newState)
        _uiState.value = _uiState.value.copy(torchOn = newState)
    }

    fun setZoomRatio(camera: androidx.camera.core.CameraControl?, ratio: Float) {
        camera?.setZoomRatio(ratio)
        _uiState.value = _uiState.value.copy(zoomRatio = ratio)
    }

    fun resumeScanning() {
        _uiState.value = _uiState.value.copy(isPaused = false)
    }

    fun setActiveSession(sessionId: Long?, sessionName: String?) {
        _uiState.value = _uiState.value.copy(activeSessionId = sessionId, activeSessionName = sessionName)
    }

    fun showManualEntry(show: Boolean) {
        _uiState.value = _uiState.value.copy(manualEntryVisible = show)
    }

    /** Called from the camera analysis callback thread's main-thread-posted result. */
    fun onBarcodesDetected(detected: List<DetectedBarcode>) {
        val first = detected.firstOrNull() ?: return
        if (_uiState.value.isPaused) return
        viewModelScope.launch {
            handleNewScan(first.content, first.format)
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

        _uiState.value = when (state.scanMode) {
            ScanMode.SINGLE -> state.copy(isPaused = true, lastScan = saved)
            ScanMode.CONTINUOUS -> state.copy(batchCount = state.batchCount + 1, lastScan = saved)
        }
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

    private fun playFeedback(feedback: com.barcodebridge.app.data.settings.FeedbackSettings) {
        if (feedback.soundEnabled) feedbackPlayer.playSuccessTone()
        if (feedback.vibrationEnabled) feedbackPlayer.vibrateSuccess()
        // Visual flash is driven by lastScan changing, observed by the UI layer.
    }

    override fun onCleared() {
        feedbackPlayer.release()
        super.onCleared()
    }
}
