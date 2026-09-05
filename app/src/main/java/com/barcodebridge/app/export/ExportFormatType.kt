package com.barcodebridge.app.export

enum class ExportFormatType(val extension: String, val mimeType: String) {
    CSV("csv", "text/csv"),
    TXT("txt", "text/plain"),
}

data class PreparedExport(
    val filename: String,
    val mimeType: String,
    val bytes: ByteArray,
)
