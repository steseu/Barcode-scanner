package com.barcodebridge.app.ui.scan

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.barcodebridge.app.R
import com.barcodebridge.app.data.settings.ScanMode
import com.barcodebridge.app.ui.common.PermissionGate
import kotlinx.coroutines.launch

@Composable
fun ScanScreen(viewModel: ScanViewModel = hiltViewModel()) {
    PermissionGate(
        permission = Manifest.permission.CAMERA,
        rationaleTitle = stringResource(R.string.permission_camera_title),
        rationaleText = stringResource(R.string.permission_camera_rationale),
    ) {
        ScanScreenContent(viewModel)
    }
}

@Composable
private fun ScanScreenContent(viewModel: ScanViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }

    val duplicateSkippedText = stringResource(R.string.scan_duplicate_skipped)
    val noBarcodeText = stringResource(R.string.scan_no_barcode_found)
    var unmappableDialogEvent by remember { mutableStateOf<ScanEvent.HidUnmappableChars?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ScanEvent.HidUnmappableChars -> unmappableDialogEvent = event
                else -> {
                    val message = when (event) {
                        is ScanEvent.DuplicateSkipped -> duplicateSkippedText
                        is ScanEvent.NoBarcodeInImage -> noBarcodeText
                        is ScanEvent.TransportFailed -> event.message
                        is ScanEvent.Error -> event.message
                        else -> null
                    }
                    if (message != null) scope.launch { snackbarHostState.showSnackbar(message) }
                }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.scanImageFromGallery(context, it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                zoomRatio = uiState.zoomRatio,
                minZoomRatio = uiState.minZoomRatio,
                maxZoomRatio = uiState.maxZoomRatio,
                onZoomRatioChange = { viewModel.setZoomRatio(camera?.cameraControl, it) },
                onCameraReady = { camera = it },
                onBarcodesDetected = { viewModel.onBarcodesDetected(it) },
            )

            ScanTargetOverlay(highlighted = uiState.isPaused, modifier = Modifier.fillMaxSize())
            ScanFlashOverlay(flashKey = uiState.lastScan?.id, modifier = Modifier.fillMaxSize())

            if (!uiState.isPaused) {
                Text(
                    text = stringResource(R.string.scan_hint_point_camera),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 24.dp)
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            if (uiState.scanMode == ScanMode.CONTINUOUS && uiState.batchCount > 0) {
                Text(
                    text = stringResource(R.string.scan_batch_count, uiState.batchCount),
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(24.dp)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            // Bottom third: all interactive controls, reachable one-handed.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(bottom = 24.dp, top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SegmentedButton(
                        selected = uiState.scanMode == ScanMode.SINGLE,
                        onClick = { viewModel.switchScanMode(ScanMode.SINGLE) },
                        shape = MaterialTheme.shapes.small,
                    ) { Text(stringResource(R.string.scan_mode_single)) }
                    SegmentedButton(
                        selected = uiState.scanMode == ScanMode.CONTINUOUS,
                        onClick = { viewModel.switchScanMode(ScanMode.CONTINUOUS) },
                        shape = MaterialTheme.shapes.small,
                    ) { Text(stringResource(R.string.scan_mode_continuous)) }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalIconButton(
                        onClick = { viewModel.toggleTorch(camera?.cameraControl) },
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(
                            imageVector = if (uiState.torchOn) Icons.Filled.FlashOff else Icons.Filled.FlashOn,
                            contentDescription = stringResource(R.string.cd_torch_toggle),
                        )
                    }
                    FilledTonalIconButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = stringResource(R.string.scan_from_gallery))
                    }
                    FilledTonalIconButton(
                        onClick = { viewModel.showManualEntry(true) },
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.scan_manual_entry))
                    }
                    if (uiState.isPaused) {
                        FilledTonalIconButton(
                            onClick = { viewModel.resumeScanning() },
                            modifier = Modifier.size(56.dp),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.cd_switch_mode))
                        }
                    }
                }
            }
        }
    }

    if (uiState.manualEntryVisible) {
        ManualEntryDialog(
            onDismiss = { viewModel.showManualEntry(false) },
            onSubmit = { content, format -> viewModel.submitManualEntry(content, format) },
        )
    }

    unmappableDialogEvent?.let { event ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { unmappableDialogEvent = null },
            title = { Text(stringResource(R.string.hid_unmappable_dialog_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.hid_unmappable_dialog_message,
                        event.layoutLabel,
                        event.characters.joinToString(" "),
                    )
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    viewModel.switchToWifiTransfer()
                    unmappableDialogEvent = null
                }) { Text(stringResource(R.string.hid_suggest_method_b)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { unmappableDialogEvent = null }) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }
}
