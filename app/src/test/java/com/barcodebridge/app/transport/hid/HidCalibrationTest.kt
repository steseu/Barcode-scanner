package com.barcodebridge.app.transport.hid

import com.barcodebridge.app.data.settings.HidKeyboardLayout
import com.barcodebridge.app.data.settings.HidSettings
import com.barcodebridge.app.data.settings.UnmappableCharAction
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HidCalibrationTest {

    @Test
    fun `decoding sent reports with the same layout reproduces the original text`() {
        val de = KeymapTestUtil.loadMap(HidKeyboardLayout.DE)
        val engine = KeymapEngine()
        val settings = HidSettings(layout = HidKeyboardLayout.DE, unmappableAction = UnmappableCharAction.SKIP)
        val text = "abc123"
        val reports = (engine.encode(text, de, settings) as HidEncodeResult.Success).reports
        val predicted = HidCalibration.predict(reports, de)
        assertThat(predicted).isEqualTo(text)
    }

    @Test
    fun `ranking prefers the layout that decodes back to the observed string`() {
        val de = KeymapTestUtil.loadMap(HidKeyboardLayout.DE)
        val us = KeymapTestUtil.loadMap(HidKeyboardLayout.EN_US)
        val engine = KeymapEngine()
        val settings = HidSettings(layout = HidKeyboardLayout.DE, unmappableAction = UnmappableCharAction.SKIP)
        // Sent while assuming DE, but the PC was actually on US layout: y/z will
        // come out swapped relative to what the phone intended.
        val reports = (engine.encode("zy", de, settings) as HidEncodeResult.Success).reports
        val actualOnUsPc = HidCalibration.predict(reports, us)

        val ranked = HidCalibration.rank(reports, actualOnUsPc, mapOf(HidKeyboardLayout.DE to de, HidKeyboardLayout.EN_US to us))
        assertThat(ranked.first().layout).isEqualTo(HidKeyboardLayout.EN_US)
        assertThat(ranked.first().matchRatio).isEqualTo(1.0)
    }
}
