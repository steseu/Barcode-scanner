package com.barcodebridge.app.export

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Resolves `{timestamp}` in a filename template to a sortable, filesystem-safe pattern. */
object FilenameTemplate {

    private val PATTERN = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

    fun resolve(template: String, now: LocalDateTime = LocalDateTime.now()): String =
        template.replace("{timestamp}", PATTERN.format(now))

    fun withExtension(filename: String, extension: String): String =
        if (filename.endsWith(".$extension", ignoreCase = true)) filename else "$filename.$extension"
}
