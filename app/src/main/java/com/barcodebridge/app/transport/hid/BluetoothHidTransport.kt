package com.barcodebridge.app.transport.hid

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import androidx.core.content.ContextCompat
import com.barcodebridge.app.data.settings.SettingsRepository
import com.barcodebridge.app.transport.TransportResult
import com.barcodebridge.app.transport.TransportSender
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

enum class HidConnectionState { DISABLED, DISCONNECTED, CONNECTING, CONNECTED }

data class HidStatus(val state: HidConnectionState, val deviceName: String? = null)

/**
 * Registers this device as a Bluetooth HID keyboard (`BluetoothHidDevice`,
 * API 28+) so scans get typed straight into whatever window has focus on the
 * paired Windows PC - a "keyboard wedge" with no companion software needed.
 *
 * Pairing itself happens from the Windows Bluetooth settings once this app
 * has registered the HID app record and the phone is discoverable; from
 * there [connectionState] reflects what Windows does.
 */
@Singleton
class BluetoothHidTransport @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val keymapLoader: KeymapLoader,
    private val keymapEngine: KeymapEngine,
) : TransportSender {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    private var hidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow(HidStatus(HidConnectionState.DISCONNECTED))
    val status: StateFlow<HidStatus> = _status.asStateFlow()

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            hidDevice = proxy as BluetoothHidDevice
            registerApp()
        }

        override fun onServiceDisconnected(profile: Int) {
            hidDevice = null
            _status.value = HidStatus(HidConnectionState.DISCONNECTED)
        }
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevice = device
                    _status.value = HidStatus(HidConnectionState.CONNECTED, device.safeName())
                }
                BluetoothProfile.STATE_CONNECTING -> _status.value = HidStatus(HidConnectionState.CONNECTING)
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (connectedDevice?.address == device.address) connectedDevice = null
                    _status.value = HidStatus(HidConnectionState.DISCONNECTED)
                    maybeAutoReconnect(device)
                }
            }
        }

        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            if (!registered) _status.value = HidStatus(HidConnectionState.DISCONNECTED)
        }
    }

    @SuppressLint("MissingPermission")
    fun startRegistration() {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _status.value = HidStatus(HidConnectionState.DISABLED)
            return
        }
        if (!hasBluetoothPermission()) return
        adapter.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE)
    }

    @SuppressLint("MissingPermission")
    private fun registerApp() {
        val device = hidDevice ?: return
        if (!hasBluetoothPermission()) return
        val sdpSettings = BluetoothHidDeviceAppSdpSettings(
            "BarcodeBridge",
            "Barcode scanner keyboard wedge",
            "BarcodeBridge",
            BluetoothHidDevice.SUBCLASS1_KEYBOARD,
            KEYBOARD_DESCRIPTOR,
        )
        val qos = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_GUARANTEED,
            800, 9, 0, 11250, 0,
        )
        device.registerApp(sdpSettings, null, qos, ContextCompat.getMainExecutor(context), hidCallback)
    }

    @SuppressLint("MissingPermission")
    private fun maybeAutoReconnect(device: BluetoothDevice) {
        val hid = hidDevice ?: return
        if (!hasBluetoothPermission()) return
        scope.launch {
            val autoReconnect = settingsRepository.settings.first().hid.autoReconnect
            if (autoReconnect) {
                _status.value = HidStatus(HidConnectionState.CONNECTING)
                runCatching { hid.connect(device) }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun send(text: String): TransportResult {
        val device = connectedDevice ?: return TransportResult.Failure(message = "HID not connected")
        val hid = hidDevice ?: return TransportResult.Failure(message = "HID not connected")
        if (!hasBluetoothPermission()) return TransportResult.Failure(message = "Bluetooth permission missing")

        val settings = settingsRepository.settings.first().hid
        val loadResult = keymapLoader.load(settings.layout)
        val encoded = keymapEngine.encode(text, loadResult.layoutMap, settings)

        return when (encoded) {
            is HidEncodeResult.Unmappable -> TransportResult.Failure(
                message = "Unmappable characters for layout ${settings.layout}: ${encoded.characters.joinToString()}",
                unmappableChars = encoded.characters,
                layoutLabel = settings.layout.name,
            )
            is HidEncodeResult.Success -> sendReports(device, hid, encoded.reports, settings.typingDelayMs)
        }
    }

    /**
     * Sends [text] regardless of the unmappable-character setting (calibration
     * always uses SKIP so the wizard never silently aborts) and returns the
     * exact reports transmitted, so [HidCalibration] can decode them per layout.
     */
    @SuppressLint("MissingPermission")
    suspend fun sendCalibrationString(
        text: String,
        layout: com.barcodebridge.app.data.settings.HidKeyboardLayout,
    ): Pair<TransportResult, List<HidReport>> {
        val device = connectedDevice ?: return TransportResult.Failure(message = "HID not connected") to emptyList()
        val hid = hidDevice ?: return TransportResult.Failure(message = "HID not connected") to emptyList()
        if (!hasBluetoothPermission()) return TransportResult.Failure(message = "Bluetooth permission missing") to emptyList()

        val settings = settingsRepository.settings.first().hid.copy(
            layout = layout,
            unmappableAction = com.barcodebridge.app.data.settings.UnmappableCharAction.SKIP,
        )
        val loadResult = keymapLoader.load(layout)
        val encoded = keymapEngine.encode(text, loadResult.layoutMap, settings)
        val reports = (encoded as? HidEncodeResult.Success)?.reports ?: emptyList()
        val result = sendReports(device, hid, reports, settings.typingDelayMs)
        return result to reports
    }

    private suspend fun sendReports(
        device: BluetoothDevice,
        hid: BluetoothHidDevice,
        reports: List<HidReport>,
        typingDelayMs: Int,
    ): TransportResult = withContext(Dispatchers.IO) {
        // Typing a long barcode is dozens of binder calls spaced by the
        // configured delay - never do that on the caller's main dispatcher.
        for (report in reports) {
            val sent = hid.sendReport(device, REPORT_ID_KEYBOARD, report.toBytes())
            if (!sent) return@withContext TransportResult.Failure(message = "sendReport failed")
            delay(typingDelayMs.toLong())
        }
        TransportResult.Success
    }

    private fun hasBluetoothPermission(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.safeName(): String? = runCatching { name }.getOrNull() ?: address

    companion object {
        private const val REPORT_ID_KEYBOARD = 1

        /** Standard USB HID boot-keyboard report descriptor (8-byte report: modifier, reserved, 6 keycodes). */
        private val KEYBOARD_DESCRIPTOR = byteArrayOf(
            0x05, 0x01, 0x09, 0x06, 0xA1.toByte(), 0x01,
            0x85.toByte(), REPORT_ID_KEYBOARD.toByte(),
            0x05, 0x07, 0x19, 0xE0.toByte(), 0x29, 0xE7.toByte(),
            0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95.toByte(), 0x08,
            0x81.toByte(), 0x02,
            0x95.toByte(), 0x01, 0x75, 0x08, 0x81.toByte(), 0x01,
            0x95.toByte(), 0x05, 0x75, 0x01, 0x05, 0x08, 0x19, 0x01, 0x29, 0x05,
            0x91.toByte(), 0x02,
            0x95.toByte(), 0x01, 0x75, 0x03, 0x91.toByte(), 0x01,
            0x95.toByte(), 0x06, 0x75, 0x08, 0x15, 0x00, 0x25, 0x65,
            0x05, 0x07, 0x19, 0x00, 0x29, 0x65,
            0x81.toByte(), 0x00,
            0xC0.toByte(),
        )
    }
}
