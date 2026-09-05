package com.barcodebridge.app.ui.settings

import android.Manifest
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.barcodebridge.app.R
import com.barcodebridge.app.data.settings.TcpSettings
import com.barcodebridge.app.transport.tcp.PairingQrParser
import com.barcodebridge.app.ui.common.PermissionGate
import com.barcodebridge.app.ui.scan.CameraPreview

/** Full-screen camera used only to scan the Windows companion app's pairing QR code - never touches scan history. */
@Composable
fun PairingQrScannerDialog(onDismiss: () -> Unit, onPaired: (TcpSettings) -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize()) {
            PermissionGate(
                permission = Manifest.permission.CAMERA,
                rationaleTitle = stringResource(R.string.permission_camera_title),
                rationaleText = stringResource(R.string.permission_camera_rationale),
            ) {
                PairingScanner(onPaired = onPaired)
            }

            Text(
                stringResource(R.string.tcp_pairing_scan_qr),
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(24.dp)
                    .fillMaxWidth(),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close), tint = Color.White)
            }
        }
    }
}

@Composable
private fun PairingScanner(onPaired: (TcpSettings) -> Unit) {
    // Guards against the analyzer firing again between the first hit and the
    // dialog actually closing.
    var handled by remember { mutableStateOf(false) }

    CameraPreview(
        modifier = Modifier.fillMaxSize(),
        zoomRatio = 1f,
        minZoomRatio = 1f,
        maxZoomRatio = 1f,
        onZoomRatioChange = {},
        onCameraReady = {},
        onBarcodesDetected = { detected ->
            if (!handled) {
                val parsed = detected.firstNotNullOfOrNull { PairingQrParser.parse(it.content) }
                if (parsed != null) {
                    handled = true
                    onPaired(parsed)
                }
            }
        },
    )
}
