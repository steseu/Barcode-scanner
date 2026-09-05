package com.barcodebridge.app.ui.history

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.barcodebridge.app.R
import com.barcodebridge.app.domain.model.BarcodeContentClassifier
import com.barcodebridge.app.domain.model.BarcodeContentType
import com.barcodebridge.app.domain.model.ScanRecord
import com.barcodebridge.app.export.ExportDialog
import com.barcodebridge.app.export.ExportViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel(),
    exportViewModel: ExportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val exportSettings by exportViewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showExportDialog by remember { mutableStateOf(false) }
    var pendingExportFormat by remember { mutableStateOf<com.barcodebridge.app.export.ExportFormatType?>(null) }
    val unassignedLabel = stringResource(R.string.history_session_default)
    val deleteConfirmTitle = stringResource(R.string.history_delete_confirm_title)

    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri -> exportViewModel.onDocumentCreated(uri) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        launch {
            exportViewModel.events.collect { event ->
                val message = when (event) {
                    is com.barcodebridge.app.export.ExportUiEvent.Success -> context.getString(R.string.export_success)
                    is com.barcodebridge.app.export.ExportUiEvent.Failure ->
                        context.getString(R.string.export_failed, event.message)
                }
                snackbarHostState.showSnackbar(message)
            }
        }
        launch {
            exportViewModel.shareRequests.collect { uri ->
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "*/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, null))
            }
        }
    }

    var recordPendingDeletion by remember { mutableStateOf<ScanRecord?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.isSelectionMode) {
                        Text(
                            androidx.compose.ui.res.pluralStringResource(
                                R.plurals.selected_items_count,
                                uiState.selectedIds.size,
                                uiState.selectedIds.size,
                            )
                        )
                    } else {
                        Text(stringResource(R.string.nav_history))
                    }
                },
                actions = {
                    if (uiState.isSelectionMode) {
                        IconButton(onClick = { viewModel.deleteSelected() }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.history_delete))
                        }
                        IconButton(onClick = { showExportDialog = true }) {
                            Icon(Icons.Filled.Upload, contentDescription = stringResource(R.string.history_export))
                        }
                    } else {
                        IconButton(onClick = { showExportDialog = true }) {
                            Icon(Icons.Filled.Upload, contentDescription = stringResource(R.string.history_export))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::setSearchQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.history_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )

            FormatFilterRow(
                selected = uiState.formatFilter,
                onSelect = viewModel::setFormatFilter,
            )

            DateRangeFilterRow(onSelectRange = viewModel::setDateRange)

            SessionFilterRow(
                sessions = uiState.sessions,
                selectedSessionId = uiState.sessionFilter,
                onSelectSession = viewModel::setSessionFilter,
                onCreateSession = { name ->
                    viewModel.createSession(name) { newId ->
                        if (uiState.isSelectionMode) {
                            viewModel.assignToSession(uiState.selectedIds.toList(), newId)
                        }
                    }
                },
            )

            if (uiState.scans.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.scans, key = { it.id }) { record ->
                        HistoryRow(
                            record = record,
                            selected = uiState.selectedIds.contains(record.id),
                            selectionMode = uiState.isSelectionMode,
                            onClick = {
                                if (uiState.isSelectionMode) viewModel.toggleSelection(record.id)
                            },
                            onLongClick = { viewModel.toggleSelection(record.id) },
                            onCopy = {
                                copyToClipboard(context, record.content)
                                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.scan_result_copied)) }
                            },
                            onEditNote = { viewModel.startEditingNote(record) },
                            onDelete = { recordPendingDeletion = record },
                            onContentAction = { intent -> runCatching { context.startActivity(intent) } },
                        )
                    }
                }
            }
        }
    }

    uiState.editingRecord?.let { record ->
        EditNoteDialog(
            record = record,
            onDismiss = viewModel::cancelEditingNote,
            onSave = { note -> viewModel.saveNote(record, note) },
        )
    }

    recordPendingDeletion?.let { record ->
        AlertDialog(
            onDismissRequest = { recordPendingDeletion = null },
            title = { Text(deleteConfirmTitle) },
            text = { Text(stringResource(R.string.history_delete_confirm_message, 1)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteOne(record)
                    recordPendingDeletion = null
                }) { Text(stringResource(R.string.history_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { recordPendingDeletion = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showExportDialog) {
        ExportDialog(
            settings = exportSettings,
            onDismiss = { showExportDialog = false },
            onUpdateCsv = exportViewModel::updateCsvSettings,
            onUpdateTxt = exportViewModel::updateTxtSettings,
            onSetFilenameTemplate = exportViewModel::setFilenameTemplate,
            onSetShareAfterExport = exportViewModel::setShareAfterExport,
            onExport = { format ->
                val recordsToExport = if (uiState.isSelectionMode) {
                    uiState.scans.filter { uiState.selectedIds.contains(it.id) }
                } else {
                    uiState.scans
                }
                pendingExportFormat = format
                showExportDialog = false
                scope.launch {
                    val prepared = exportViewModel.prepare(
                        format = format,
                        records = recordsToExport,
                        sessions = uiState.sessions,
                        unassignedSessionLabel = unassignedLabel,
                        columnLabel = { it.name },
                    )
                    createDocumentLauncher.launch(prepared.filename)
                }
            },
        )
    }
}

@Composable
private fun FormatFilterRow(
    selected: com.barcodebridge.app.domain.model.BarcodeFormat?,
    onSelect: (com.barcodebridge.app.domain.model.BarcodeFormat?) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.history_filter_all_formats)) },
            )
        }
        items(
            com.barcodebridge.app.domain.model.BarcodeFormat.entries
                .filter { it != com.barcodebridge.app.domain.model.BarcodeFormat.UNKNOWN }
        ) { format ->
            FilterChip(
                selected = selected == format,
                onClick = { onSelect(format) },
                label = { Text(format.displayName) },
            )
        }
    }
}

