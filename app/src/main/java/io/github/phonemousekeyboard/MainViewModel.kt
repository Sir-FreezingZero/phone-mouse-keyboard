package io.github.phonemousekeyboard

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.phonemousekeyboard.bluetooth.BluetoothDeviceManager
import io.github.phonemousekeyboard.bluetooth.BluetoothDevicesState
import io.github.phonemousekeyboard.bluetooth.ConnectionState
import io.github.phonemousekeyboard.bluetooth.HidConnection
import io.github.phonemousekeyboard.bluetooth.HidMode
import io.github.phonemousekeyboard.bluetooth.displayName
import io.github.phonemousekeyboard.input.HidInput
import io.github.phonemousekeyboard.input.MouseButton
import io.github.phonemousekeyboard.input.SpecialKeys
import io.github.phonemousekeyboard.input.TextDiff
import io.github.phonemousekeyboard.input.TypingQueue
import io.github.phonemousekeyboard.input.UsKeyboardLayout
import io.github.phonemousekeyboard.ui.bluetooth.BluetoothUiState
import io.github.phonemousekeyboard.ui.bluetooth.DeviceItem
import io.github.phonemousekeyboard.ui.bluetooth.DeviceStatus
import io.github.phonemousekeyboard.ui.keyboard.KeyboardSettings
import io.github.phonemousekeyboard.ui.keyboard.KeyboardSettingsStore
import io.github.phonemousekeyboard.ui.mouse.MouseSettings
import io.github.phonemousekeyboard.ui.mouse.MouseSettingsStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Owns the Bluetooth objects so they survive screen rotation, and translates UI events
 * (finger movement, text edits, button taps) into Bluetooth actions.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val connection = HidConnection(application, viewModelScope)
    private val input = HidInput(connection)
    private val deviceManager = BluetoothDeviceManager(application)
    private val mouseSettingsStore = MouseSettingsStore(application)
    private val keyboardSettingsStore = KeyboardSettingsStore(application)

    private val typingQueue = TypingQueue(
        scope = viewModelScope,
        type = { key -> input.typeKey(key) },
        pauseAfterKeyMillis = ::pauseAfterKeyMillis,
    )

    private var hasBluetoothPermission = false

    val connectionState: StateFlow<ConnectionState> = connection.state

    val bluetoothUiState: StateFlow<BluetoothUiState> =
        combine(
            deviceManager.state,
            connection.state,
            connection.mode,
            connection.classicProblem,
            ::buildBluetoothUiState,
        ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BluetoothUiState())

    init {
        deviceManager.onPaired = ::connectAfterPairing

        // Follow the Bluetooth radio being switched on and off, e.g. from quick settings.
        viewModelScope.launch {
            deviceManager.state
                .map { it.isBluetoothEnabled }
                .distinctUntilChanged()
                .collect { isEnabled ->
                    if (!hasBluetoothPermission) return@collect
                    if (isEnabled) connection.start() else connection.onBluetoothTurnedOff()
                }
        }
    }

    /** Call whenever the Bluetooth permission is confirmed as granted. Safe to repeat. */
    fun onBluetoothPermissionGranted() {
        if (hasBluetoothPermission) return
        hasBluetoothPermission = true
        deviceManager.start()
        connection.start()
    }

    // ---- Bluetooth page ----

    fun retryBluetoothService() = connection.restart()

    fun switchMode(mode: HidMode) = connection.switchTo(mode)

    fun startScan() = deviceManager.startScan()

    fun stopScan() = deviceManager.stopScan()

    fun pair(address: String) {
        deviceManager.findDevice(address)?.let(deviceManager::pair)
    }

    fun connect(address: String) {
        deviceManager.findDevice(address)?.let(connection::connect)
    }

    fun disconnect() = connection.disconnect()

    /**
     * After pairing, Windows and most other hosts connect to the keyboard/mouse on their own.
     * Give them a moment to do so, and connect from this side only if they haven't.
     */
    private fun connectAfterPairing(device: BluetoothDevice) {
        viewModelScope.launch {
            delay(HOST_CONNECT_GRACE_MILLIS)
            val current = connection.state.value
            val isLinked = current is ConnectionState.Connected ||
                current is ConnectionState.Connecting
            if (!isLinked) connection.connect(device)
        }
    }

    // ---- Mouse ----

    val mouseSettings: StateFlow<MouseSettings> = mouseSettingsStore.settings

    fun updateMouseSettings(change: (MouseSettings) -> MouseSettings) = mouseSettingsStore.update(change)

    fun resetMouseSettings() = mouseSettingsStore.reset()

    fun moveMouse(dx: Int, dy: Int) = input.moveMouse(dx, dy)

    fun scroll(notches: Int) = input.moveMouse(dx = 0, dy = 0, wheel = notches)

    fun pressMouseButton(button: MouseButton) = input.pressMouseButton(button)

    fun releaseMouseButton(button: MouseButton) = input.releaseMouseButton(button)

    fun clickMouseButton(button: MouseButton) = input.clickMouseButton(button)

    // ---- Keyboard ----

    val keyboardSettings: StateFlow<KeyboardSettings> = keyboardSettingsStore.settings

    /** Key presses still waiting to be sent, see [KeyboardSettings.characterDelayMillis]. */
    val pendingKeystrokes: StateFlow<Int> = typingQueue.pending

    fun updateKeyboardSettings(change: (KeyboardSettings) -> KeyboardSettings) =
        keyboardSettingsStore.update(change)

    fun resetKeyboardSettings() = keyboardSettingsStore.reset()

    /** Sends whatever key presses are needed to turn [oldText] into [newText] on the host. */
    fun sendTextChange(oldText: String, newText: String) {
        val diff = TextDiff.between(oldText, newText)

        repeat(diff.deletions) { typingQueue.add(SpecialKeys.BACKSPACE) }

        for (char in diff.insertion) {
            // Characters with no key on a US layout (e.g. emoji) are skipped.
            UsKeyboardLayout.keyStrokeFor(char)?.let(typingQueue::add)
        }
    }

    /** Drops any typing that hasn't been sent yet. */
    fun stopTyping() = typingQueue.cancel()

    /** The fixed delay per character plus a fresh random amount of the extra delay. */
    private fun pauseAfterKeyMillis(): Long {
        val settings = keyboardSettings.value
        val randomExtra = if (settings.randomExtraDelayMillis > 0) {
            Random.nextInt(0, settings.randomExtraDelayMillis + 1)
        } else {
            0
        }
        return (settings.characterDelayMillis + randomExtra).toLong()
    }

    override fun onCleared() {
        deviceManager.close()
        connection.stop()
    }

    /** Merges the radio/device state and the connection state into what the Bluetooth page shows. */
    private fun buildBluetoothUiState(
        devices: BluetoothDevicesState,
        connectionState: ConnectionState,
        mode: HidMode,
        classicProblem: String?,
    ): BluetoothUiState {
        val connectedAddress = (connectionState as? ConnectionState.Connected)?.deviceAddress
        val connectingAddress = (connectionState as? ConnectionState.Connecting)?.deviceAddress

        return BluetoothUiState(
            isBluetoothEnabled = devices.isBluetoothEnabled,
            connectionState = connectionState,
            mode = mode,
            classicProblem = classicProblem,
            problem = devices.pairingProblem
                ?: (connectionState as? ConnectionState.Disconnected)?.problem,
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
