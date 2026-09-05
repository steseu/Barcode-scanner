package com.barcodebridge.app.domain.model

import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class ParsedCalendarEvent(
    val title: String,
    val location: String,
    val description: String,
    val startEpochMillis: Long?,
    val endEpochMillis: Long?,
)

/** Minimal iCalendar VEVENT reader - just the fields useful for prefilling Android's "add event" intent. */
object CalendarEventParser {
    private val UTC_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    private val LOCAL_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    fun parse(content: String): ParsedCalendarEvent {
        val lines = content.lines().map { it.trim() }
        fun field(name: String): String? = lines.firstOrNull { it.startsWith("$name:", ignoreCase = true) }
            ?.substringAfter(":")

        return ParsedCalendarEvent(
            title = field("SUMMARY").orEmpty(),
            location = field("LOCATION").orEmpty(),
            description = field("DESCRIPTION").orEmpty(),
            startEpochMillis = field("DTSTART")?.let(::parseEpochMillis),
            endEpochMillis = field("DTEND")?.let(::parseEpochMillis),
        )
    }

    private fun parseEpochMillis(raw: String): Long? = runCatching {
        if (raw.endsWith("Z")) {
            LocalDateTime.parse(raw, UTC_FORMAT).toInstant(ZoneOffset.UTC).toEpochMilli()
        } else {
            LocalDateTime.parse(raw, LOCAL_FORMAT).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
    }.getOrNull()
}
