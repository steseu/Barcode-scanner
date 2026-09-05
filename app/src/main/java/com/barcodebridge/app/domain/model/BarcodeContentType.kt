package com.barcodebridge.app.domain.model

enum class BarcodeContentType { URL, WIFI, CONTACT_VCARD, CALENDAR_EVENT, PLAIN_TEXT }

object BarcodeContentClassifier {
    fun classify(content: String): BarcodeContentType {
        val trimmed = content.trim()
        return when {
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) -> BarcodeContentType.URL
            trimmed.startsWith("WIFI:", ignoreCase = true) -> BarcodeContentType.WIFI
            trimmed.startsWith("BEGIN:VCARD", ignoreCase = true) -> BarcodeContentType.CONTACT_VCARD
            trimmed.startsWith("BEGIN:VEVENT", ignoreCase = true) ||
                trimmed.startsWith("BEGIN:VCALENDAR", ignoreCase = true) -> BarcodeContentType.CALENDAR_EVENT
            else -> BarcodeContentType.PLAIN_TEXT
        }
    }
}

/** Parsed fields from a `WIFI:S:<ssid>;T:<WPA|WEP|nopass>;P:<password>;;` QR payload. */
data class WifiCredentials(val ssid: String, val password: String, val security: String)

object WifiQrParser {
    private val fieldRegex = Regex("""(?<!\\)([A-Z]):((?:\\.|[^;])*);""")

    fun parse(content: String): WifiCredentials? {
        val payload = content.substringAfter("WIFI:", "")
        if (payload.isEmpty()) return null
        val fields = fieldRegex.findAll("$payload;").associate { match ->
            match.groupValues[1] to unescape(match.groupValues[2])
        }
        val ssid = fields["S"] ?: return null
        return WifiCredentials(
            ssid = ssid,
            password = fields["P"].orEmpty(),
            security = fields["T"]?.takeIf { it.isNotBlank() } ?: "nopass",
        )
    }

    private fun unescape(value: String): String = value
        .replace("\\;", ";")
        .replace("\\,", ",")
        .replace("\\:", ":")
        .replace("\\\\", "\\")
}
