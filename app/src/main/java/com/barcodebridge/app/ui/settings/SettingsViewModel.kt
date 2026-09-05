package com.barcodebridge.app.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.barcodebridge.app.data.settings.AppLanguage
import com.barcodebridge.app.data.settings.AppSettings
import com.barcodebridge.app.data.settings.FeedbackSettings
import com.barcodebridge.app.data.settings.HidKeyboardLayout
import com.barcodebridge.app.data.settings.HidSettings
import com.barcodebridge.app.data.settings.HttpSettings
import com.barcodebridge.app.data.settings.ScanMode
import com.barcodebridge.app.data.settings.SettingsRepository
import com.barcodebridge.app.data.settings.TcpSettings
import com.barcodebridge.app.data.settings.TransferMethod
import com.barcodebridge.app.transport.hid.BluetoothHidTransport
import com.barcodebridge.app.transport.hid.HidCalibration
import com.barcodebridge.app.transport.hid.HidStatus
import com.barcodebridge.app.transport.hid.KeymapLoader
import com.barcodebridge.app.transport.http.HttpWebhookTransport
import com.barcodebridge.app.transport.tcp.TcpOfflineQueue
import com.barcodebridge.app.transport.tcp.TcpStatus
import com.barcodebridge.app.transport.tcp.TcpTransport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CalibrationUiState(
    val inProgress: Boolean = false,
    val sent: Boolean = false,
    val sentReports: List<com.barcodebridge.app.transport.hid.HidReport> = emptyList(),
    val results: List<HidCalibration.CandidateScore> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    val hidTransport: BluetoothHidTransport,
    val tcpTransport: TcpTransport,
    private val tcpOfflineQueue: TcpOfflineQueue,
    private val keymapLoader: KeymapLoader,
    private val httpWebhookTransport: HttpWebhookTransport,
    private val okHttpClient: okhttp3.OkHttpClient,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val hidStatus: StateFlow<HidStatus> = hidTransport.status
    val tcpStatus: StateFlow<TcpStatus> = tcpTransport.status

    private val _tcpQueueCount = MutableStateFlow(0)
    val tcpQueueCount: StateFlow<Int> = _tcpQueueCount.asStateFlow()

    private val _calibration = MutableStateFlow(CalibrationUiState())
    val calibration: StateFlow<CalibrationUiState> = _calibration.asStateFlow()

    fun refreshTcpQueueCount() {
        viewModelScope.launch { _tcpQueueCount.value = tcpOfflineQueue.peekCount() }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { settingsRepository.update { it.copy(language = language) } }
        val locales = when (language) {
            AppLanguage.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
            AppLanguage.GERMAN -> LocaleListCompat.forLanguageTags("de")
            AppLanguage.ENGLISH -> LocaleListCompat.forLanguageTags("en")
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    fun setDefaultScanMode(mode: ScanMode) {
        viewModelScope.launch { settingsRepository.update { it.copy(defaultScanMode = mode) } }
    }

    fun setDuplicateWindowMillis(millis: Long) {
        viewModelScope.launch { settingsRepository.update { it.copy(duplicateWindowMillis = millis) } }
    }

    fun updateFeedback(transform: (FeedbackSettings) -> FeedbackSettings) {
        viewModelScope.launch { settingsRepository.update { it.copy(feedback = transform(it.feedback)) } }
    }

    fun setTransferMethod(method: TransferMethod) {
        viewModelScope.launch { settingsRepository.update { it.copy(transferMethod = method) } }
        // Bring up the newly selected transport and stop the one that is no
        // longer used, so an unselected method doesn't keep a socket or an
        // HID registration alive in the background.
        if (method == TransferMethod.BLUETOOTH_HID) hidTransport.startRegistration()
        if (method == TransferMethod.WIFI_TCP) tcpTransport.start() else tcpTransport.stop()
    }

    fun updateHid(transform: (HidSettings) -> HidSettings) {
        viewModelScope.launch { settingsRepository.update { it.copy(hid = transform(it.hid)) } }
    }

    fun updateTcp(transform: (TcpSettings) -> TcpSettings) {
        viewModelScope.launch { settingsRepository.update { it.copy(tcp = transform(it.tcp)) } }
    }

    fun updateHttp(transform: (HttpSettings) -> HttpSettings) {
        viewModelScope.launch { settingsRepository.update { it.copy(http = transform(it.http)) } }
    }

    suspend fun testHttpWebhook(): Result<Int> {
        val http = settings.value.http
        return runCatching {
            val request = httpWebhookTransport.buildRequest(http, "BarcodeBridge test")
            okHttpClient.newCall(request).execute().use { it.code }
        }
    }

    fun sendCalibrationTestString() {
        viewModelScope.launch {
            _calibration.value = CalibrationUiState(inProgress = true)
            val currentLayout = settings.value.hid.layout
            val (result, reports) = hidTransport.sendCalibrationString(HidCalibration.TEST_STRING, currentLayout)
            _calibration.value = if (result is com.barcodebridge.app.transport.TransportResult.Failure) {
                CalibrationUiState(error = result.message)
            } else {
                CalibrationUiState(sent = true, sentReports = reports)
            }
        }
    }

    fun compareCalibration(actualObserved: String) {
        val sentReports = _calibration.value.sentReports
        if (sentReports.isEmpty()) return
        val candidateMaps = HidKeyboardLayout.entries.associateWith { keymapLoader.load(it).layoutMap }
        val ranked = HidCalibration.rank(sentReports, actualObserved, candidateMaps)
        _calibration.value = _calibration.value.copy(results = ranked)
    }

    fun resetCalibration() {
        _calibration.value = CalibrationUiState()
    }
}
