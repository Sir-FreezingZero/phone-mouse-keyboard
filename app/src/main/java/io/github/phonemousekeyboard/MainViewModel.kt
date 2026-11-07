package io.github.phonemousekeyboard

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.phonemousekeyboard.bluetooth.BluetoothDeviceManager
import io.github.phonemousekeyboard.bluetooth.BluetoothDevicesState
import io.github.phonemousekeyboard.bluetooth.ConnectionState
import io.github.phonemousekeyboard.bluetooth.HidTransport
import io.github.phonemousekeyboard.bluetooth.ScrollResolutionMemory
import io.github.phonemousekeyboard.bluetooth.ble.BleHidTransport
import io.github.phonemousekeyboard.bluetooth.displayName
import io.github.phonemousekeyboard.input.DpadDirection
import io.github.phonemousekeyboard.input.GamepadButton
import io.github.phonemousekeyboard.input.GamepadStick
import io.github.phonemousekeyboard.input.HidInput
import io.github.phonemousekeyboard.input.InputActivity
import io.github.phonemousekeyboard.input.LinkOpener
import io.github.phonemousekeyboard.input.MediaKey
import io.github.phonemousekeyboard.input.MouseButton
import io.github.phonemousekeyboard.input.SpecialKeys
import io.github.phonemousekeyboard.input.TextDiff
import io.github.phonemousekeyboard.input.TypingQueue
import io.github.phonemousekeyboard.input.UsKeyboardLayout
import io.github.phonemousekeyboard.ui.PageVisibilityStore
import io.github.phonemousekeyboard.ui.Screen
import io.github.phonemousekeyboard.ui.airmouse.AirMouseSettings
import io.github.phonemousekeyboard.ui.airmouse.AirMouseSettingsStore
import io.github.phonemousekeyboard.ui.bluetooth.BluetoothUiState
import io.github.phonemousekeyboard.ui.bluetooth.DeviceItem
import io.github.phonemousekeyboard.ui.bluetooth.DeviceStatus
import io.github.phonemousekeyboard.ui.keyboard.KeyboardSettings
import io.github.phonemousekeyboard.ui.keyboard.KeyboardSettingsStore
import io.github.phonemousekeyboard.ui.links.Link
import io.github.phonemousekeyboard.ui.links.LinksSettings
import io.github.phonemousekeyboard.ui.links.LinksSettingsStore
import io.github.phonemousekeyboard.ui.links.LinksStore
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

    private val connection: HidTransport =
        BleHidTransport(application, ScrollResolutionMemory(application))
    private val input = HidInput(connection, onInputSent = ::onInputSent)
    private val inputActivity = InputActivity(viewModelScope)
    private val deviceManager = BluetoothDeviceManager(application)
    private val mouseSettingsStore = MouseSettingsStore(application)
    private val airMouseSettingsStore = AirMouseSettingsStore(application)
    private val keyboardSettingsStore = KeyboardSettingsStore(application)
    private val linksStore = LinksStore(application)
    private val linksSettingsStore = LinksSettingsStore(application)
    private val pageVisibilityStore = PageVisibilityStore(application)

    private val typingQueue = TypingQueue(
        scope = viewModelScope,
        type = { key -> input.typeKey(key) },
        pauseAfterKeyMillis = ::pauseAfterKeyMillis,
    )

    private val linkOpener = LinkOpener(viewModelScope, typeKey = { key -> input.typeKey(key) })

    private var hasBluetoothPermission = false

    val connectionState: StateFlow<ConnectionState> = connection.state

    /** True while mouse or keyboard input is being sent to a connected computer. */
    val isSendingInput: StateFlow<Boolean> = inputActivity.isActive

    val bluetoothUiState: StateFlow<BluetoothUiState> =
        combine(deviceManager.state, connection.state, ::buildBluetoothUiState).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BluetoothUiState())

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

    // ---- Pages ----

    /** Main pages hidden from the header's swipeable title. */
    val hiddenPages: StateFlow<Set<Screen>> = pageVisibilityStore.hiddenPages

    fun setPageShown(page: Screen, isShown: Boolean) = pageVisibilityStore.setShown(page, isShown)

    // ---- Mouse ----

    val mouseSettings: StateFlow<MouseSettings> = mouseSettingsStore.settings

    fun updateMouseSettings(change: (MouseSettings) -> MouseSettings) = mouseSettingsStore.update(change)

    fun resetMouseSettings() = mouseSettingsStore.reset()

    // ---- Air mouse ----
    // Movement and clicks use the same functions as the trackpad (moveMouse, pressMouseButton…).

    val airMouseSettings: StateFlow<AirMouseSettings> = airMouseSettingsStore.settings

    fun updateAirMouseSettings(change: (AirMouseSettings) -> AirMouseSettings) =
        airMouseSettingsStore.update(change)

    fun resetAirMouseSettings() = airMouseSettingsStore.reset()

    fun moveMouse(dx: Int, dy: Int) = input.moveMouse(dx, dy)

    /** Scrolls for a two-finger drag of [fingerDeltaY] phone pixels on the trackpad. */
    fun scroll(fingerDeltaY: Float) {
        val settings = mouseSettings.value
        input.scroll(settings.scrollNotchesFor(fingerDeltaY), smooth = settings.smoothScrolling)
    }

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

    /** Sends a single Backspace, e.g. when Backspace is pressed in an empty text box. */
    fun pressBackspace() = typingQueue.add(SpecialKeys.BACKSPACE)

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

    // ---- Links ----

    val links: StateFlow<List<Link>> = linksStore.links

    val linksSettings: StateFlow<LinksSettings> = linksSettingsStore.settings

    /** The address being opened on the computer right now, or null. */
    val openingLinkAddress: StateFlow<String?> = linkOpener.openingAddress

    fun updateLinksSettings(change: (LinksSettings) -> LinksSettings) =
        linksSettingsStore.update(change)

    fun resetLinksSettings() = linksSettingsStore.reset()

    fun saveLink(link: Link) = linksStore.save(link)

    fun deleteLink(link: Link) = linksStore.delete(link)

    fun moveLink(link: Link, offset: Int) = linksStore.move(link, offset)

    /** Opens [link] in the computer's browser, as chosen in [LinksSettings]. */
    fun openLink(link: Link) {
        val settings = linksSettings.value
        linkOpener.open(
            address = link.address,
            target = settings.target,
            shortcutKey = settings.shortcutKey,
            waitMillis = settings.waitMillis.toLong(),
        )
    }

    // ---- Media ----

    fun pressMediaKey(key: MediaKey) = input.pressMediaKey(key)

    fun releaseMediaKey() = input.releaseMediaKey()

    // ---- Gamepad ----

    fun pressGamepadButton(button: GamepadButton) = input.pressGamepadButton(button)

    fun releaseGamepadButton(button: GamepadButton) = input.releaseGamepadButton(button)

    fun setGamepadDpad(direction: DpadDirection?) = input.setGamepadDpad(direction)

    fun setGamepadStick(stick: GamepadStick, x: Float, y: Float) =
        input.setGamepadStick(stick, x, y)

    fun resetGamepad() = input.resetGamepad()

    private fun onInputSent() {
        // Input only reaches the computer while connected; otherwise it isn't "sent".
        if (connection.state.value is ConnectionState.Connected) inputActivity.ping()
    }

    override fun onCleared() {
        deviceManager.close()
        connection.stop()
    }

    /** Merges the radio/device state and the connection state into what the Bluetooth page shows. */
    private fun buildBluetoothUiState(
        devices: BluetoothDevicesState,
        connectionState: ConnectionState,
    ): BluetoothUiState {
        val connectedAddress = (connectionState as? ConnectionState.Connected)?.deviceAddress
        val connectingAddress = (connectionState as? ConnectionState.Connecting)?.deviceAddress

        return BluetoothUiState(
            isBluetoothEnabled = devices.isBluetoothEnabled,
            connectionState = connectionState,
            problem = devices.pairingProblem
                ?: (connectionState as? ConnectionState.Disconnected)?.problem,
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
        )
    }

    private companion object {
        const val HOST_CONNECT_GRACE_MILLIS = 2_000L
    }
}
