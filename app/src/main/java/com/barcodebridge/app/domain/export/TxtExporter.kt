package com.barcodebridge.app.domain.export

import com.barcodebridge.app.data.settings.TxtExportSettings

/**
 * Renders one line per scan from a user-defined template such as
 * `{content}\t{format}\t{timestamp}`. Placeholders are replaced literally
 * (no escaping) since TXT export is meant to be a raw, human-authored line
 * format rather than a structured one - that's what CSV is for.
 */
object TxtExporter {

    fun export(rows: List<ExportRow>, settings: TxtExportSettings): String {
        val ending = settings.lineEnding.value
        return rows.joinToString(separator = "") { row -> renderLine(row, settings.lineTemplate) + ending }
    }

    fun exportBytes(rows: List<ExportRow>, settings: TxtExportSettings): ByteArray =
        export(rows, settings).toByteArray(Charsets.UTF_8)

    private fun renderLine(row: ExportRow, template: String): String = template
        .replace("{content}", row.content)
        .replace("{format}", row.format)
        .replace("{date}", row.date)
        .replace("{time}", row.time)
        .replace("{timestamp}", row.timestamp)
        .replace("{note}", row.note)
        .replace("{session}", row.session)
        .replace("{index}", row.index.toString())
}
