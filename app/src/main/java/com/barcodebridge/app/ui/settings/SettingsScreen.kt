package com.barcodebridge.app.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.barcodebridge.app.R
import com.barcodebridge.app.data.settings.AppLanguage
import com.barcodebridge.app.data.settings.HidKeyboardLayout
import com.barcodebridge.app.data.settings.HidSuffix
import com.barcodebridge.app.data.settings.ScanMode
import com.barcodebridge.app.data.settings.TransferMethod
import com.barcodebridge.app.data.settings.UnmappableCharAction
import com.barcodebridge.app.data.settings.WebhookPayloadFormat
import com.barcodebridge.app.export.ExportDialog
import com.barcodebridge.app.export.ExportViewModel
import com.barcodebridge.app.transport.hid.HidConnectionState
import com.barcodebridge.app.transport.tcp.TcpConnectionState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    exportViewModel: ExportViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val hidStatus by viewModel.hidStatus.collectAsStateWithLifecycle()
    val tcpStatus by viewModel.tcpStatus.collectAsStateWithLifecycle()
    val tcpQueueCount by viewModel.tcpQueueCount.collectAsStateWithLifecycle()
    val calibrationState by viewModel.calibration.collectAsStateWithLifecycle()
    var showExportDialog by remember { mutableStateOf(false) }
    var showCalibrationDialog by remember { mutableStateOf(false) }
    var showPairingScanner by remember { mutableStateOf(false) }
    var showBluetoothRationale by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) viewModel.hidTransport.startRegistration()
    }

    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.refreshTcpQueueCount() }

    androidx.compose.material3.Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_settings)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(padding)) {
            item { SectionHeader(stringResource(R.string.settings_section_scan)) }
            item {
                ScanModeRow(settings.defaultScanMode, viewModel::setDefaultScanMode)
                DuplicateWindowSlider(settings.duplicateWindowMillis, viewModel::setDuplicateWindowMillis)
                HorizontalDivider()
            }

            item { SectionHeader(stringResource(R.string.settings_section_feedback)) }
            item {
                ToggleRow(stringResource(R.string.settings_sound_feedback), settings.feedback.soundEnabled) { checked ->
                    viewModel.updateFeedback { it.copy(soundEnabled = checked) }
                }
                ToggleRow(stringResource(R.string.settings_vibration_feedback), settings.feedback.vibrationEnabled) { checked ->
                    viewModel.updateFeedback { it.copy(vibrationEnabled = checked) }
                }
                ToggleRow(stringResource(R.string.settings_flash_feedback), settings.feedback.flashEnabled) { checked ->
                    viewModel.updateFeedback { it.copy(flashEnabled = checked) }
                }
                HorizontalDivider()
            }

            item { SectionHeader(stringResource(R.string.settings_section_export)) }
            item {
                TextButton(onClick = { showExportDialog = true }) {
                    Text(stringResource(R.string.export_csv_columns) + " / " + stringResource(R.string.export_txt_template))
                }
                HorizontalDivider()
            }

            item { SectionHeader(stringResource(R.string.settings_section_transfer)) }
            item {
                TransferMethodRow(settings.transferMethod) { method ->
                    viewModel.setTransferMethod(method)
                    // The granular Bluetooth permissions only exist on API 31+;
                    // older versions are covered by the manifest's legacy
                    // BLUETOOTH/BLUETOOTH_ADMIN install-time permissions. The
                    // rationale is always shown first, never a bare prompt.
                    if (method == TransferMethod.BLUETOOTH_HID &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ) {
                        showBluetoothRationale = true
                    }
                }
            }
            when (settings.transferMethod) {
                TransferMethod.BLUETOOTH_HID -> item {
                    HidSettingsSection(
                        hid = settings.hid,
                        status = hidStatus,
                        onUpdate = viewModel::updateHid,
                        onOpenCalibration = { showCalibrationDialog = true },
                    )
                }
                TransferMethod.WIFI_TCP -> item {
                    TcpSettingsSection(
                        tcp = settings.tcp,
                        status = tcpStatus,
                        queueCount = tcpQueueCount,
                        onUpdate = viewModel::updateTcp,
                        onScanPairingQr = { showPairingScanner = true },
                    )
                }
                TransferMethod.HTTP_WEBHOOK -> item {
                    HttpSettingsSection(
                        http = settings.http,
                        onUpdate = viewModel::updateHttp,
                        onTest = {
                            scope.launch {
                                val message = viewModel.testHttpWebhook().fold(
                                    onSuccess = { code -> context.getString(R.string.http_webhook_test_success, code) },
                                    onFailure = { error ->
                                        context.getString(
                                            R.string.http_webhook_test_failed,
                                            error.message ?: error::class.java.simpleName,
                                        )
                                    },
                                )
                                snackbarHostState.showSnackbar(message)
                            }
                        },
                    )
                }
                TransferMethod.NONE -> Unit
            }
            item { HorizontalDivider() }

            item { SectionHeader(stringResource(R.string.settings_section_language)) }
            item { LanguageRow(settings.language, viewModel::setLanguage) }
        }
    }

    if (showExportDialog) {
        val exportSettings by exportViewModel.settings.collectAsStateWithLifecycle()
        ExportDialog(
            settings = exportSettings,
            onDismiss = { showExportDialog = false },
            onUpdateCsv = exportViewModel::updateCsvSettings,
            onUpdateTxt = exportViewModel::updateTxtSettings,
            onSetFilenameTemplate = exportViewModel::setFilenameTemplate,
            onSetShareAfterExport = exportViewModel::setShareAfterExport,
            onExport = { showExportDialog = false },
            confirmIsExport = false,
        )
    }

    if (showCalibrationDialog) {
        CalibrationDialog(
            state = calibrationState,
            onDismiss = { showCalibrationDialog = false; viewModel.resetCalibration() },
            onSend = viewModel::sendCalibrationTestString,
            onCompare = viewModel::compareCalibration,
        )
    }

    if (showBluetoothRationale) {
        AlertDialog(
            onDismissRequest = { showBluetoothRationale = false },
            title = { Text(stringResource(R.string.permission_bluetooth_title)) },
            text = { Text(stringResource(R.string.permission_bluetooth_rationale)) },
            confirmButton = {
                TextButton(onClick = {
                    showBluetoothRationale = false
                    bluetoothPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_ADVERTISE,
                        )
                    )
                }) { Text(stringResource(R.string.permission_grant)) }
            },
            dismissButton = {
                TextButton(onClick = { showBluetoothRationale = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showPairingScanner) {
        PairingQrScannerDialog(
            onDismiss = { showPairingScanner = false },
            onPaired = { parsedSettings ->
                viewModel.updateTcp { parsedSettings }
                showPairingScanner = false
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ScanModeRow(current: ScanMode, onSelect: (ScanMode) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        RadioButton(selected = current == ScanMode.SINGLE, onClick = { onSelect(ScanMode.SINGLE) })
        Text(stringResource(R.string.scan_mode_single), modifier = Modifier.padding(end = 16.dp))
        RadioButton(selected = current == ScanMode.CONTINUOUS, onClick = { onSelect(ScanMode.CONTINUOUS) })
        Text(stringResource(R.string.scan_mode_continuous))
    }
}

@Composable
private fun DuplicateWindowSlider(currentMillis: Long, onChange: (Long) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            stringResource(R.string.settings_duplicate_window) + ": " +
                stringResource(R.string.settings_duplicate_window_seconds, currentMillis / 1000f)
        )
        Slider(
            value = currentMillis.toFloat(),
            onValueChange = { onChange(it.toLong()) },
            valueRange = 0f..10000f,
            steps = 19,
        )
    }
}

@Composable
private fun TransferMethodRow(current: TransferMethod, onSelect: (TransferMethod) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        TransferMethod.entries.forEach { method ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = current == method, onClick = { onSelect(method) })
                Text(
                    when (method) {
                        TransferMethod.NONE -> stringResource(R.string.settings_transfer_method_none)
                        TransferMethod.BLUETOOTH_HID -> stringResource(R.string.settings_transfer_method_hid)
                        TransferMethod.WIFI_TCP -> stringResource(R.string.settings_transfer_method_tcp)
                        TransferMethod.HTTP_WEBHOOK -> stringResource(R.string.settings_transfer_method_http)
                    }
                )
            }
        }
    }
}

