package com.barcodebridge.app.transport.hid

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KeymapParserValidationTest {

    @Test
    fun `valid entries are parsed`() {
        val json = """
            { "layout": "test", "entries": { "a": { "usageCode": 4, "modifiers": [] } } }
        """.trimIndent()
        val result = KeymapParser.parse(json)
        assertThat(result.issues).isEmpty()
        assertThat(result.layoutMap.entries['a']?.usageCode).isEqualTo(4)
    }

    @Test
    fun `entry with unknown modifier is reported and excluded`() {
        val json = """
            { "layout": "test", "entries": {
                "a": { "usageCode": 4, "modifiers": [] },
                "b": { "usageCode": 5, "modifiers": ["SUPER_ALT"] }
            } }
        """.trimIndent()
        val result = KeymapParser.parse(json)
        assertThat(result.issues).isNotEmpty()
        assertThat(result.issues.any { it.character == "b" }).isTrue()
        assertThat(result.layoutMap.entries).doesNotContainKey('b')
        assertThat(result.layoutMap.entries).containsKey('a')
    }

    @Test
    fun `entry with out-of-range usage code is reported and excluded`() {
        val json = """
            { "layout": "test", "entries": {
                "a": { "usageCode": 4, "modifiers": [] },
                "b": { "usageCode": 9999, "modifiers": [] }
            } }
        """.trimIndent()
        val result = KeymapParser.parse(json)
        assertThat(result.issues.any { it.character == "b" && it.reason.contains("usageCode") }).isTrue()
        assertThat(result.layoutMap.entries).doesNotContainKey('b')
    }

    @Test
    fun `entry key that is not exactly one character is reported and excluded`() {
        val json = """
            { "layout": "test", "entries": {
                "a": { "usageCode": 4, "modifiers": [] },
                "ab": { "usageCode": 5, "modifiers": [] }
            } }
        """.trimIndent()
        val result = KeymapParser.parse(json)
        assertThat(result.issues.any { it.character == "ab" }).isTrue()
        assertThat(result.layoutMap.entries).hasSize(1)
    }

    @Test
    fun `deadKey flag defaults to false when omitted`() {
        val json = """
            { "layout": "test", "entries": { "a": { "usageCode": 4 } } }
        """.trimIndent()
        val result = KeymapParser.parse(json)
        assertThat(result.layoutMap.entries.getValue('a').deadKey).isFalse()
    }
}
