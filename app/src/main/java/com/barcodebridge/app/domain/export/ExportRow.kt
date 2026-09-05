package com.barcodebridge.app.domain.export

/**
 * A single scan already flattened into display-ready strings (localized date/
 * time, translated format name, resolved session name). Keeping the
 * exporters below free of Android/locale APIs makes them plain, deterministic
 * JVM unit tests.
 */
data class ExportRow(
    val content: String,
    val format: String,
    val date: String,
    val time: String,
    val timestamp: String,
    val note: String,
    val session: String,
    val index: Int,
)
