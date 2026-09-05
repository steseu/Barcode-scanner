package com.barcodebridge.app.export

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
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
import com.barcodebridge.app.data.settings.AppSettings
import com.barcodebridge.app.data.settings.CsvColumn
import com.barcodebridge.app.data.settings.CsvDelimiter
import com.barcodebridge.app.data.settings.LineEnding

@Composable
fun ExportDialog(
    settings: AppSettings,
    onDismiss: () -> Unit,
    onUpdateCsv: ((com.barcodebridge.app.data.settings.CsvExportSettings) -> com.barcodebridge.app.data.settings.CsvExportSettings) -> Unit,
    onUpdateTxt: ((com.barcodebridge.app.data.settings.TxtExportSettings) -> com.barcodebridge.app.data.settings.TxtExportSettings) -> Unit,
    onSetFilenameTemplate: (String) -> Unit,
    onSetShareAfterExport: (Boolean) -> Unit,
    onExport: (ExportFormatType) -> Unit,
) {
    var format by remember { mutableStateOf(ExportFormatType.CSV) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history_export)) },
        text = {
            LazyColumn(modifier = Modifier.height(420.dp)) {
                item {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(selected = format == ExportFormatType.CSV, onClick = { format = ExportFormatType.CSV }, shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(0, 2)) {
                            Text(stringResource(R.string.export_format_csv))
                        }
                        SegmentedButton(selected = format == ExportFormatType.TXT, onClick = { format = ExportFormatType.TXT }, shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(1, 2)) {
                            Text(stringResource(R.string.export_format_txt))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                if (format == ExportFormatType.CSV) {
                    item { Text(stringResource(R.string.export_csv_columns), style = androidx.compose.material3.MaterialTheme.typography.labelLarge) }
                    items(CsvColumn.entries.toList()) { column ->
                        Row {
                            Checkbox(
                                checked = settings.csv.columns.contains(column),
                                onCheckedChange = { checked ->
                                    onUpdateCsv { csv ->
                                        csv.copy(
                                            columns = if (checked) csv.columns + column else csv.columns - column
                                        )
                                    }
                                },
                            )
                            Text(csvColumnLabel(column), modifier = Modifier.fillMaxWidth())
                        }
                    }
                    item {
                        HorizontalDivider(Modifier.height(1.dp))
                        Text(stringResource(R.string.export_csv_delimiter), style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                        CsvDelimiter.entries.forEach { delimiter ->
                            Row {
                                RadioButton(
                                    selected = settings.csv.delimiter == delimiter,
                                    onClick = { onUpdateCsv { it.copy(delimiter = delimiter) } },
                                )
                                Text(
                                    when (delimiter) {
                                        CsvDelimiter.COMMA -> stringResource(R.string.export_csv_delimiter_comma)
                                        CsvDelimiter.SEMICOLON -> stringResource(R.string.export_csv_delimiter_semicolon)
                                        CsvDelimiter.TAB -> stringResource(R.string.export_csv_delimiter_tab)
                                    }
                                )
                            }
                        }
                        Row {
                            Text(stringResource(R.string.export_csv_header), modifier = Modifier.fillMaxWidth())
                            Switch(checked = settings.csv.includeHeader, onCheckedChange = { onUpdateCsv { csv -> csv.copy(includeHeader = it) } })
                        }
                        Row {
                            Text(stringResource(R.string.export_csv_bom), modifier = Modifier.fillMaxWidth())
                            Switch(checked = settings.csv.utf8Bom, onCheckedChange = { onUpdateCsv { csv -> csv.copy(utf8Bom = it) } })
                        }
                    }
                } else {
                    item {
                        OutlinedTextField(
                            value = settings.txt.lineTemplate,
                            onValueChange = { onUpdateTxt { txt -> txt.copy(lineTemplate = it) } },
                            label = { Text(stringResource(R.string.export_txt_template)) },
                            placeholder = { Text(stringResource(R.string.export_txt_template_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.export_txt_line_ending), style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                        LineEnding.entries.forEach { ending ->
                            Row {
                                RadioButton(
                                    selected = settings.txt.lineEnding == ending,
                                    onClick = { onUpdateTxt { it.copy(lineEnding = ending) } },
                                )
                                Text(
                                    if (ending == LineEnding.LF) stringResource(R.string.export_txt_line_ending_lf)
                                    else stringResource(R.string.export_txt_line_ending_crlf)
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = settings.exportFilenameTemplate,
                        onValueChange = onSetFilenameTemplate,
                        label = { Text(stringResource(R.string.export_filename_template)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row {
                        Text(stringResource(R.string.export_share_after_export), modifier = Modifier.fillMaxWidth())
                        Switch(checked = settings.shareAfterExport, onCheckedChange = onSetShareAfterExport)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onExport(format) }) { Text(stringResource(R.string.export_start)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
