package com.barcodebridge.app.transport.tcp

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PairingQrParserTest {

    @Test
    fun `valid pairing QR is parsed into host, port and token`() {
        val settings = PairingQrParser.parse("barcodebridge://pair?host=192.168.1.5&port=9999&token=abc123")
        assertThat(settings).isNotNull()
        assertThat(settings!!.host).isEqualTo("192.168.1.5")
        assertThat(settings.port).isEqualTo(9999)
        assertThat(settings.token).isEqualTo("abc123")
    }

    @Test
    fun `missing token defaults to empty string`() {
        val settings = PairingQrParser.parse("barcodebridge://pair?host=10.0.0.1&port=1234")
        assertThat(settings!!.token).isEmpty()
    }

    @Test
    fun `unrelated QR content is not parsed`() {
        assertThat(PairingQrParser.parse("https://example.com")).isNull()
    }

    @Test
    fun `missing required host field yields null`() {
        assertThat(PairingQrParser.parse("barcodebridge://pair?port=1234")).isNull()
    }

    @Test
    fun `non-numeric port yields null`() {
        assertThat(PairingQrParser.parse("barcodebridge://pair?host=1.2.3.4&port=abc")).isNull()
    }
}
