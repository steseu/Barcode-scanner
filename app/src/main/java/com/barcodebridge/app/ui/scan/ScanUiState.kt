package com.barcodebridge.app.ui.scan

import com.barcodebridge.app.data.settings.ScanMode
import com.barcodebridge.app.domain.model.BarcodeFormat
import com.barcodebridge.app.domain.model.ScanRecord
import com.barcodebridge.app.domain.model.ScanSession

data class ScanUiState(
    val scanMode: ScanMode = ScanMode.SINGLE,
    val torchOn: Boolean = false,
    val zoomRatio: Float = 1f,
    val minZoomRatio: Float = 1f,
    val maxZoomRatio: Float = 4f,
    val isPaused: Boolean = false,
    val flashFeedbackEnabled: Boolean = true,
    val batchCount: Int = 0,
    val activeSessionId: Long? = null,
    val activeSessionName: String? = null,
    val sessions: List<ScanSession> = emptyList(),
    val sessionPickerVisible: Boolean = false,
    val lastScan: ScanRecord? = null,
    val manualEntryVisible: Boolean = false,
)

sealed interface ScanEvent {
    data class BarcodeSaved(val record: ScanRecord) : ScanEvent
    data object DuplicateSkipped : ScanEvent
    data object NoBarcodeInImage : ScanEvent
    data class TransportFailed(val message: String) : ScanEvent
    data class HidUnmappableChars(val characters: List<Char>, val layoutLabel: String) : ScanEvent
    data class Error(val message: String) : ScanEvent
}

data class ManualEntryFormState(
    val content: String = "",
    val format: BarcodeFormat = BarcodeFormat.QR_CODE,
)
