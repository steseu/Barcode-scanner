package com.barcodebridge.app.transport.tcp

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Durable FIFO of scans that couldn't be sent while the TCP companion app was unreachable. */
@Singleton
class TcpOfflineQueue @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val json = Json
    private val serializer = ListSerializer(String.serializer())

    suspend fun enqueue(text: String) {
        dataStore.edit { prefs ->
            val current = readList(prefs[QUEUE_KEY])
            prefs[QUEUE_KEY] = json.encodeToString(serializer, current + text)
        }
    }

    suspend fun peekCount(): Int = readList(dataStore.data.first()[QUEUE_KEY]).size

    /** Removes and returns every queued entry, in FIFO order. */
    suspend fun drainAll(): List<String> {
        var drained: List<String> = emptyList()
        dataStore.edit { prefs ->
            drained = readList(prefs[QUEUE_KEY])
            prefs[QUEUE_KEY] = json.encodeToString(serializer, emptyList())
        }
        return drained
    }

    private fun readList(raw: String?): List<String> =
        raw?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() } ?: emptyList()

    companion object {
        private val QUEUE_KEY = stringPreferencesKey("tcp_offline_queue_json")
    }
}
