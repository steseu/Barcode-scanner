package com.barcodebridge.app.transport.tcp

import com.barcodebridge.app.data.settings.SettingsRepository
import com.barcodebridge.app.data.settings.TcpSettings
import com.barcodebridge.app.transport.TransportResult
import com.barcodebridge.app.transport.TransportSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

enum class TcpConnectionState { DISCONNECTED, CONNECTING, CONNECTED, UNREACHABLE }

data class TcpStatus(val state: TcpConnectionState, val host: String = "", val port: Int = 0)

/**
 * TCP client for the Windows companion app (transfer method B). Pairing is
 * out-of-band (the PC shows a QR code with host/port/token, scanned once in
 * settings); after that this maintains a long-lived connection and re-sends
 * anything queued in [offlineQueue] once the link comes back.
 */
@Singleton
class TcpTransport @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val offlineQueue: TcpOfflineQueue,
) : TransportSender {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var maintainJob: Job? = null
    private var socket: Socket? = null
    private var writer: BufferedWriter? = null

    private val _status = MutableStateFlow(TcpStatus(TcpConnectionState.DISCONNECTED))
    val status: StateFlow<TcpStatus> = _status.asStateFlow()

    fun start() {
        if (maintainJob?.isActive == true) return
        maintainJob = scope.launch { maintainConnectionLoop() }
    }

    fun stop() {
        maintainJob?.cancel()
        closeSocket()
        _status.value = TcpStatus(TcpConnectionState.DISCONNECTED)
    }

    private suspend fun maintainConnectionLoop() {
        while (currentCoroutineContext().isActive) {
            val tcpSettings = settingsRepository.settings.first().tcp
            if (tcpSettings.host.isBlank()) {
                delay(RETRY_DELAY_MS)
                continue
            }
            val socketIsUsable = socket?.let { it.isConnected && !it.isClosed } ?: false
            if (!socketIsUsable) {
                connect(tcpSettings)
            }
            delay(RETRY_DELAY_MS)
        }
    }

    private suspend fun connect(settings: TcpSettings) {
        _status.value = TcpStatus(TcpConnectionState.CONNECTING, settings.host, settings.port)
        runCatching {
            val newSocket = Socket()
            newSocket.connect(InetSocketAddress(settings.host, settings.port), CONNECT_TIMEOUT_MS)
            val newWriter = newSocket.getOutputStream().bufferedWriter(Charsets.UTF_8)
            newWriter.write("AUTH ${settings.token}\n")
            newWriter.flush()
            socket = newSocket
            writer = newWriter
        }.onSuccess {
            _status.value = TcpStatus(TcpConnectionState.CONNECTED, settings.host, settings.port)
            flushOfflineQueue()
        }.onFailure {
            closeSocket()
            _status.value = TcpStatus(TcpConnectionState.UNREACHABLE, settings.host, settings.port)
        }
    }

    private suspend fun flushOfflineQueue() {
        val pending = offlineQueue.drainAll()
        for (line in pending) {
            if (!writeLine(line)) {
                offlineQueue.enqueue(line)
                break
            }
        }
    }

    private fun writeLine(text: String): Boolean {
        val activeWriter = writer ?: return false
        return runCatching {
            activeWriter.write(text.replace("\r", " ").replace("\n", " "))
            activeWriter.newLine()
            activeWriter.flush()
            true
        }.getOrElse {
            closeSocket()
            false
        }
    }

    override suspend fun send(text: String): TransportResult {
        if (writeLine(text)) return TransportResult.Success
        offlineQueue.enqueue(text)
        _status.value = _status.value.copy(state = TcpConnectionState.UNREACHABLE)
        return TransportResult.Failure(message = "Host unreachable, queued for retry")
    }

    private fun closeSocket() {
        runCatching { writer?.close() }
        runCatching { socket?.close() }
        writer = null
        socket = null
    }

    companion object {
        private const val RETRY_DELAY_MS = 4000L
        private const val CONNECT_TIMEOUT_MS = 4000
    }
}
