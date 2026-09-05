package com.barcodebridge.app.domain.model

import com.google.mlkit.vision.barcode.common.Barcode

/**
 * App-level barcode format, decoupled from the ML Kit constants so Room,
 * DataStore and exports don't depend on the vision SDK's integer codes.
 */
enum class BarcodeFormat(val displayName: String) {
    EAN_8("EAN-8"),
    EAN_13("EAN-13"),
    UPC_A("UPC-A"),
    UPC_E("UPC-E"),
    CODE_39("Code 39"),
    CODE_93("Code 93"),
    CODE_128("Code 128"),
    ITF("ITF"),
    CODABAR("Codabar"),
    QR_CODE("QR Code"),
    DATA_MATRIX("Data Matrix"),
    PDF_417("PDF417"),
    AZTEC("Aztec"),
    UNKNOWN("Unknown");

    companion object {
        fun fromMlKit(mlKitFormat: Int): BarcodeFormat = when (mlKitFormat) {
            Barcode.FORMAT_EAN_8 -> EAN_8
            Barcode.FORMAT_EAN_13 -> EAN_13
            Barcode.FORMAT_UPC_A -> UPC_A
            Barcode.FORMAT_UPC_E -> UPC_E
            Barcode.FORMAT_CODE_39 -> CODE_39
            Barcode.FORMAT_CODE_93 -> CODE_93
            Barcode.FORMAT_CODE_128 -> CODE_128
            Barcode.FORMAT_ITF -> ITF
            Barcode.FORMAT_CODABAR -> CODABAR
            Barcode.FORMAT_QR_CODE -> QR_CODE
            Barcode.FORMAT_DATA_MATRIX -> DATA_MATRIX
            Barcode.FORMAT_PDF417 -> PDF_417
            Barcode.FORMAT_AZTEC -> AZTEC
            else -> UNKNOWN
        }

        /** Bitmask of all formats this app supports, for [com.google.mlkit.vision.barcode.BarcodeScannerOptions]. */
        const val SUPPORTED_MLKIT_FORMATS_MASK =
            Barcode.FORMAT_EAN_8 or Barcode.FORMAT_EAN_13 or Barcode.FORMAT_UPC_A or
                Barcode.FORMAT_UPC_E or Barcode.FORMAT_CODE_39 or Barcode.FORMAT_CODE_93 or
                Barcode.FORMAT_CODE_128 or Barcode.FORMAT_ITF or Barcode.FORMAT_CODABAR or
                Barcode.FORMAT_QR_CODE or Barcode.FORMAT_DATA_MATRIX or Barcode.FORMAT_PDF417 or
                Barcode.FORMAT_AZTEC
    }
}
