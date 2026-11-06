package io.github.phonemousekeyboard.ui.bluetooth

import io.github.phonemousekeyboard.bluetooth.ConnectionState

/** Everything shown on the Bluetooth page. Devices are identified by their address. */
data class BluetoothUiState(
    val isBluetoothEnabled: Boolean = false,
    val connectionState: ConnectionState = ConnectionState.Starting("Waiting…"),
    /** The most recent pairing or connection failure, explained for the user. */
    val problem: String? = null,
    val pairedDevices: List<DeviceItem> = emptyList(),
)

/** One row in a device list. */
data class DeviceItem(
    val address: String,
    val name: String,
    val status: DeviceStatus,
)

enum class DeviceStatus {
    PAIRED,
    CONNECTING,
    CONNECTED,
}

/**
 * Everything the user can do from the Bluetooth page. Grouped into one object so the screen
 * doesn't need a long list of separate callback parameters.
 */
data class BluetoothActions(
    val onGrantPermissions: () -> Unit,
    val onTurnOnBluetooth: () -> Unit,
    val onRetryService: () -> Unit,
    val onOpenSystemSettings: () -> Unit,
    val onConnect: (address: String) -> Unit,
    val onDisconnect: () -> Unit,
)
