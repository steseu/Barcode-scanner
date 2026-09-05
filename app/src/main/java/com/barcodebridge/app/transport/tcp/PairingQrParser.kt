package com.barcodebridge.app.transport.tcp

import com.barcodebridge.app.data.settings.TcpSettings

/**
 * Parses the pairing QR code shown by the Windows companion app:
 * `barcodebridge://pair?host=<ip>&port=<port>&token=<token>`.
 */
object PairingQrParser {
    private const val SCHEME_PREFIX = "barcodebridge://pair"

    fun parse(content: String): TcpSettings? {
        if (!content.startsWith(SCHEME_PREFIX)) return null
        val query = content.substringAfter('?', "")
        if (query.isEmpty()) return null
        val params = query.split('&').mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) null else part.substring(0, idx) to part.substring(idx + 1)
        }.toMap()

        val host = params["host"] ?: return null
        val port = params["port"]?.toIntOrNull() ?: return null
        val token = params["token"].orEmpty()
        return TcpSettings(host = host, port = port, token = token)
    }
}
