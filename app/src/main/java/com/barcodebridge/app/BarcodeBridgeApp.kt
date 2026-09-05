package com.barcodebridge.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.barcodebridge.app.data.settings.AppLanguage
import com.barcodebridge.app.data.settings.AppSettings
import com.barcodebridge.app.data.settings.SettingsRepository
import com.barcodebridge.app.data.settings.TransferMethod
import com.barcodebridge.app.transport.hid.BluetoothHidTransport
import com.barcodebridge.app.transport.tcp.TcpTransport
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltAndroidApp
class BarcodeBridgeApp : Application() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var bluetoothHidTransport: BluetoothHidTransport

    @Inject
    lateinit var tcpTransport: TcpTransport

    override fun onCreate() {
        super.onCreate()

        // Read once, synchronously: the persisted language has to be applied
        // before the first activity is created (otherwise the first frame
        // renders in the wrong language and then recreates), and
        // AppCompatDelegate.setApplicationLocales must run on the main thread.
        val settings = runCatching {
            runBlocking { settingsRepository.settings.first() }
        }.getOrDefault(AppSettings())

        applyLocale(settings.language)
        startActiveTransport(settings.transferMethod)
    }

    private fun applyLocale(language: AppLanguage) {
        val locales = when (language) {
            AppLanguage.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
            AppLanguage.GERMAN -> LocaleListCompat.forLanguageTags("de")
            AppLanguage.ENGLISH -> LocaleListCompat.forLanguageTags("en")
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    /** Only the configured method is started; the other two stay completely idle. */
    private fun startActiveTransport(method: TransferMethod) {
        when (method) {
            TransferMethod.BLUETOOTH_HID -> bluetoothHidTransport.startRegistration()
            TransferMethod.WIFI_TCP -> tcpTransport.start()
            TransferMethod.HTTP_WEBHOOK, TransferMethod.NONE -> Unit
        }
    }
}
