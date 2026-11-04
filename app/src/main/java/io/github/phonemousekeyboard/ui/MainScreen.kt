package io.github.phonemousekeyboard.ui

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.phonemousekeyboard.MainViewModel
import io.github.phonemousekeyboard.bluetooth.ConnectionState
import io.github.phonemousekeyboard.ui.connection.ConnectionBar
import io.github.phonemousekeyboard.ui.connection.DevicePickerDialog
import io.github.phonemousekeyboard.ui.keyboard.KeyboardScreen
import io.github.phonemousekeyboard.ui.mouse.MouseScreen

/**
 * Top-level layout: connection status on top, the active tool in the middle,
 * Mouse / Keyboard tabs at the bottom.
 *
 * @param permissionMissing true when the user has declined the Bluetooth permission.
 * @param onRequestPermission asks for the Bluetooth permission again.
 * @param onMakeDiscoverable asks Android to make the phone visible to nearby computers.
 */
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    permissionMissing: Boolean,
    onRequestPermission: () -> Unit,
    onMakeDiscoverable: () -> Unit,
) {
    val bluetoothState by viewModel.connectionState.collectAsStateWithLifecycle()
    val connectionState = if (permissionMissing) {
        ConnectionState.Unavailable("Bluetooth permission is required.")
    } else {
        bluetoothState
    }

    var selectedTab by rememberSaveable { mutableStateOf(AppTab.MOUSE) }
    var showDevicePicker by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            ConnectionBar(
                state = connectionState,
                onConnectClick = { showDevicePicker = true },
                onDisconnectClick = viewModel::disconnect,
                onRetryClick = if (permissionMissing) onRequestPermission else viewModel::startBluetooth,
                modifier = Modifier.statusBarsPadding(),
            )
        },
        bottomBar = {
            BottomTabBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
            )
        },
    ) { innerPadding ->
        val contentModifier = Modifier
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)

        when (selectedTab) {
            AppTab.MOUSE -> MouseScreen(
                onMove = viewModel::moveMouse,
                onScroll = viewModel::scroll,
                onClick = viewModel::clickMouseButton,
                onButtonPress = viewModel::pressMouseButton,
                onButtonRelease = viewModel::releaseMouseButton,
                modifier = contentModifier,
            )

            AppTab.KEYBOARD -> KeyboardScreen(
                onTextChange = viewModel::sendTextChange,
                // Keep the text box above the on-screen keyboard.
                modifier = contentModifier.imePadding(),
            )
        }
    }

    if (showDevicePicker) {
        DevicePickerDialog(
            pairedDevices = viewModel.pairedDevices(),
            onDeviceSelected = { device ->
                showDevicePicker = false
                viewModel.connect(device)
            },
            onMakeDiscoverable = {
                showDevicePicker = false
                onMakeDiscoverable()
            },
            onDismiss = { showDevicePicker = false },
        )
    }
}
