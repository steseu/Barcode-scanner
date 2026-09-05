package com.barcodebridge.app.ui.history

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.CalendarContract
import androidx.core.content.FileProvider
import com.barcodebridge.app.domain.model.CalendarEventParser
import com.barcodebridge.app.domain.model.WifiCredentials
import com.barcodebridge.app.domain.model.WifiQrParser
import java.io.File

/** Builds the platform intents for the content-aware history actions (open URL, add contact, connect Wi-Fi, add event). */
object ScanContentActions {

    fun openUrlIntent(url: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))

    /**
     * Stages the raw vCard as a temp file and hands it to any app that
     * registers for vCard MIME types (typically Contacts) - this avoids
     * hand-parsing vCard fields and matches how sharing a .vcf file works.
     */
    fun addContactIntent(context: Context, vCardContent: String): Intent {
        val dir = File(context.cacheDir, "vcards").apply { mkdirs() }
        val file = File(dir, "contact_${System.currentTimeMillis()}.vcf")
        file.writeText(vCardContent, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "text/x-vcard")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun addCalendarEventIntent(content: String): Intent {
        val event = CalendarEventParser.parse(content)
        return Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, event.title)
            putExtra(CalendarContract.Events.EVENT_LOCATION, event.location)
            putExtra(CalendarContract.Events.DESCRIPTION, event.description)
            event.startEpochMillis?.let { putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
            event.endEpochMillis?.let { putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it) }
        }
    }

    sealed interface WifiConnectResult {
        data class Suggested(val ssid: String) : WifiConnectResult
        data class OpenSettingsManually(val credentials: WifiCredentials) : WifiConnectResult
        data object InvalidQr : WifiConnectResult
    }

    /**
     * API 29+ can suggest the network directly via [WifiNetworkSuggestion]
     * without any Wi-Fi permission. Older devices need `CHANGE_WIFI_STATE`,
     * which this app deliberately doesn't request (see README limitations),
     * so they fall back to opening system Wi-Fi settings with the parsed
     * SSID/password shown to the user for manual entry.
     */
    fun connectToWifi(context: Context, qrContent: String): WifiConnectResult {
        val credentials = WifiQrParser.parse(qrContent) ?: return WifiConnectResult.InvalidQr
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val suggestionBuilder = WifiNetworkSuggestion.Builder().setSsid(credentials.ssid)
            if (credentials.security.equals("WPA", ignoreCase = true) && credentials.password.isNotBlank()) {
                suggestionBuilder.setWpa2Passphrase(credentials.password)
            }
            val wifiManager = context.applicationContext.getSystemService(android.net.wifi.WifiManager::class.java)
            val status = wifiManager.addNetworkSuggestions(listOf(suggestionBuilder.build()))
            if (status == android.net.wifi.WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
                return WifiConnectResult.Suggested(credentials.ssid)
            }
        }
        return WifiConnectResult.OpenSettingsManually(credentials)
    }

    fun openWifiSettingsIntent(): Intent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
}
