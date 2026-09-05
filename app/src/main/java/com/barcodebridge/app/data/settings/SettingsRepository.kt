package com.barcodebridge.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the whole [AppSettings] tree as a single JSON blob. A dozen+
 * loosely related settings (scan, feedback, export, three transfer methods)
 * don't need a dozen+ separate Preferences keys; one versioned document keeps
 * reads/writes atomic and the schema in one place.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        prefs[SETTINGS_KEY]?.let { raw ->
            runCatching { json.decodeFromString(AppSettings.serializer(), raw) }.getOrNull()
        } ?: AppSettings()
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            val current = prefs[SETTINGS_KEY]?.let { raw ->
                runCatching { json.decodeFromString(AppSettings.serializer(), raw) }.getOrNull()
            } ?: AppSettings()
            prefs[SETTINGS_KEY] = json.encodeToString(AppSettings.serializer(), transform(current))
        }
    }

    companion object {
        private val SETTINGS_KEY = stringPreferencesKey("app_settings_json")
    }
}
