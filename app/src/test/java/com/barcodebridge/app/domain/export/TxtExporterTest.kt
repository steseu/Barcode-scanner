package com.barcodebridge.app.domain.export

import com.barcodebridge.app.data.settings.LineEnding
import com.barcodebridge.app.data.settings.TxtExportSettings
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TxtExporterTest {

    private fun row(
        content: String = "12345",
        format: String = "EAN-13",
        timestamp: String = "01.01.2026 12:00:00",
        note: String = "",
        session: String = "Default",
        index: Int = 1,
    ) = ExportRow(content, format, "01.01.2026", "12:00:00", timestamp, note, session, index)

    @Test
    fun `default template substitutes content format and timestamp`() {
        val settings = TxtExportSettings(lineTemplate = "{content}\t{format}\t{timestamp}", lineEnding = LineEnding.LF)
        val txt = TxtExporter.export(listOf(row(content = "123", format = "QR", timestamp = "ts")), settings)
        assertThat(txt).isEqualTo("123\tQR\tts\n")
    }

    @Test
    fun `all placeholders are substituted`() {
        val settings = TxtExportSettings(
            lineTemplate = "{index}: {content} [{format}] {date} {time} note={note} session={session}",
            lineEnding = LineEnding.LF,
        )
        val txt = TxtExporter.export(
            listOf(row(content = "C", format = "F", note = "N", session = "S", index = 7)),
            settings,
        )
        assertThat(txt).isEqualTo("7: C [F] 01.01.2026 12:00:00 note=N session=S\n")
    }

    @Test
    fun `LF line ending is used when configured`() {
        val settings = TxtExportSettings(lineTemplate = "{content}", lineEnding = LineEnding.LF)
        val txt = TxtExporter.export(listOf(row(content = "a"), row(content = "b")), settings)
        assertThat(txt).isEqualTo("a\nb\n")
        assertThat(txt).doesNotContain("\r\n")
    }

    @Test
    fun `CRLF line ending is used when configured`() {
        val settings = TxtExportSettings(lineTemplate = "{content}", lineEnding = LineEnding.CRLF)
        val txt = TxtExporter.export(listOf(row(content = "a"), row(content = "b")), settings)
        assertThat(txt).isEqualTo("a\r\nb\r\n")
    }

    @Test
    fun `empty row list produces empty output`() {
        val settings = TxtExportSettings()
        val txt = TxtExporter.export(emptyList(), settings)
        assertThat(txt).isEmpty()
    }

    @Test
    fun `bytes are UTF-8 encoded`() {
        val settings = TxtExportSettings(lineTemplate = "{content}", lineEnding = LineEnding.LF)
        val bytes = TxtExporter.exportBytes(listOf(row(content = "ä€")), settings)
        assertThat(bytes).isEqualTo("ä€\n".toByteArray(Charsets.UTF_8))
    }
}
