package com.barcodebridge.app.export

import com.barcodebridge.app.domain.export.ExportRow
import com.barcodebridge.app.domain.model.ScanRecord
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Turns domain [ScanRecord]s into locale-formatted [ExportRow]s for CSV/TXT export. */
@Singleton
class ExportRowMapper @Inject constructor() {

    fun map(records: List<ScanRecord>, sessionNames: Map<Long, String>, unassignedLabel: String): List<ExportRow> {
        val locale = Locale.getDefault()
        val zone = ZoneId.systemDefault()
        val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale)
        val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withLocale(locale)
        val timestampFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.MEDIUM).withLocale(locale)

        return records.mapIndexed { index, record ->
            val zdt = Instant.ofEpochMilli(record.timestamp.toEpochMilliseconds()).atZone(zone)
            ExportRow(
                content = record.content,
                format = record.format.displayName,
                date = dateFormatter.format(zdt),
                time = timeFormatter.format(zdt),
                timestamp = timestampFormatter.format(zdt),
                note = record.note,
                session = record.sessionId?.let { sessionNames[it] } ?: unassignedLabel,
                index = index + 1,
            )
        }
    }
}
