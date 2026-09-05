package com.barcodebridge.app.transport.hid

import com.barcodebridge.app.data.settings.HidKeyboardLayout
import java.io.File

/**
 * Unit tests run on the plain JVM (no AssetManager), so the real layout JSON
 * files are read straight off disk - Gradle runs unit test tasks with the
 * module directory as the working directory, so this path is stable.
 */
object KeymapTestUtil {
    fun loadResult(layout: HidKeyboardLayout): KeymapLoadResult {
        val file = File("src/main/assets/keymaps/${layout.assetFileName}")
        check(file.exists()) { "Missing keymap asset: ${file.absolutePath}" }
        return KeymapParser.parse(file.readText())
    }

    fun loadMap(layout: HidKeyboardLayout): KeyboardLayoutMap = loadResult(layout).layoutMap
}
