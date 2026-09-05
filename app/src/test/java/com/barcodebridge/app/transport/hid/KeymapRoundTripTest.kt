package com.barcodebridge.app.transport.hid

import com.barcodebridge.app.data.settings.HidKeyboardLayout
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * Every layout must cover the full printable ASCII range plus the
 * German-specific extras (umlauts/ß/€ and the AltGr punctuation set) - a
 * silently missing character is exactly the "stille Datenverfälschung" the
 * spec calls out, so any gap here must fail loudly.
 */
class KeymapRoundTripTest {

    private val fullPrintableAscii = (0x20..0x7E).map { it.toChar() }
    private val requiredExtras = "äöüÄÖÜß€@\\|~^[]{}<>".toList()

    /** Characters a real physical keyboard for this layout does not have (e.g. no Euro key on US/UK). */
    private val knownGapsPerLayout: Map<HidKeyboardLayout, Set<Char>> = mapOf(
        HidKeyboardLayout.EN_US to setOf('ä', 'ö', 'ü', 'Ä', 'Ö', 'Ü', 'ß', '€'),
        HidKeyboardLayout.EN_UK to setOf('ä', 'ö', 'ü', 'Ä', 'Ö', 'Ü', 'ß', '€'),
    )

    @Test
    fun `every layout maps full printable ASCII except documented gaps`() {
        for (layout in HidKeyboardLayout.entries) {
            val map = KeymapTestUtil.loadMap(layout)
            val gaps = knownGapsPerLayout[layout].orEmpty()
            val missing = fullPrintableAscii.filter { it !in gaps && it !in map.entries }
            assertWithMessage("missing ASCII chars for $layout").that(missing).isEmpty()
        }
    }

    @Test
    fun `DE, AT and CH map the required German extras`() {
        for (layout in listOf(HidKeyboardLayout.DE, HidKeyboardLayout.AT, HidKeyboardLayout.CH)) {
            val map = KeymapTestUtil.loadMap(layout)
            val missing = requiredExtras.filter { it !in map.entries }
            assertWithMessage("missing extras for $layout").that(missing).isEmpty()
        }
    }

    @Test
    fun `no layout uses numpad usage codes`() {
        for (layout in HidKeyboardLayout.entries) {
            val map = KeymapTestUtil.loadMap(layout)
            val numpadCodes = map.entries.values.filter { it.usageCode in 0x54..0x63 }
            assertWithMessage("numpad codes used by $layout").that(numpadCodes).isEmpty()
        }
    }

    @Test
    fun `every mapped character round trips through KeymapEngine without becoming unmappable`() {
        val engine = KeymapEngine()
        for (layout in HidKeyboardLayout.entries) {
            val map = KeymapTestUtil.loadMap(layout)
            val settings = com.barcodebridge.app.data.settings.HidSettings(
                layout = layout,
                unmappableAction = com.barcodebridge.app.data.settings.UnmappableCharAction.ABORT_AND_WARN,
            )
            val text = map.entries.keys.joinToString("")
            val result = engine.encode(text, map, settings)
            assertWithMessage("encode result for $layout").that(result).isInstanceOf(HidEncodeResult.Success::class.java)
        }
    }
}
