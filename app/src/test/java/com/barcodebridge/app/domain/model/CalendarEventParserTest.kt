package com.barcodebridge.app.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CalendarEventParserTest {

    @Test
    fun `parses title, location and description`() {
        val vevent = """
            BEGIN:VEVENT
            SUMMARY:Team Meeting
            LOCATION:Room 42
            DESCRIPTION:Quarterly planning
            DTSTART:20260115T090000Z
            DTEND:20260115T100000Z
            END:VEVENT
        """.trimIndent()
        val event = CalendarEventParser.parse(vevent)
        assertThat(event.title).isEqualTo("Team Meeting")
        assertThat(event.location).isEqualTo("Room 42")
        assertThat(event.description).isEqualTo("Quarterly planning")
        assertThat(event.startEpochMillis).isNotNull()
        assertThat(event.endEpochMillis).isGreaterThan(event.startEpochMillis)
    }

    @Test
    fun `missing fields default to empty or null`() {
        val event = CalendarEventParser.parse("BEGIN:VEVENT\nEND:VEVENT")
        assertThat(event.title).isEmpty()
        assertThat(event.startEpochMillis).isNull()
        assertThat(event.endEpochMillis).isNull()
    }
}
