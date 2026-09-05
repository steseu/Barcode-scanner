package com.barcodebridge.app.transport.hid

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class KeymapFileDto(
    val layout: String,
    val entries: Map<String, KeyEntryDto>,
)

@Serializable
internal data class KeyEntryDto(
    val usageCode: Int,
    val modifiers: List<String> = emptyList(),
    val deadKey: Boolean = false,
)

data class KeymapValidationIssue(val character: String, val reason: String)

data class KeymapLoadResult(
    val layoutMap: KeyboardLayoutMap,
    val issues: List<KeymapValidationIssue>,
)

/**
 * Pure JSON -> [KeyboardLayoutMap] parser, deliberately free of any Android
 * dependency so the full-ASCII round-trip and validation tests can run as
 * plain JVM unit tests against the real files in `assets/keymaps/`.
 */
object KeymapParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String): KeymapLoadResult {
        val dto = json.decodeFromString(KeymapFileDto.serializer(), raw)
        val issues = mutableListOf<KeymapValidationIssue>()
        val entries = mutableMapOf<Char, KeyMapping>()

        for ((key, entry) in dto.entries) {
            if (key.length != 1) {
                issues.add(KeymapValidationIssue(key, "key must be exactly one character"))
                continue
            }
            if (entry.usageCode !in 0..231) {
                issues.add(KeymapValidationIssue(key, "usageCode ${entry.usageCode} out of range"))
                continue
            }
            val modifiers = mutableSetOf<KeyModifier>()
            var modifierError = false
            for (m in entry.modifiers) {
                val resolved = runCatching { KeyModifier.valueOf(m) }.getOrNull()
                if (resolved == null) {
                    issues.add(KeymapValidationIssue(key, "unknown modifier '$m'"))
                    modifierError = true
                } else {
                    modifiers.add(resolved)
                }
            }
            if (modifierError) continue
            entries[key.single()] = KeyMapping(entry.usageCode, modifiers, entry.deadKey)
        }

        return KeymapLoadResult(KeyboardLayoutMap(dto.layout, entries), issues)
    }
}
