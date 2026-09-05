package com.barcodebridge.app.export

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDateTime

class FilenameTemplateTest {

    @Test
    fun `timestamp placeholder is replaced with sortable pattern`() {
        val fixed = LocalDateTime.of(2026, 3, 4, 13, 5, 9)
        val name = FilenameTemplate.resolve("barcodebridge_{timestamp}", fixed)
        assertThat(name).isEqualTo("barcodebridge_20260304_130509")
    }

    @Test
    fun `extension is appended once`() {
        assertThat(FilenameTemplate.withExtension("export", "csv")).isEqualTo("export.csv")
        assertThat(FilenameTemplate.withExtension("export.csv", "csv")).isEqualTo("export.csv")
    }
}
