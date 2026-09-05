package com.barcodebridge.app.transport.hid

import com.barcodebridge.app.data.settings.HidKeyboardLayout

/**
 * Since the phone always sends the same HID usage/modifier codes (chosen by
 * whatever layout the user picked in settings), what actually appears on the
 * PC depends only on the PC's own active OS layout. So calibration works
 * backwards: decode the exact reports we sent using each candidate layout's
 * reverse table and see which candidate's decoded string matches what the
 * user says really appeared - that candidate is the PC's real layout.
 */
object HidCalibration {
    const val TEST_STRING = "yz-@€ß[]|^ äöü ABC 123"

    data class CharDiff(val index: Int, val actual: Char?, val predicted: Char?)

    data class CandidateScore(
        val layout: HidKeyboardLayout,
        val predicted: String,
        val matchRatio: Double,
        val diffs: List<CharDiff>,
    )

    fun buildReverseMap(layout: KeyboardLayoutMap): Map<Pair<Int, Int>, Char> =
        layout.entries.entries.associate { (char, mapping) -> (mapping.usageCode to mapping.modifiers.toBitmask()) to char }

    fun predict(sentReports: List<HidReport>, candidate: KeyboardLayoutMap): String {
        val reverse = buildReverseMap(candidate)
        return sentReports
            .filter { it != HidReport.RELEASE_ALL }
            .mapNotNull { reverse[it.usageCode to it.modifierBits] }
            .joinToString("")
    }

    fun rank(
        sentReports: List<HidReport>,
        actualObserved: String,
        candidates: Map<HidKeyboardLayout, KeyboardLayoutMap>,
    ): List<CandidateScore> = candidates.map { (layoutId, map) ->
        val predicted = predict(sentReports, map)
        val diffs = diff(actualObserved, predicted)
        val matchRatio = 1.0 - diffs.size.toDouble() / maxOf(actualObserved.length, predicted.length, 1)
        CandidateScore(layoutId, predicted, matchRatio, diffs)
    }.sortedByDescending { it.matchRatio }

    private fun diff(actual: String, predicted: String): List<CharDiff> {
        val length = maxOf(actual.length, predicted.length)
        return (0 until length).mapNotNull { i ->
            val a = actual.getOrNull(i)
            val p = predicted.getOrNull(i)
            if (a != p) CharDiff(i, a, p) else null
        }
    }
}
