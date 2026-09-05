package com.barcodebridge.app.transport.hid

/** One 8-byte USB HID boot-keyboard report: [modifierByte, reserved=0, usageCode, 0,0,0,0,0]. */
data class HidReport(val modifierBits: Int, val usageCode: Int) {
    fun toBytes(): ByteArray = byteArrayOf(
        modifierBits.toByte(), 0, usageCode.toByte(), 0, 0, 0, 0, 0
    )

    companion object {
        val RELEASE_ALL = HidReport(0, 0)
    }
}

const val KEY_ENTER = 40
const val KEY_TAB = 43
const val KEY_SPACE = 44
