package com.barcodebridge.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.barcodebridge.app.data.settings.AppLanguage
import com.barcodebridge.app.data.settings.SettingsRepository
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

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        applyPersistedLocale()
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
