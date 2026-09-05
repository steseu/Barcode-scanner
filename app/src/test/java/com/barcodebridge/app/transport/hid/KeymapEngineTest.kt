package com.barcodebridge.app.transport.hid

import com.barcodebridge.app.data.settings.HidKeyboardLayout
import com.barcodebridge.app.data.settings.HidSettings
import com.barcodebridge.app.data.settings.HidSuffix
import com.barcodebridge.app.data.settings.UnmappableCharAction
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KeymapEngineTest {

    private val engine = KeymapEngine()
    private val de = KeymapTestUtil.loadMap(HidKeyboardLayout.DE)

    private fun baseSettings(action: UnmappableCharAction = UnmappableCharAction.SKIP) =
        HidSettings(layout = HidKeyboardLayout.DE, prefix = "", suffix = HidSuffix.NONE, unmappableAction = action)

    @Test
    fun `dead key is followed by a Space to commit the literal accent character`() {
        val result = engine.encode("^", de, baseSettings()) as HidEncodeResult.Success
        val circumflex = de.entries.getValue('^')
        val expected = listOf(
            HidReport(circumflex.modifiers.toBitmask(), circumflex.usageCode),
            HidReport.RELEASE_ALL,
            HidReport(0, KEY_SPACE),
            HidReport.RELEASE_ALL,
        )
        assertThat(result.reports).isEqualTo(expected)
    }

    @Test
    fun `two identical consecutive characters get an extra empty report between them`() {
        val result = engine.encode("aa", de, baseSettings()) as HidEncodeResult.Success
        val a = de.entries.getValue('a')
        val aReport = HidReport(a.modifiers.toBitmask(), a.usageCode)
        val expected = listOf(
            aReport, HidReport.RELEASE_ALL,
            HidReport.RELEASE_ALL, // forced gap so the host sees two distinct keystrokes
            aReport, HidReport.RELEASE_ALL,
        )
        assertThat(result.reports).isEqualTo(expected)
    }

    @Test
    fun `two different characters do not get an extra empty report`() {
        val result = engine.encode("ab", de, baseSettings()) as HidEncodeResult.Success
        val a = de.entries.getValue('a')
        val b = de.entries.getValue('b')
        val expected = listOf(
            HidReport(a.modifiers.toBitmask(), a.usageCode), HidReport.RELEASE_ALL,
            HidReport(b.modifiers.toBitmask(), b.usageCode), HidReport.RELEASE_ALL,
        )
        assertThat(result.reports).isEqualTo(expected)
    }

    @Test
    fun `prefix is sent before the content and suffix after`() {
        val settings = baseSettings().copy(prefix = "A", suffix = HidSuffix.ENTER)
        val result = engine.encode("b", de, settings) as HidEncodeResult.Success
        val a = de.entries.getValue('A')
        val b = de.entries.getValue('b')
        val expected = listOf(
            HidReport(a.modifiers.toBitmask(), a.usageCode), HidReport.RELEASE_ALL,
            HidReport(b.modifiers.toBitmask(), b.usageCode), HidReport.RELEASE_ALL,
            HidReport(0, KEY_ENTER), HidReport.RELEASE_ALL,
        )
        assertThat(result.reports).isEqualTo(expected)
    }

    @Test
    fun `tab suffix uses the Tab usage code`() {
        val settings = baseSettings().copy(suffix = HidSuffix.TAB)
        val result = engine.encode("a", de, settings) as HidEncodeResult.Success
        assertThat(result.reports.last()).isEqualTo(HidReport.RELEASE_ALL)
        assertThat(result.reports[result.reports.size - 2]).isEqualTo(HidReport(0, KEY_TAB))
    }

    @Test
    fun `none suffix appends nothing`() {
        val settings = baseSettings().copy(suffix = HidSuffix.NONE)
        val result = engine.encode("a", de, settings) as HidEncodeResult.Success
        assertThat(result.reports).hasSize(2) // just the one key press+release
    }

    @Test
    fun `unmappable action SKIP drops the character silently`() {
        val settings = baseSettings(UnmappableCharAction.SKIP)
        val result = engine.encode("§a", de, settings) as HidEncodeResult.Success // § is not in the DE table
        val a = de.entries.getValue('a')
        assertThat(result.reports).isEqualTo(listOf(HidReport(a.modifiers.toBitmask(), a.usageCode), HidReport.RELEASE_ALL))
    }

    @Test
    fun `unmappable action REPLACE substitutes a question mark`() {
        val settings = baseSettings(UnmappableCharAction.REPLACE_WITH_QUESTION_MARK)
        val result = engine.encode("§", de, settings) as HidEncodeResult.Success
        val q = de.entries.getValue('?')
        assertThat(result.reports).isEqualTo(listOf(HidReport(q.modifiers.toBitmask(), q.usageCode), HidReport.RELEASE_ALL))
    }

    @Test
    fun `unmappable action ABORT_AND_WARN reports every offending character up front without sending anything`() {
        val settings = baseSettings(UnmappableCharAction.ABORT_AND_WARN)
        val result = engine.encode("a§b§", de, settings)
        assertThat(result).isInstanceOf(HidEncodeResult.Unmappable::class.java)
        assertThat((result as HidEncodeResult.Unmappable).characters).containsExactly('§')
    }
}
