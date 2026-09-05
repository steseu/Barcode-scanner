package com.barcodebridge.app.transport

import com.barcodebridge.app.data.settings.SettingsRepository
import com.barcodebridge.app.data.settings.TransferMethod
import com.barcodebridge.app.transport.hid.BluetoothHidTransport
import com.barcodebridge.app.transport.http.HttpWebhookTransport
import com.barcodebridge.app.transport.tcp.TcpTransport
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Routes a scan to whichever of the three transfer methods is active in settings. */
@Singleton
class CompositeTransportSender @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val hidTransport: BluetoothHidTransport,
    private val tcpTransport: TcpTransport,
    private val httpWebhookTransport: HttpWebhookTransport,
) : TransportSender {

    override suspend fun send(text: String): TransportResult {
        return when (settingsRepository.settings.first().transferMethod) {
            TransferMethod.NONE -> TransportResult.Disabled
            TransferMethod.BLUETOOTH_HID -> hidTransport.send(text)
            TransferMethod.WIFI_TCP -> tcpTransport.send(text)
            TransferMethod.HTTP_WEBHOOK -> httpWebhookTransport.send(text)
        }
    }
}
