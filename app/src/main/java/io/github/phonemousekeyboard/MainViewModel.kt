package io.github.phonemousekeyboard

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.phonemousekeyboard.bluetooth.BluetoothDeviceManager
import io.github.phonemousekeyboard.bluetooth.BluetoothDevicesState
import io.github.phonemousekeyboard.bluetooth.BluetoothHidController
import io.github.phonemousekeyboard.bluetooth.ConnectionState
import io.github.phonemousekeyboard.bluetooth.displayName
import io.github.phonemousekeyboard.input.MouseButton
import io.github.phonemousekeyboard.input.SpecialKeys
import io.github.phonemousekeyboard.input.TextDiff
import io.github.phonemousekeyboard.input.UsKeyboardLayout
import io.github.phonemousekeyboard.ui.bluetooth.BluetoothUiState
import io.github.phonemousekeyboard.ui.bluetooth.DeviceItem
import io.github.phonemousekeyboard.ui.bluetooth.DeviceStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Owns the Bluetooth objects so they survive screen rotation, and translates UI events
 * (finger movement, text edits, button taps) into Bluetooth actions.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val controller = BluetoothHidController(application)
    private val deviceManager = BluetoothDeviceManager(application)

    private var hasBluetoothPermission = false

    val connectionState: StateFlow<ConnectionState> = controller.state

    val bluetoothUiState: StateFlow<BluetoothUiState> =
        combine(deviceManager.state, controller.state, ::buildBluetoothUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BluetoothUiState())

    init {
        deviceManager.onPaired = ::connectAfterPairing

        // Follow the Bluetooth radio being switched on and off, e.g. from quick settings.
        viewModelScope.launch {
            deviceManager.state
                .map { it.isBluetoothEnabled }
                .distinctUntilChanged()
                .collect { isEnabled ->
                    if (!hasBluetoothPermission) return@collect
                    if (isEnabled) controller.start() else controller.onBluetoothTurnedOff()
                }
        }
    }

    /** Call whenever the Bluetooth permission is confirmed as granted. Safe to repeat. */
    fun onBluetoothPermissionGranted() {
        if (hasBluetoothPermission) return
        hasBluetoothPermission = true
        deviceManager.start()
        controller.start()
    }

    // ---- Bluetooth page ----

    fun retryBluetoothService() = controller.restart()

    fun startScan() = deviceManager.startScan()

    fun stopScan() = deviceManager.stopScan()

    fun pair(address: String) {
        deviceManager.findDevice(address)?.let(deviceManager::pair)
    }

    fun connect(address: String) {
        deviceManager.findDevice(address)?.let(controller::connect)
    }

    fun disconnect() = controller.disconnect()

    /**
     * After pairing, Windows and most other hosts connect to the keyboard/mouse on their own.
     * Give them a moment to do so, and connect from this side only if they haven't.
     */
    private fun connectAfterPairing(device: BluetoothDevice) {
        viewModelScope.launch {
            delay(HOST_CONNECT_GRACE_MILLIS)
            val connection = controller.state.value
            val isLinked = connection is ConnectionState.Connected ||
                connection is ConnectionState.Connecting
            if (!isLinked) controller.connect(device)
        }
    }

    // ---- Mouse ----

    fun moveMouse(dx: Int, dy: Int) = controller.moveMouse(dx, dy)

    fun scroll(notches: Int) = controller.moveMouse(dx = 0, dy = 0, wheel = notches)

    fun pressMouseButton(button: MouseButton) = controller.pressMouseButton(button)

    fun releaseMouseButton(button: MouseButton) = controller.releaseMouseButton(button)

    fun clickMouseButton(button: MouseButton) = controller.clickMouseButton(button)

    // ---- Keyboard ----

    /** Sends whatever key presses are needed to turn [oldText] into [newText] on the host. */
    fun sendTextChange(oldText: String, newText: String) {
        val diff = TextDiff.between(oldText, newText)

        repeat(diff.deletions) { controller.typeKey(SpecialKeys.BACKSPACE) }

        for (char in diff.insertion) {
            // Characters with no key on a US layout (e.g. emoji) are skipped.
            UsKeyboardLayout.keyStrokeFor(char)?.let(controller::typeKey)
        }
    }

    override fun onCleared() {
        deviceManager.close()
        controller.stop()
    }

    /** Merges the radio/device state and the connection state into what the Bluetooth page shows. */
    private fun buildBluetoothUiState(
        devices: BluetoothDevicesState,
        connection: ConnectionState,
    ): BluetoothUiState {
        val connectedAddress = (connection as? ConnectionState.Connected)?.deviceAddress
        val connectingAddress = (connection as? ConnectionState.Connecting)?.deviceAddress

        return BluetoothUiState(
            isBluetoothEnabled = devices.isBluetoothEnabled,
            connectionState = connection,
            problem = devices.pairingProblem
                ?: (connection as? ConnectionState.Disconnected)?.problem,
            isScanning = devices.isScanning,
            pairedDevices = devices.pairedDevices.map { device ->
                DeviceItem(
                    address = device.address,
                    name = device.displayName(),
                    status = when (device.address) {
                        connectedAddress -> DeviceStatus.CONNECTED
                        connectingAddress -> DeviceStatus.CONNECTING
                        else -> DeviceStatus.PAIRED
                    },
                )
            },
            nearbyDevices = devices.nearbyDevices.map { device ->
                DeviceItem(
                    address = device.address,
                    name = device.displayName(),
                    status = if (device.address == devices.pairingAddress) {
                        DeviceStatus.PAIRING
                    } else {
                        DeviceStatus.NOT_PAIRED
                    },
                )
            },
        )
    }

    private companion object {
        const val HOST_CONNECT_GRACE_MILLIS = 2_000L
    }
}
