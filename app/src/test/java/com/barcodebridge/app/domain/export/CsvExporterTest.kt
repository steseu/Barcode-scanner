package com.barcodebridge.app.domain.export

import com.barcodebridge.app.data.settings.CsvColumn
import com.barcodebridge.app.data.settings.CsvDelimiter
import com.barcodebridge.app.data.settings.CsvExportSettings
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CsvExporterTest {

    private fun row(
        content: String = "12345",
        format: String = "EAN-13",
        date: String = "01.01.2026",
        time: String = "12:00:00",
        note: String = "",
        session: String = "Default",
        index: Int = 1,
    ) = ExportRow(content, format, date, time, "$date $time", note, session, index)

    @Test
    fun `header row uses selected columns in order`() {
        val settings = CsvExportSettings(
            columns = listOf(CsvColumn.CONTENT, CsvColumn.FORMAT),
            delimiter = CsvDelimiter.SEMICOLON,
            includeHeader = true,
        )
        val csv = CsvExporter.export(listOf(row()), settings, columnLabel = { it.name })
        val firstLine = csv.substringBefore("\r\n")
        assertThat(firstLine).isEqualTo("CONTENT;FORMAT")
    }

    @Test
    fun `no header row when disabled`() {
        val settings = CsvExportSettings(columns = listOf(CsvColumn.CONTENT), includeHeader = false)
        val csv = CsvExporter.export(listOf(row(content = "ABC")), settings)
        assertThat(csv).isEqualTo("ABC\r\n")
    }

    @Test
    fun `rows use configured delimiter`() {
        val settings = CsvExportSettings(
            columns = listOf(CsvColumn.CONTENT, CsvColumn.FORMAT, CsvColumn.DATE),
            delimiter = CsvDelimiter.TAB,
            includeHeader = false,
        )
        val csv = CsvExporter.export(listOf(row(content = "A", format = "QR", date = "01.01.2026")), settings)
        assertThat(csv).isEqualTo("A\tQR\t01.01.2026\r\n")
    }

    @Test
    fun `field containing delimiter is quoted`() {
        val settings = CsvExportSettings(columns = listOf(CsvColumn.CONTENT), delimiter = CsvDelimiter.SEMICOLON, includeHeader = false)
        val csv = CsvExporter.export(listOf(row(content = "a;b")), settings)
        assertThat(csv).isEqualTo("\"a;b\"\r\n")
    }

    @Test
    fun `field containing quote is escaped by doubling`() {
        val settings = CsvExportSettings(columns = listOf(CsvColumn.CONTENT), includeHeader = false)
        val csv = CsvExporter.export(listOf(row(content = "say \"hi\"")), settings)
        assertThat(csv).isEqualTo("\"say \"\"hi\"\"\"\r\n")
    }

    @Test
    fun `field containing newline is quoted`() {
        val settings = CsvExportSettings(columns = listOf(CsvColumn.CONTENT), includeHeader = false)
        val csv = CsvExporter.export(listOf(row(content = "line1\nline2")), settings)
        assertThat(csv).isEqualTo("\"line1\nline2\"\r\n")
    }

    @Test
    fun `field containing carriage return is quoted`() {
        val settings = CsvExportSettings(columns = listOf(CsvColumn.CONTENT), includeHeader = false)
        val csv = CsvExporter.export(listOf(row(content = "a\rb")), settings)
        assertThat(csv).isEqualTo("\"a\rb\"\r\n")
    }

    @Test
    fun `plain field is not quoted`() {
        val settings = CsvExportSettings(columns = listOf(CsvColumn.CONTENT), includeHeader = false)
        val csv = CsvExporter.export(listOf(row(content = "plain")), settings)
        assertThat(csv).isEqualTo("plain\r\n")
    }

    @Test
    fun `index column reflects row position starting at 1`() {
        val settings = CsvExportSettings(columns = listOf(CsvColumn.INDEX), includeHeader = false)
        val rows = listOf(row(index = 1), row(index = 2), row(index = 3))
        val csv = CsvExporter.export(rows, settings)
        assertThat(csv).isEqualTo("1\r\n2\r\n3\r\n")
    }

    @Test
    fun `utf8 bom is prepended only when enabled`() {
        val settingsWithBom = CsvExportSettings(columns = listOf(CsvColumn.CONTENT), includeHeader = false, utf8Bom = true)
        val bytes = CsvExporter.exportBytes(listOf(row(content = "x")), settingsWithBom)
        assertThat(bytes.take(3)).isEqualTo(listOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))

        val settingsNoBom = settingsWithBom.copy(utf8Bom = false)
        val bytesNoBom = CsvExporter.exportBytes(listOf(row(content = "x")), settingsNoBom)
        assertThat(bytesNoBom.take(3)).isNotEqualTo(listOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
    }

    @Test
    fun `session and note columns are included when selected`() {
        val settings = CsvExportSettings(columns = listOf(CsvColumn.SESSION, CsvColumn.NOTE), includeHeader = false)
        val csv = CsvExporter.export(listOf(row(session = "Inventur", note = "geprüft")), settings)
        assertThat(csv).isEqualTo("Inventur;geprüft\r\n")
    }
}
