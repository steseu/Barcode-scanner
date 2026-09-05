package com.barcodebridge.app.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WifiQrParserTest {

    @Test
    fun `parses ssid, password and security type`() {
        val creds = WifiQrParser.parse("WIFI:S:MyNetwork;T:WPA;P:secret123;;")
        assertThat(creds).isNotNull()
        assertThat(creds!!.ssid).isEqualTo("MyNetwork")
        assertThat(creds.password).isEqualTo("secret123")
        assertThat(creds.security).isEqualTo("WPA")
    }

    @Test
    fun `escaped semicolons in ssid are unescaped`() {
        val creds = WifiQrParser.parse("WIFI:S:My\\;Network;T:WPA;P:pw;;")
        assertThat(creds!!.ssid).isEqualTo("My;Network")
    }

    @Test
    fun `missing password defaults to empty and security defaults to nopass`() {
        val creds = WifiQrParser.parse("WIFI:S:OpenNetwork;;")
        assertThat(creds!!.password).isEmpty()
        assertThat(creds.security).isEqualTo("nopass")
    }

    @Test
    fun `content classifier detects wifi, url, vcard, event and plain text`() {
        assertThat(BarcodeContentClassifier.classify("WIFI:S:x;;")).isEqualTo(BarcodeContentType.WIFI)
        assertThat(BarcodeContentClassifier.classify("https://example.com")).isEqualTo(BarcodeContentType.URL)
        assertThat(BarcodeContentClassifier.classify("BEGIN:VCARD\nEND:VCARD")).isEqualTo(BarcodeContentType.CONTACT_VCARD)
        assertThat(BarcodeContentClassifier.classify("BEGIN:VEVENT\nEND:VEVENT")).isEqualTo(BarcodeContentType.CALENDAR_EVENT)
        assertThat(BarcodeContentClassifier.classify("4006381333931")).isEqualTo(BarcodeContentType.PLAIN_TEXT)
    }
}
