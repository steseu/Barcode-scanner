package com.barcodebridge.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.barcodebridge.app.data.settings.AppLanguage
import com.barcodebridge.app.data.settings.SettingsRepository
import com.barcodebridge.app.transport.hid.BluetoothHidTransport
import com.barcodebridge.app.transport.tcp.TcpTransport
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class BarcodeBridgeApp : Application() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var bluetoothHidTransport: BluetoothHidTransport

    @Inject
    lateinit var tcpTransport: TcpTransport

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        applyPersistedLocale()
        // Both are safe no-ops until the user actually enables/configures the
        // corresponding transfer method in settings (permission/host checks
        // happen inside each transport).
        bluetoothHidTransport.startRegistration()
        tcpTransport.start()
    }

    /**
     * Per-app language must be applied as early as possible (before any UI is
     * created) so the first frame already renders in the persisted language.
     */
    private fun applyPersistedLocale() {
        appScope.launch {
            val language = settingsRepository.settings.first().language
            val locales = when (language) {
                AppLanguage.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
                AppLanguage.GERMAN -> LocaleListCompat.forLanguageTags("de")
                AppLanguage.ENGLISH -> LocaleListCompat.forLanguageTags("en")
            }
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }
}