@Composable
private fun LanguageRow(current: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        listOf(
            AppLanguage.SYSTEM to R.string.settings_language_system,
            AppLanguage.GERMAN to R.string.settings_language_german,
            AppLanguage.ENGLISH to R.string.settings_language_english,
        ).forEach { (language, labelRes) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = current == language, onClick = { onSelect(language) })
                Text(stringResource(labelRes))
            }
        }
    }
}

@Composable
private fun HidSettingsSection(
    hid: com.barcodebridge.app.data.settings.HidSettings,
    status: com.barcodebridge.app.transport.hid.HidStatus,
    onUpdate: ((com.barcodebridge.app.data.settings.HidSettings) -> com.barcodebridge.app.data.settings.HidSettings) -> Unit,
    onOpenCalibration: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            when (status.state) {
                HidConnectionState.DISABLED -> stringResource(R.string.hid_status_bluetooth_off)
                HidConnectionState.DISCONNECTED -> stringResource(R.string.hid_status_disconnected)
                HidConnectionState.CONNECTING -> stringResource(R.string.hid_status_connecting)
                HidConnectionState.CONNECTED -> stringResource(R.string.hid_status_connected, status.deviceName.orEmpty())
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.hid_keyboard_layout), style = MaterialTheme.typography.labelLarge)
        Text(stringResource(R.string.hid_keyboard_layout_hint), style = MaterialTheme.typography.bodySmall)
        HidKeyboardLayout.entries.forEach { layout ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = hid.layout == layout, onClick = { onUpdate { it.copy(layout = layout) } })
                Text(layoutLabel(layout))
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = hid.prefix,
            onValueChange = { value -> onUpdate { it.copy(prefix = value) } },
            label = { Text(stringResource(R.string.hid_prefix)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.hid_suffix), style = MaterialTheme.typography.labelLarge)
        HidSuffix.entries.forEach { suffix ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = hid.suffix == suffix, onClick = { onUpdate { it.copy(suffix = suffix) } })
                Text(suffixLabel(suffix))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.hid_typing_delay) + ": " + stringResource(R.string.hid_typing_delay_ms, hid.typingDelayMs))
        Slider(
            value = hid.typingDelayMs.toFloat(),
            onValueChange = { value -> onUpdate { it.copy(typingDelayMs = value.toInt()) } },
            valueRange = 0f..100f,
        )
        ToggleRow(stringResource(R.string.hid_auto_reconnect), hid.autoReconnect) { checked ->
            onUpdate { it.copy(autoReconnect = checked) }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.hid_unmappable_action), style = MaterialTheme.typography.labelLarge)
        UnmappableCharAction.entries.forEach { action ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = hid.unmappableAction == action, onClick = { onUpdate { it.copy(unmappableAction = action) } })
                Text(unmappableActionLabel(action))
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onOpenCalibration) { Text(stringResource(R.string.hid_calibration_start)) }
    }
}

