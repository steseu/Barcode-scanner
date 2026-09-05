package com.barcodebridge.app.transport.hid

/** HID modifier byte bits (USB HID Boot Keyboard report, byte 0). */
enum class KeyModifier(val bit: Int) {
    CTRL(0x01),
    SHIFT(0x02),
    /** Right Alt - this is how AltGr must be sent, never as Ctrl+Alt. */
    ALTGR(0x40),
}

data class KeyMapping(
    val usageCode: Int,
    val modifiers: Set<KeyModifier> = emptySet(),
    /**
     * True for keys that on the real keyboard are "dead" (´, `, ^, AltGr+`):
     * pressing them alone doesn't emit a glyph until a following keystroke
     * commits it. To output the accent character *by itself*, the engine
     * must send the dead key followed by a Space.
     */
    val deadKey: Boolean = false,
)

data class KeyboardLayoutMap(
    val layoutId: String,
    val entries: Map<Char, KeyMapping>,
)

fun Set<KeyModifier>.toBitmask(): Int = fold(0) { acc, m -> acc or m.bit }
