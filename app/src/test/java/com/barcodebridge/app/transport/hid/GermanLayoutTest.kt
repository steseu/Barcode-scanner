package com.barcodebridge.app.transport.hid

import com.barcodebridge.app.data.settings.HidKeyboardLayout
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Every bullet point from the spec's "zwingend abgedeckte Fallstricke" list, pinned to a test. */
class GermanLayoutTest {

    private val de = KeymapTestUtil.loadMap(HidKeyboardLayout.DE)
    private val us = KeymapTestUtil.loadMap(HidKeyboardLayout.EN_US)

    @Test
    fun `y and z are swapped between DE and US via usage codes 0x1C and 0x1D`() {
        assertThat(de.entries.getValue('z').usageCode).isEqualTo(0x1C)
        assertThat(de.entries.getValue('y').usageCode).isEqualTo(0x1D)
        assertThat(us.entries.getValue('y').usageCode).isEqualTo(0x1C)
        assertThat(us.entries.getValue('z').usageCode).isEqualTo(0x1D)
    }

    @Test
    fun `umlauts and sharp s use their documented usage codes`() {
        assertThat(de.entries.getValue('ä').usageCode).isEqualTo(0x34)
        assertThat(de.entries.getValue('ö').usageCode).isEqualTo(0x33)
        assertThat(de.entries.getValue('ü').usageCode).isEqualTo(0x2F)
        assertThat(de.entries.getValue('ß').usageCode).isEqualTo(0x2D)
    }

    @Test
    fun `hyphen is on usage 0x38 not 0x2D on DE`() {
        val hyphen = de.entries.getValue('-')
        assertThat(hyphen.usageCode).isEqualTo(0x38)
        assertThat(hyphen.usageCode).isNotEqualTo(0x2D)
    }

    @Test
    fun `at sign is AltGr plus Q`() {
        val q = de.entries.getValue('q')
        val at = de.entries.getValue('@')
        assertThat(at.usageCode).isEqualTo(q.usageCode)
        assertThat(at.modifiers).containsExactly(KeyModifier.ALTGR)
    }

    @Test
    fun `euro sign is AltGr plus E`() {
        val e = de.entries.getValue('e')
        val euro = de.entries.getValue('€')
        assertThat(euro.usageCode).isEqualTo(e.usageCode)
        assertThat(euro.modifiers).containsExactly(KeyModifier.ALTGR)
    }

    @Test
    fun `backslash is AltGr plus sharp-s key`() {
        val ss = de.entries.getValue('ß')
        val backslash = de.entries.getValue('\\')
        assertThat(backslash.usageCode).isEqualTo(ss.usageCode)
        assertThat(backslash.modifiers).containsExactly(KeyModifier.ALTGR)
    }

    @Test
    fun `pipe is AltGr plus less-than key`() {
        val lessThan = de.entries.getValue('<')
        val pipe = de.entries.getValue('|')
        assertThat(pipe.usageCode).isEqualTo(lessThan.usageCode)
        assertThat(pipe.modifiers).containsExactly(KeyModifier.ALTGR)
    }

    @Test
    fun `AltGr is sent as right-Alt bit 0x40, never combined with Ctrl`() {
        assertThat(KeyModifier.ALTGR.bit).isEqualTo(0x40)
        for (mapping in de.entries.values) {
            if (KeyModifier.ALTGR in mapping.modifiers) {
                assertThat(mapping.modifiers).doesNotContain(KeyModifier.CTRL)
            }
        }
    }

    @Test
    fun `tilde is AltGr plus plus-key and is a dead key`() {
        val plus = de.entries.getValue('+')
        val tilde = de.entries.getValue('~')
        assertThat(tilde.usageCode).isEqualTo(plus.usageCode)
        assertThat(tilde.modifiers).containsExactly(KeyModifier.ALTGR)
        assertThat(tilde.deadKey).isTrue()
    }

    @Test
    fun `brackets and braces are on AltGr of digits 7, 8, 9 and 0`() {
        assertThat(de.entries.getValue('{').usageCode).isEqualTo(de.entries.getValue('7').usageCode)
        assertThat(de.entries.getValue('[').usageCode).isEqualTo(de.entries.getValue('8').usageCode)
        assertThat(de.entries.getValue(']').usageCode).isEqualTo(de.entries.getValue('9').usageCode)
        assertThat(de.entries.getValue('}').usageCode).isEqualTo(de.entries.getValue('0').usageCode)
        for (c in listOf('{', '[', ']', '}')) {
            assertThat(de.entries.getValue(c).modifiers).containsExactly(KeyModifier.ALTGR)
        }
    }

    @Test
    fun `circumflex, acute and grave are dead keys`() {
        assertThat(de.entries.getValue('^').deadKey).isTrue()
        assertThat(de.entries.getValue('´').deadKey).isTrue()
        assertThat(de.entries.getValue('`').deadKey).isTrue()
    }

    @Test
    fun `less-than and greater-than have their own key at usage 0x64`() {
        assertThat(de.entries.getValue('<').usageCode).isEqualTo(0x64)
        assertThat(de.entries.getValue('>').usageCode).isEqualTo(0x64)
        // On US layouts this ISO key doesn't exist at all.
        assertThat(us.entries.values.none { it.usageCode == 0x64 }).isTrue()
    }
}