@Composable
private fun layoutLabel(layout: HidKeyboardLayout): String = when (layout) {
    HidKeyboardLayout.DE -> stringResource(R.string.hid_layout_de)
    HidKeyboardLayout.EN_US -> stringResource(R.string.hid_layout_en_us)
    HidKeyboardLayout.EN_UK -> stringResource(R.string.hid_layout_en_uk)
    HidKeyboardLayout.CH -> stringResource(R.string.hid_layout_ch)
    HidKeyboardLayout.AT -> stringResource(R.string.hid_layout_at)
    HidKeyboardLayout.FR_AZERTY -> stringResource(R.string.hid_layout_fr_azerty)
}

@Composable
private fun suffixLabel(suffix: HidSuffix): String = when (suffix) {
    HidSuffix.NONE -> stringResource(R.string.hid_suffix_none)
    HidSuffix.ENTER -> stringResource(R.string.hid_suffix_enter)
    HidSuffix.TAB -> stringResource(R.string.hid_suffix_tab)
    HidSuffix.CRLF -> stringResource(R.string.hid_suffix_crlf)
}

@Composable
private fun unmappableActionLabel(action: UnmappableCharAction): String = when (action) {
    UnmappableCharAction.SKIP -> stringResource(R.string.hid_unmappable_skip)
    UnmappableCharAction.REPLACE_WITH_QUESTION_MARK -> stringResource(R.string.hid_unmappable_replace)
    UnmappableCharAction.ABORT_AND_WARN -> stringResource(R.string.hid_unmappable_warn)
}

