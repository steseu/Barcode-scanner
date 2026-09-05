package com.barcodebridge.app.export

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.barcodebridge.app.R
import com.barcodebridge.app.data.settings.CsvColumn

@Composable
fun csvColumnLabel(column: CsvColumn): String = when (column) {
    CsvColumn.CONTENT -> stringResource(R.string.export_csv_column_content)
    CsvColumn.FORMAT -> stringResource(R.string.export_csv_column_format)
    CsvColumn.DATE -> stringResource(R.string.export_csv_column_date)
    CsvColumn.TIME -> stringResource(R.string.export_csv_column_time)
    CsvColumn.NOTE -> stringResource(R.string.export_csv_column_note)
    CsvColumn.SESSION -> stringResource(R.string.export_csv_column_session)
    CsvColumn.INDEX -> stringResource(R.string.export_csv_column_index)
}
