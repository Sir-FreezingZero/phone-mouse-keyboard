package io.github.phonemousekeyboard.ui.bluetooth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.phonemousekeyboard.PermissionStatus
import io.github.phonemousekeyboard.bluetooth.ConnectionState
import io.github.phonemousekeyboard.ui.components.GroupDivider
import io.github.phonemousekeyboard.ui.components.GroupNote
import io.github.phonemousekeyboard.ui.components.NumberedSteps
import io.github.phonemousekeyboard.ui.components.SettingRow
import io.github.phonemousekeyboard.ui.components.SettingsGroup
import io.github.phonemousekeyboard.ui.theme.ConnectedGreen

/**
 * Everything needed to get the phone talking to a computer, top to bottom in the order a new
 * user would need it:
 *
 *  1. Status       — problems, permissions, Bluetooth on/off, keyboard & mouse service, connection
 *  2. Pairing      — step-by-step instructions (computers pair from their own settings)
 *  3. Paired       — computers already paired; connect or disconnect
 *  4. System       — shortcut to Android's own Bluetooth settings
 */
@Composable
fun BluetoothScreen(
    state: BluetoothUiState,
    permissions: PermissionStatus,
    actions: BluetoothActions,
    modifier: Modifier = Modifier,
) {
    // Connecting is only offered once the keyboard/mouse service is running. A computer that pairs before then only ever sees a phone, not an input device.
    val isReady = permissions.canUseBluetooth &&
        state.isBluetoothEnabled &&
        state.connectionState.isServiceRunning()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { StatusGroup(state, permissions, actions) }
        item { PairingGuideGroup() }
        item { PairedDevicesGroup(state, isReady, actions) }
        item { SystemGroup(actions) }
    }
}

// ----------------------------------------------------------------------------------------------
// Groups
// ----------------------------------------------------------------------------------------------

@Composable
private fun StatusGroup(
    state: BluetoothUiState,
    permissions: PermissionStatus,
    actions: BluetoothActions,
) {
    val connection = state.connectionState

    SettingsGroup(title = "Status") {
        state.problem?.let { problem ->
            GroupNote(text = problem, color = MaterialTheme.colorScheme.error)
            GroupDivider()
        }

        if (!permissions.canUseBluetooth) {
            SettingRow(
                title = "Permission",
                detail = "Nearby devices access",
                actionLabel = "Grant",
                onAction = actions.onGrantPermissions,
            )
            GroupDivider()
        }

        SettingRow(
            title = "Bluetooth",
            detail = if (state.isBluetoothEnabled) "On" else "Off",
            actionLabel = if (state.isBluetoothEnabled) null else "Turn on",
            onAction = actions.onTurnOnBluetooth,
            isActionEnabled = permissions.canUseBluetooth,
        )
        GroupDivider()

        SettingRow(
            title = "Keyboard & mouse service",
            detail = connection.serviceDescription(),
            detailColor = connection.serviceColor(),
            actionLabel = if (connection is ConnectionState.Unavailable) "Retry" else null,
            onAction = actions.onRetryService,
            isActionEnabled = permissions.canUseBluetooth && state.isBluetoothEnabled,
        )
        GroupDivider()

        val isLinked = connection is ConnectionState.Connected ||
            connection is ConnectionState.Connecting
        SettingRow(
            title = "Connection",
            detail = connection.connectionDescription(),
            detailColor = connection.connectionColor(),
            actionLabel = if (isLinked) "Disconnect" else null,
            onAction = actions.onDisconnect,
        )
    }
}

/**
 * Pairing is always started from the computer, which reads this phone's keyboard/mouse
 * details as part of pairing. While nothing is connected the phone advertises itself, so
 * there is no "make discoverable" step.
 */
@Composable
private fun PairingGuideGroup() {
    SettingsGroup(title = "Pair with a computer") {
        NumberedSteps(
            listOf(
                "Wait until Keyboard & mouse service above says Ready.",
                "Keep this app open. While nothing is connected, computers nearby can see " +
                    "this phone as a keyboard and mouse.",
                "If this phone was paired with the computer before, remove it on the " +
                    "computer and forget the computer in Android's Bluetooth settings.",
                "On Windows: Settings › Bluetooth & devices › Add device › Bluetooth, " +
                    "then choose this phone.",
                "Accept the pairing prompts on the phone and the computer. There may be " +
                    "two on the phone. The computer connects by itself.",
            ),
        )
    }
}

@Composable
private fun PairedDevicesGroup(
    state: BluetoothUiState,
    isReady: Boolean,
    actions: BluetoothActions,
) {
    SettingsGroup(title = "Paired devices") {
        if (state.pairedDevices.isEmpty()) {
            GroupNote("No paired devices yet.")
        }

        state.pairedDevices.forEachIndexed { index, device ->
            if (index > 0) GroupDivider()

            val isLinked = device.status == DeviceStatus.CONNECTED ||
                device.status == DeviceStatus.CONNECTING
            SettingRow(
                title = device.name,
                detail = device.status.label(),
                detailColor = device.status.color(),
                actionLabel = if (isLinked) "Disconnect" else "Connect",
                onAction = if (isLinked) actions.onDisconnect else ({ actions.onConnect(device.address) }),
                isActionEnabled = isReady,
            )
        }
    }
}

@Composable
private fun SystemGroup(actions: BluetoothActions) {
    SettingsGroup(title = "System") {
        SettingRow(
            title = "Android Bluetooth settings",
            detail = "Rename this phone, forget devices",
            actionLabel = "Open",
            onAction = actions.onOpenSystemSettings,
        )
    }
}

// ----------------------------------------------------------------------------------------------
// Text and colours
// ----------------------------------------------------------------------------------------------

private fun ConnectionState.isServiceRunning(): Boolean =
    this is ConnectionState.Disconnected ||
        this is ConnectionState.Connecting ||
        this is ConnectionState.Connected

@Composable
private fun ConnectionState.serviceColor() = when (this) {
    is ConnectionState.Unavailable -> MaterialTheme.colorScheme.error
    is ConnectionState.Starting -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> ConnectedGreen
}

private fun ConnectionState.serviceDescription(): String = when (this) {
    is ConnectionState.Unavailable -> reason
    is ConnectionState.Starting -> step
    else -> "Ready"
}

private fun ConnectionState.connectionDescription(): String = when (this) {
    is ConnectionState.Connected -> "Connected to $deviceName"
    is ConnectionState.Connecting -> "Connecting to $deviceName…"
    else -> "Not connected"
}

@Composable
private fun ConnectionState.connectionColor() =
    if (this is ConnectionState.Connected) ConnectedGreen else MaterialTheme.colorScheme.onSurfaceVariant

private fun DeviceStatus.label(): String = when (this) {
    DeviceStatus.PAIRED -> "Paired"
    DeviceStatus.CONNECTING -> "Connecting…"
    DeviceStatus.CONNECTED -> "Connected"
}

@Composable
private fun DeviceStatus.color() =
    if (this == DeviceStatus.CONNECTED) ConnectedGreen else MaterialTheme.colorScheme.onSurfaceVariant
