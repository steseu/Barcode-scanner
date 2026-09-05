package com.barcodebridge.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.barcodebridge.app.data.repository.ScanFilter
import com.barcodebridge.app.data.repository.ScanRepository
import com.barcodebridge.app.data.repository.SessionRepository
import com.barcodebridge.app.domain.model.BarcodeFormat
import com.barcodebridge.app.domain.model.ScanRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val scanRepository: ScanRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val filters = MutableStateFlow(HistoryUiState())
    private val selection = MutableStateFlow<Set<Long>>(emptySet())
    private val editing = MutableStateFlow<ScanRecord?>(null)

    private val scans = filters.flatMapLatest { state ->
        scanRepository.observeScans(
            ScanFilter(
                query = state.searchQuery,
                format = state.formatFilter,
                sessionId = state.sessionFilter,
                startInstant = state.dateRange.start,
                endInstant = state.dateRange.end,
            )
        )
    }

    val uiState: StateFlow<HistoryUiState> = combine(
        filters, scans, sessionRepository.observeAll(), selection, editing,
    ) { filterState, scans, sessions, selectedIds, editingRecord ->
        filterState.copy(
            scans = scans,
            sessions = sessions,
            selectedIds = selectedIds,
            editingRecord = editingRecord,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryUiState())

    fun setSearchQuery(query: String) {
        filters.value = filters.value.copy(searchQuery = query)
    }

    fun setFormatFilter(format: BarcodeFormat?) {
        filters.value = filters.value.copy(formatFilter = format)
    }

    fun setSessionFilter(sessionId: Long?) {
        filters.value = filters.value.copy(sessionFilter = sessionId)
    }

    fun setDateRange(range: DateRange) {
        filters.value = filters.value.copy(dateRange = range)
    }

    fun toggleSelection(id: Long) {
        selection.value = selection.value.toMutableSet().apply {
            if (!add(id)) remove(id)
        }
    }

    fun selectAll(ids: List<Long>) {
        selection.value = ids.toSet()
    }

    fun clearSelection() {
        selection.value = emptySet()
    }

    fun deleteSelected() {
        val ids = selection.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            scanRepository.deleteByIds(ids)
            clearSelection()
        }
    }

    fun deleteOne(record: ScanRecord) {
        viewModelScope.launch { scanRepository.delete(record) }
    }

    fun startEditingNote(record: ScanRecord) {
        editing.value = record
    }

    fun cancelEditingNote() {
        editing.value = null
    }

    fun saveNote(record: ScanRecord, note: String) {
        viewModelScope.launch {
            scanRepository.update(record.copy(note = note))
            editing.value = null
        }
    }

    fun createSession(name: String, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = sessionRepository.create(name)
            onCreated(id)
        }
    }

    fun renameSession(id: Long, newName: String) {
        viewModelScope.launch { sessionRepository.rename(id, newName) }
    }

    fun assignToSession(ids: List<Long>, sessionId: Long?) {
        viewModelScope.launch {
            scanRepository.getByIds(ids).forEach { record ->
                scanRepository.update(record.copy(sessionId = sessionId))
            }
        }
    }
}
