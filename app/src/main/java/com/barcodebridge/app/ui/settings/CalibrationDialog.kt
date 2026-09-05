package com.barcodebridge.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.barcodebridge.app.R
import com.barcodebridge.app.transport.hid.HidCalibration

@Composable
fun CalibrationDialog(
    state: CalibrationUiState,
    onDismiss: () -> Unit,
    onSend: () -> Unit,
    onCompare: (String) -> Unit,
) {
    var actualText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.hid_calibration_title)) },
        text = {
            Column {
                Text(stringResource(R.string.hid_calibration_intro), style = MaterialTheme.typography.bodyMedium)
                androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.hid_calibration_expected) + ": " + HidCalibration.TEST_STRING)
                androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))

                if (!state.sent) {
                    Button(onClick = onSend) { Text(stringResource(R.string.hid_calibration_send)) }
                } else {
                    OutlinedTextField(
                        value = actualText,
                        onValueChange = { actualText = it },
                        label = { Text(stringResource(R.string.hid_calibration_actual_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                    Button(onClick = { onCompare(actualText) }, enabled = actualText.isNotBlank()) {
                        Text(stringResource(R.string.hid_calibration_compare))
                    }
                }

                state.error?.let {
                    androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }

                if (state.results.isNotEmpty()) {
                    androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
                    val best = state.results.first()
                    if (best.matchRatio >= 0.999) {
                        Text(stringResource(R.string.hid_calibration_match, best.layout.name))
                    } else if (best.matchRatio > 0.0) {
                        Text(stringResource(R.string.hid_calibration_mismatch, best.layout.name))
                    } else {
                        Text(stringResource(R.string.hid_calibration_no_match))
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                    state.results.take(3).forEach { score ->
                        Text("${score.layout.name}: ${(score.matchRatio * 100).toInt()}% - \"${score.predicted}\"")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}
