package com.barcodebridge.app.domain.export

import com.barcodebridge.app.data.settings.CsvColumn
import com.barcodebridge.app.data.settings.CsvExportSettings

/**
 * Builds an RFC 4180-ish CSV: CRLF row endings (Excel expects them
 * regardless of the TXT export's configurable line ending), and a field is
 * quoted only when it contains the delimiter, a quote, or a line break -
 * with internal quotes doubled.
 */
object CsvExporter {

    private const val ROW_ENDING = "\r\n"
    private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    fun export(
        rows: List<ExportRow>,
        settings: CsvExportSettings,
        columnLabel: (CsvColumn) -> String = { it.name },
    ): String {
        val delimiter = settings.delimiter.char
        val builder = StringBuilder()

        if (settings.includeHeader) {
            builder.append(settings.columns.joinToString(delimiter.toString()) { escape(columnLabel(it), delimiter) })
            builder.append(ROW_ENDING)
        }

        for (row in rows) {
            builder.append(
                settings.columns.joinToString(delimiter.toString()) { column ->
                    escape(valueFor(column, row), delimiter)
                }
            )
            builder.append(ROW_ENDING)
        }

        return builder.toString()
    }

    fun exportBytes(
        rows: List<ExportRow>,
        settings: CsvExportSettings,
        columnLabel: (CsvColumn) -> String = { it.name },
    ): ByteArray {
        val csv = export(rows, settings, columnLabel).toByteArray(Charsets.UTF_8)
        return if (settings.utf8Bom) UTF8_BOM + csv else csv
    }

    private fun valueFor(column: CsvColumn, row: ExportRow): String = when (column) {
        CsvColumn.CONTENT -> row.content
        CsvColumn.FORMAT -> row.format
        CsvColumn.DATE -> row.date
        CsvColumn.TIME -> row.time
        CsvColumn.NOTE -> row.note
        CsvColumn.SESSION -> row.session
        CsvColumn.INDEX -> row.index.toString()
    }

    private fun escape(field: String, delimiter: Char): String {
        val needsQuoting = field.contains(delimiter) || field.contains('"') ||
            field.contains('\n') || field.contains('\r')
        return if (needsQuoting) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
    }
}