private enum class DateRangePreset { ALL, TODAY, LAST_7_DAYS, LAST_30_DAYS }

@Composable
private fun DateRangeFilterRow(
    onSelectRange: (DateRange) -> Unit,
) {
    var selected by remember { mutableStateOf(DateRangePreset.ALL) }
    androidx.compose.foundation.lazy.LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(DateRangePreset.entries) { preset ->
            FilterChip(
                selected = selected == preset,
                onClick = {
                    selected = preset
                    val now = kotlinx.datetime.Clock.System.now()
                    val start = when (preset) {
                        DateRangePreset.ALL -> null
                        DateRangePreset.TODAY -> now.minus(kotlin.time.Duration.parse("1d"))
                        DateRangePreset.LAST_7_DAYS -> now.minus(kotlin.time.Duration.parse("7d"))
                        DateRangePreset.LAST_30_DAYS -> now.minus(kotlin.time.Duration.parse("30d"))
                    }
                    onSelectRange(DateRange(start, null))
                },
                label = {
                    Text(
                        when (preset) {
                            DateRangePreset.ALL -> stringResource(R.string.history_filter_all_formats)
                            DateRangePreset.TODAY -> "24h"
                            DateRangePreset.LAST_7_DAYS -> "7d"
                            DateRangePreset.LAST_30_DAYS -> "30d"
                        }
                    )
                },
            )
        }
    }
}

@Composable
private fun SessionFilterRow(
    sessions: List<com.barcodebridge.app.domain.model.ScanSession>,
    selectedSessionId: Long?,
    onSelectSession: (Long?) -> Unit,
    onCreateSession: (String) -> Unit,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    androidx.compose.foundation.lazy.LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = selectedSessionId == null,
                onClick = { onSelectSession(null) },
                label = { Text(stringResource(R.string.history_filter_all_formats)) },
            )
        }
        items(sessions, key = { it.id }) { session ->
            FilterChip(
                selected = selectedSessionId == session.id,
                onClick = { onSelectSession(session.id) },
                label = { Text(session.name) },
            )
        }
        item {
            FilterChip(
                selected = false,
                onClick = { showCreateDialog = true },
                label = { Text(stringResource(R.string.history_session_new)) },
            )
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(R.string.history_session_new)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.history_session_name_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) onCreateSession(name)
                    showCreateDialog = false
                }) { Text(stringResource(R.string.history_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun HistoryRow(
    record: ScanRecord,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCopy: () -> Unit,
    onEditNote: () -> Unit,
    onDelete: () -> Unit,
    onContentAction: (Intent) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    ListItem(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        leadingContent = {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onClick() })
            }
        },
        headlineContent = { Text(record.content, maxLines = 2) },
        supportingContent = { Text("${record.format.displayName} · ${record.timestamp}") },
        trailingContent = {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = null)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.history_copy)) }, onClick = { menuExpanded = false; onCopy() })
                    when (BarcodeContentClassifier.classify(record.content)) {
                        BarcodeContentType.URL -> DropdownMenuItem(
                            text = { Text(stringResource(R.string.history_open_url)) },
                            onClick = { menuExpanded = false; onContentAction(com.barcodebridge.app.ui.history.ScanContentActions.openUrlIntent(record.content)) },
                        )
                        BarcodeContentType.CONTACT_VCARD -> DropdownMenuItem(
                            text = { Text(stringResource(R.string.history_add_contact)) },
                            onClick = { menuExpanded = false; onContentAction(com.barcodebridge.app.ui.history.ScanContentActions.addContactIntent(context, record.content)) },
                        )
                        BarcodeContentType.CALENDAR_EVENT -> DropdownMenuItem(
                            text = { Text(stringResource(R.string.history_add_calendar_event)) },
                            onClick = { menuExpanded = false; onContentAction(com.barcodebridge.app.ui.history.ScanContentActions.addCalendarEventIntent(record.content)) },
                        )
                        BarcodeContentType.WIFI -> DropdownMenuItem(
                            text = { Text(stringResource(R.string.history_connect_wifi)) },
                            onClick = {
                                menuExpanded = false
                                when (val result = com.barcodebridge.app.ui.history.ScanContentActions.connectToWifi(context, record.content)) {
                                    is com.barcodebridge.app.ui.history.ScanContentActions.WifiConnectResult.OpenSettingsManually ->
                                        onContentAction(com.barcodebridge.app.ui.history.ScanContentActions.openWifiSettingsIntent())
                                    else -> Unit
                                }
                            },
                        )
                        BarcodeContentType.PLAIN_TEXT -> Unit
                    }
                    DropdownMenuItem(text = { Text(stringResource(R.string.history_edit_note)) }, onClick = { menuExpanded = false; onEditNote() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.history_delete)) }, onClick = { menuExpanded = false; onDelete() })
                }
            }
        },
    )
}

@Composable
private fun EditNoteDialog(record: ScanRecord, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var note by remember(record.id) { mutableStateOf(record.note) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history_edit_note)) },
        text = {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.history_note_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(note) }) { Text(stringResource(R.string.history_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    clipboardManager.setPrimaryClip(ClipData.newPlainText("BarcodeBridge", text))
}
