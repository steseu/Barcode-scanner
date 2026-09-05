package com.barcodebridge.app.transport

sealed interface TransportResult {
    data object Disabled : TransportResult
    data object Success : TransportResult
    data class Failure(val messageResId: Int? = null, val message: String? = null) : TransportResult
}

/** Sends a scanned string to the paired PC via whichever transfer method is active in settings. */
interface TransportSender {
    suspend fun send(text: String): TransportResult
}