@Composable
private fun TcpSettingsSection(
    tcp: com.barcodebridge.app.data.settings.TcpSettings,
    status: com.barcodebridge.app.transport.tcp.TcpStatus,
    queueCount: Int,
    onUpdate: ((com.barcodebridge.app.data.settings.TcpSettings) -> com.barcodebridge.app.data.settings.TcpSettings) -> Unit,
    onScanPairingQr: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Button(onClick = onScanPairingQr) { Text(stringResource(R.string.tcp_pairing_scan_qr)) }
        Spacer(Modifier.height(8.dp))
        Text(
            when (status.state) {
                TcpConnectionState.DISCONNECTED -> stringResource(R.string.tcp_status_disconnected)
                TcpConnectionState.CONNECTING -> stringResource(R.string.tcp_status_connecting)
                TcpConnectionState.CONNECTED -> stringResource(R.string.tcp_status_connected, status.host, status.port)
                TcpConnectionState.UNREACHABLE -> stringResource(R.string.tcp_status_unreachable)
            }
        )
        if (queueCount > 0) {
            Text(stringResource(R.string.tcp_offline_buffer_count, queueCount), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = tcp.host,
            onValueChange = { value -> onUpdate { it.copy(host = value) } },
            label = { Text(stringResource(R.string.tcp_host)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = if (tcp.port == 0) "" else tcp.port.toString(),
            onValueChange = { value -> onUpdate { it.copy(port = value.toIntOrNull() ?: 0) } },
            label = { Text(stringResource(R.string.tcp_port)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = tcp.token,
            onValueChange = { value -> onUpdate { it.copy(token = value) } },
            label = { Text(stringResource(R.string.tcp_token)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun HttpSettingsSection(
    http: com.barcodebridge.app.data.settings.HttpSettings,
    onUpdate: ((com.barcodebridge.app.data.settings.HttpSettings) -> com.barcodebridge.app.data.settings.HttpSettings) -> Unit,
    onTest: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = http.url,
            onValueChange = { value -> onUpdate { it.copy(url = value) } },
            label = { Text(stringResource(R.string.http_webhook_url)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.http_webhook_method), style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = http.payloadFormat == WebhookPayloadFormat.JSON,
                onClick = { onUpdate { it.copy(payloadFormat = WebhookPayloadFormat.JSON) } },
            )
            Text(stringResource(R.string.http_webhook_json), modifier = Modifier.padding(end = 16.dp))
            RadioButton(
                selected = http.payloadFormat == WebhookPayloadFormat.PLAIN_TEXT,
                onClick = { onUpdate { it.copy(payloadFormat = WebhookPayloadFormat.PLAIN_TEXT) } },
            )
            Text(stringResource(R.string.http_webhook_plain))
        }
        Spacer(Modifier.height(12.dp))
        HttpHeadersEditor(headers = http.headers, onUpdate = onUpdate)

        Spacer(Modifier.height(8.dp))
        Button(onClick = onTest) { Text(stringResource(R.string.http_webhook_test)) }
    }
}

@Composable
private fun HttpHeadersEditor(
    headers: Map<String, String>,
    onUpdate: ((com.barcodebridge.app.data.settings.HttpSettings) -> com.barcodebridge.app.data.settings.HttpSettings) -> Unit,
) {
    var newKey by remember { mutableStateOf("") }
    var newValue by remember { mutableStateOf("") }

    Text(stringResource(R.string.http_webhook_headers), style = MaterialTheme.typography.labelLarge)

    headers.forEach { (key, value) ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$key: $value", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            IconButton(onClick = { onUpdate { it.copy(headers = it.headers - key) } }) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.history_delete))
            }
        }
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = newKey,
            onValueChange = { newKey = it },
            label = { Text(stringResource(R.string.http_webhook_header_key_hint)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = newValue,
            onValueChange = { newValue = it },
            label = { Text(stringResource(R.string.http_webhook_header_value_hint)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
    TextButton(
        onClick = {
            onUpdate { it.copy(headers = it.headers + (newKey.trim() to newValue.trim())) }
            newKey = ""
            newValue = ""
        },
        enabled = newKey.isNotBlank(),
    ) {
        Text(stringResource(R.string.http_webhook_add_header))
    }
}
