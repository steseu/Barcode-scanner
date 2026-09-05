package com.barcodebridge.app.transport.hid

import com.barcodebridge.app.data.settings.HidSettings
import com.barcodebridge.app.data.settings.HidSuffix
import com.barcodebridge.app.data.settings.UnmappableCharAction

sealed interface HidEncodeResult {
    data class Success(val reports: List<HidReport>) : HidEncodeResult
    data class Unmappable(val characters: List<Char>) : HidEncodeResult
}

/**
 * Turns scanned text into a sequence of HID boot-keyboard reports, applying
 * prefix/suffix, dead-key commit sequences, and the doubled-report rule for
 * two identical consecutive keystrokes. Pure and Android-free by design so
 * every layout's full-ASCII behavior can be unit tested without a device.
 */
class KeymapEngine {

    fun encode(text: String, layout: KeyboardLayoutMap, settings: HidSettings): HidEncodeResult {
        if (settings.unmappableAction == UnmappableCharAction.ABORT_AND_WARN) {
            val unmappable = findUnmappable(settings.prefix + text, layout)
            if (unmappable.isNotEmpty()) return HidEncodeResult.Unmappable(unmappable)
        }

        val reports = mutableListOf<HidReport>()
        var lastKeyReport: HidReport? = null

        lastKeyReport = appendText(settings.prefix, layout, settings.unmappableAction, reports, lastKeyReport)
        lastKeyReport = appendText(text, layout, settings.unmappableAction, reports, lastKeyReport)
        appendSuffix(settings.suffix, reports, lastKeyReport)

        return HidEncodeResult.Success(reports)
    }

    private fun findUnmappable(text: String, layout: KeyboardLayoutMap): List<Char> =
        text.filter { it !in layout.entries }.distinct()

    private fun appendText(
        text: String,
        layout: KeyboardLayoutMap,
        unmappableAction: UnmappableCharAction,
        reports: MutableList<HidReport>,
        initialLast: HidReport?,
    ): HidReport? {
        var last = initialLast
        for (char in text) {
            val mapping = layout.entries[char]
            if (mapping == null) {
                when (unmappableAction) {
                    UnmappableCharAction.SKIP -> continue
                    UnmappableCharAction.REPLACE_WITH_QUESTION_MARK -> {
                        val replacement = layout.entries['?'] ?: continue
                        last = appendMapping(replacement, reports, last)
                    }
                    UnmappableCharAction.ABORT_AND_WARN -> continue // already handled up-front
                }
            } else {
                last = appendMapping(mapping, reports, last)
            }
        }
        return last
    }

    private fun appendMapping(mapping: KeyMapping, reports: MutableList<HidReport>, last: HidReport?): HidReport {
        val keyReport = HidReport(mapping.modifiers.toBitmask(), mapping.usageCode)
        if (last == keyReport) reports.add(HidReport.RELEASE_ALL)
        reports.add(keyReport)
        reports.add(HidReport.RELEASE_ALL)
        if (mapping.deadKey) {
            // Commit the standalone accent character by following the dead
            // key with Space, exactly as a human would on a physical keyboard.
            val spaceReport = HidReport(0, KEY_SPACE)
            reports.add(spaceReport)
            reports.add(HidReport.RELEASE_ALL)
            return spaceReport
        }
        return keyReport
    }

    private fun appendSuffix(suffix: HidSuffix, reports: MutableList<HidReport>, last: HidReport?) {
        val usageCode = when (suffix) {
            HidSuffix.NONE -> return
            HidSuffix.ENTER, HidSuffix.CRLF -> KEY_ENTER
            HidSuffix.TAB -> KEY_TAB
        }
        val keyReport = HidReport(0, usageCode)
        if (last == keyReport) reports.add(HidReport.RELEASE_ALL)
        reports.add(keyReport)
        reports.add(HidReport.RELEASE_ALL)
    }
}
