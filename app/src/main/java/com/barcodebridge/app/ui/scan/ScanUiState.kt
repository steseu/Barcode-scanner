package com.barcodebridge.app.ui.scan

import com.barcodebridge.app.data.settings.ScanMode
import com.barcodebridge.app.domain.model.BarcodeFormat
import com.barcodebridge.app.domain.model.ScanRecord

data class ScanUiState(
    val scanMode: ScanMode = ScanMode.SINGLE,
    val torchOn: Boolean = false,
    val zoomRatio: Float = 1f,
    val minZoomRatio: Float = 1f,
    val maxZoomRatio: Float = 4f,
    val isPaused: Boolean = false,
    val batchCount: Int = 0,
    val activeSessionId: Long? = null,
    val activeSessionName: String? = null,
    val lastScan: ScanRecord? = null,
    val manualEntryVisible: Boolean = false,
    val hasCamera: Boolean = true,
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
