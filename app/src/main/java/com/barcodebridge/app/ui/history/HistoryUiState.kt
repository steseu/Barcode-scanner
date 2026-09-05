package com.barcodebridge.app.ui.history

import com.barcodebridge.app.domain.model.BarcodeFormat
import com.barcodebridge.app.domain.model.ScanRecord
import com.barcodebridge.app.domain.model.ScanSession
import kotlinx.datetime.Instant

data class DateRange(val start: Instant?, val end: Instant?)

data class HistoryUiState(
    val searchQuery: String = "",
    val formatFilter: BarcodeFormat? = null,
    val sessionFilter: Long? = null,
    val dateRange: DateRange = DateRange(null, null),
    val scans: List<ScanRecord> = emptyList(),
    val sessions: List<ScanSession> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val editingRecord: ScanRecord? = null,
) {
    val isSelectionMode: Boolean get() = selectedIds.isNotEmpty()
}
