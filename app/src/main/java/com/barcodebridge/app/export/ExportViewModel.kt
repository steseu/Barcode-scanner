package com.barcodebridge.app.export

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.barcodebridge.app.data.settings.AppSettings
import com.barcodebridge.app.data.settings.CsvExportSettings
import com.barcodebridge.app.data.settings.SettingsRepository
import com.barcodebridge.app.data.settings.TxtExportSettings
import com.barcodebridge.app.domain.export.CsvExporter
import com.barcodebridge.app.domain.export.TxtExporter
import com.barcodebridge.app.domain.model.ScanRecord
import com.barcodebridge.app.domain.model.ScanSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ExportUiEvent {
    data object Success : ExportUiEvent
    data class Failure(val message: String) : ExportUiEvent
}

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val rowMapper: ExportRowMapper,
    private val exportManager: ExportManager,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    private val _events = MutableSharedFlow<ExportUiEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ExportUiEvent> = _events

    private var pending: PreparedExport? = null

    fun updateCsvSettings(transform: (CsvExportSettings) -> CsvExportSettings) {
        viewModelScope.launch { settingsRepository.update { it.copy(csv = transform(it.csv)) } }
    }

    fun updateTxtSettings(transform: (TxtExportSettings) -> TxtExportSettings) {
        viewModelScope.launch { settingsRepository.update { it.copy(txt = transform(it.txt)) } }
    }

    fun setFilenameTemplate(template: String) {
        viewModelScope.launch { settingsRepository.update { it.copy(exportFilenameTemplate = template) } }
    }

    fun setShareAfterExport(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.update { it.copy(shareAfterExport = enabled) } }
    }

    /** Builds the export bytes/filename and stores them until [onDocumentCreated] delivers the SAF Uri. */
    suspend fun prepare(
        format: ExportFormatType,
        records: List<ScanRecord>,
        sessions: List<ScanSession>,
        unassignedSessionLabel: String,
        columnLabel: (com.barcodebridge.app.data.settings.CsvColumn) -> String,
    ): PreparedExport {
        val current = settingsRepository.settings.first()
        val sessionNames = sessions.associate { it.id to it.name }
        val rows = rowMapper.map(records, sessionNames, unassignedSessionLabel)
        val bytes = when (format) {
            ExportFormatType.CSV -> CsvExporter.exportBytes(rows, current.csv, columnLabel)
            ExportFormatType.TXT -> TxtExporter.exportBytes(rows, current.txt)
        }
        val filename = FilenameTemplate.withExtension(
            FilenameTemplate.resolve(current.exportFilenameTemplate),
            format.extension,
        )
        val prepared = PreparedExport(filename, format.mimeType, bytes)
        pending = prepared
        return prepared
    }

    fun onDocumentCreated(uri: Uri?) {
        val prepared = pending
        pending = null
        if (uri == null || prepared == null) return
        viewModelScope.launch {
            runCatching { exportManager.writeToDocument(uri, prepared.bytes) }
                .onSuccess {
                    _events.emit(ExportUiEvent.Success)
                    if (settings.value.shareAfterExport) shareStaged(prepared)
                }
                .onFailure { e -> _events.emit(ExportUiEvent.Failure(e.message ?: "export failed")) }
        }
    }

    private val _shareRequests = MutableSharedFlow<Uri>(extraBufferCapacity = 4)
    val shareRequests: SharedFlow<Uri> = _shareRequests

    private suspend fun shareStaged(prepared: PreparedExport) {
        val uri = exportManager.stageForSharing(prepared.filename, prepared.bytes)
        _shareRequests.emit(uri)
    }
}
