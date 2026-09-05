package com.barcodebridge.app.ui.scan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.barcodebridge.app.R
import com.barcodebridge.app.domain.model.ScanSession

/**
 * Chooses which session new scans are filed under, or creates a new one -
 * this is what makes batch scanning into a named list work without going
 * through the history screen afterwards.
 */
@Composable
fun SessionPickerDialog(
    sessions: List<ScanSession>,
    activeSessionId: Long?,
    onDismiss: () -> Unit,
    onSelect: (Long?, String?) -> Unit,
    onCreate: (String) -> Unit,
) {
    var newSessionName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scan_session)) },
        text = {
            Column {
                LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                    item {
                        SessionRow(
                            label = stringResource(R.string.history_session_default),
                            selected = activeSessionId == null,
                            onClick = { onSelect(null, null) },
                        )
                    }
                    items(sessions, key = { it.id }) { session ->
                        SessionRow(
                            label = session.name,
                            selected = activeSessionId == session.id,
                            onClick = { onSelect(session.id, session.name) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newSessionName,
                    onValueChange = { newSessionName = it },
                    label = { Text(stringResource(R.string.history_session_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(newSessionName) },
                enabled = newSessionName.isNotBlank(),
            ) {
                Text(stringResource(R.string.history_session_new))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

@Composable
private fun SessionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}
