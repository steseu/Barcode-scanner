package com.barcodebridge.app.transport.http

import com.barcodebridge.app.data.settings.HttpSettings
import com.barcodebridge.app.data.settings.SettingsRepository
import com.barcodebridge.app.data.settings.WebhookPayloadFormat
import com.barcodebridge.app.transport.TransportResult
import com.barcodebridge.app.transport.TransportSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/** Posts each scan to a user-configured webhook URL as JSON or plain text, with optional custom headers. */
@Singleton
class HttpWebhookTransport @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val client: OkHttpClient,
) : TransportSender {

    override suspend fun send(text: String): TransportResult = withContext(Dispatchers.IO) {
        val settings = settingsRepository.settings.first().http
        if (settings.url.isBlank()) return@withContext TransportResult.Failure(message = "No webhook URL configured")

        val request = buildRequest(settings, text)
        runCatching {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    TransportResult.Success
                } else {
                    TransportResult.Failure(message = "HTTP ${response.code}")
                }
            }
        }.getOrElse { e -> TransportResult.Failure(message = e.message ?: "request failed") }
    }

    fun buildRequest(settings: HttpSettings, text: String): Request {
        val body = when (settings.payloadFormat) {
            WebhookPayloadFormat.JSON -> {
                val json = JsonObject(mapOf("content" to JsonPrimitive(text)))
                json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            }
            WebhookPayloadFormat.PLAIN_TEXT -> text.toRequestBody("text/plain; charset=utf-8".toMediaType())
        }
        val builder = Request.Builder().url(settings.url).post(body)
        settings.headers.forEach { (key, value) -> builder.addHeader(key, value) }
        return builder.build()
    }
}
