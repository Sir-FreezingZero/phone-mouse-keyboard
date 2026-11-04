package io.github.phonemousekeyboard.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import io.github.phonemousekeyboard.input.KeyStroke
import io.github.phonemousekeyboard.input.MouseButton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

/**
 * Turns the phone into a Bluetooth keyboard + mouse using Android's [BluetoothHidDevice] API.
 *
 * The host computer sees a normal Bluetooth input device, so no companion software is needed
 * on the computer being controlled.
 *
 * All methods that touch Bluetooth require the BLUETOOTH_CONNECT permission on Android 12+.
 * The caller is responsible for requesting it before calling [start].
 */
@SuppressLint("MissingPermission")
class BluetoothHidController(context: Context) {

    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? =
        appContext.getSystemService(BluetoothManager::class.java)?.adapter

    private val callbackExecutor = Executors.newSingleThreadExecutor()

    private var hidDevice: BluetoothHidDevice? = null
    private var connectedHost: BluetoothDevice? = null

    /** Bit field of mouse buttons currently held down. Included in every mouse report. */
    private var pressedButtons = 0

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Starting)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    // ------------------------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------------------------

    /** Registers the phone as a HID device. Safe to call more than once. */
    fun start() {
        val adapter = adapter
        when {
            adapter == null -> {
                _state.value = ConnectionState.Unavailable("This phone has no Bluetooth.")
                return
            }
            !adapter.isEnabled -> {
                _state.value = ConnectionState.Unavailable("Bluetooth is turned off.")
                return
            }
            hidDevice != null -> return
        }

        _state.value = ConnectionState.Starting
        val requested = adapter.getProfileProxy(
            appContext,
            profileListener,
            BluetoothProfile.HID_DEVICE,
        )
        if (!requested) {
            _state.value = ConnectionState.Unavailable("This phone does not support Bluetooth HID.")
        }
    }

    /** Disconnects and unregisters. Call when the app is finished with Bluetooth. */
    fun stop() {
        hidDevice?.let { device ->
            connectedHost?.let(device::disconnect)
            device.unregisterApp()
            adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, device)
        }
        hidDevice = null
        connectedHost = null
        callbackExecutor.shutdown()
    }

    // ------------------------------------------------------------------------------------------
    // Connecting to a host
    // ------------------------------------------------------------------------------------------

    /** Computers already paired with this phone, sorted by name. */
    fun pairedDevices(): List<BluetoothDevice> =
        adapter?.bondedDevices
            .orEmpty()
            .sortedBy { it.name.orEmpty().lowercase() }

    fun connect(host: BluetoothDevice) {
        val device = hidDevice ?: return
        _state.value = ConnectionState.Connecting(host.displayName())
        if (!device.connect(host)) {
            _state.value = ConnectionState.Disconnected
        }
    }

    fun disconnect() {
        val device = hidDevice ?: return
        connectedHost?.let(device::disconnect)
    }

    // ------------------------------------------------------------------------------------------
    // Mouse
    // ------------------------------------------------------------------------------------------

    /**
     * Moves the pointer by ([dx], [dy]) pixels-ish and scrolls by [wheel] notches.
     * Large values are split into several reports because each report is limited to ±127.
     */
    fun moveMouse(dx: Int, dy: Int, wheel: Int = 0) {
        var remainingX = dx
        var remainingY = dy
        var remainingWheel = wheel

        while (remainingX != 0 || remainingY != 0 || remainingWheel != 0) {
            val stepX = remainingX.coerceIn(-MAX_REPORT_DELTA, MAX_REPORT_DELTA)
            val stepY = remainingY.coerceIn(-MAX_REPORT_DELTA, MAX_REPORT_DELTA)
            val stepWheel = remainingWheel.coerceIn(-MAX_REPORT_DELTA, MAX_REPORT_DELTA)

            sendMouseReport(stepX, stepY, stepWheel)

            remainingX -= stepX
            remainingY -= stepY
            remainingWheel -= stepWheel
        }
    }

    fun pressMouseButton(button: MouseButton) {
        pressedButtons = pressedButtons or button.bit
        sendMouseReport(dx = 0, dy = 0, wheel = 0)
    }

    fun releaseMouseButton(button: MouseButton) {
        pressedButtons = pressedButtons and button.bit.inv()
        sendMouseReport(dx = 0, dy = 0, wheel = 0)
    }

    fun clickMouseButton(button: MouseButton) {
        pressMouseButton(button)
        releaseMouseButton(button)
    }

    private fun sendMouseReport(dx: Int, dy: Int, wheel: Int) {
        val report = ByteArray(HidDescriptor.MOUSE_REPORT_SIZE)
        report[0] = pressedButtons.toByte()
        report[1] = dx.toByte()
        report[2] = dy.toByte()
        report[3] = wheel.toByte()
        sendReport(HidDescriptor.MOUSE_REPORT_ID, report)
    }

    // ------------------------------------------------------------------------------------------
    // Keyboard
    // ------------------------------------------------------------------------------------------

    /** Presses and releases a single key, holding any modifiers in [keyStroke] while doing so. */
    fun typeKey(keyStroke: KeyStroke) {
        val press = ByteArray(HidDescriptor.KEYBOARD_REPORT_SIZE)
        press[0] = keyStroke.modifiers.toByte()
        press[2] = keyStroke.keyCode.toByte()
        sendReport(HidDescriptor.KEYBOARD_REPORT_ID, press)

        // An all-zero report means "no keys held".
        sendReport(HidDescriptor.KEYBOARD_REPORT_ID, ByteArray(HidDescriptor.KEYBOARD_REPORT_SIZE))
    }

    // ------------------------------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------------------------------

    private fun sendReport(reportId: Int, data: ByteArray) {
        val device = hidDevice ?: return
        val host = connectedHost ?: return
        device.sendReport(host, reportId, data)
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            val device = proxy as BluetoothHidDevice
            hidDevice = device
            device.registerApp(SDP_SETTINGS, null, null, callbackExecutor, hidCallback)
        }

        override fun onServiceDisconnected(profile: Int) {
            hidDevice = null
            connectedHost = null
            _state.value = ConnectionState.Unavailable("Bluetooth HID service stopped.")
        }
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.d(TAG, "App registered: $registered")
            _state.value = if (registered) {
                ConnectionState.Disconnected
            } else {
                ConnectionState.Unavailable("Could not register as a keyboard and mouse.")
            }
            // Android may hand us the last host so we can reconnect automatically.
            if (registered && pluggedDevice != null) {
                connect(pluggedDevice)
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            Log.d(TAG, "Connection state for ${device.displayName()}: $state")
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedHost = device
                    _state.value = ConnectionState.Connected(device.displayName())
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    _state.value = ConnectionState.Connecting(device.displayName())
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (connectedHost == device) connectedHost = null
                    pressedButtons = 0
                    _state.value = ConnectionState.Disconnected
                }
            }
        }
    }

    companion object {
        private const val TAG = "BluetoothHidController"
        private const val MAX_REPORT_DELTA = 127

        private val SDP_SETTINGS = BluetoothHidDeviceAppSdpSettings(
            "Phone Mouse & Keyboard",
            "Phone used as a Bluetooth mouse and keyboard",
            "Phone Mouse & Keyboard",
            BluetoothHidDevice.SUBCLASS1_COMBO,
            HidDescriptor.REPORT_MAP,
        )
    }
}

/** A device's friendly name, falling back to its hardware address. */
@SuppressLint("MissingPermission")
fun BluetoothDevice.displayName(): String = name?.takeIf { it.isNotBlank() } ?: address
