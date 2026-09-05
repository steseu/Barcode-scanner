package com.barcodebridge.app.transport.hid

import android.content.Context
import com.barcodebridge.app.data.settings.HidKeyboardLayout
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class KeymapFileDto(
    val layout: String,
    val entries: Map<String, KeyEntryDto>,
)

@Serializable
private data class KeyEntryDto(
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
 * Loads and validates the per-layout JSON files under `assets/keymaps/`.
 * Adding a new keyboard layout is purely a data change (one JSON file + one
 * [HidKeyboardLayout] enum constant) - no code here needs to change.
 */
@Singleton
class KeymapLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = mutableMapOf<HidKeyboardLayout, KeymapLoadResult>()

    fun load(layout: HidKeyboardLayout): KeymapLoadResult = cache.getOrPut(layout) {
        val raw = context.assets.open("keymaps/${layout.assetFileName}").bufferedReader().use { it.readText() }
        parse(raw)
    }

    internal fun parse(raw: String): KeymapLoadResult {
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
