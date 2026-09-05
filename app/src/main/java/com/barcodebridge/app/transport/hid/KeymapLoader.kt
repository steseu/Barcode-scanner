package com.barcodebridge.app.transport.hid

import android.content.Context
import com.barcodebridge.app.data.settings.HidKeyboardLayout
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads and validates the per-layout JSON files under `assets/keymaps/`.
 * Adding a new keyboard layout is purely a data change (one JSON file + one
 * [HidKeyboardLayout] enum constant) - no code here needs to change. Actual
 * parsing/validation lives in [KeymapParser] so it can be unit tested without
 * an Android asset manager.
 */
@Singleton
class KeymapLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cache = mutableMapOf<HidKeyboardLayout, KeymapLoadResult>()

    fun load(layout: HidKeyboardLayout): KeymapLoadResult = cache.getOrPut(layout) {
        val raw = context.assets.open("keymaps/${layout.assetFileName}").bufferedReader().use { it.readText() }
        KeymapParser.parse(raw)
    }
}
