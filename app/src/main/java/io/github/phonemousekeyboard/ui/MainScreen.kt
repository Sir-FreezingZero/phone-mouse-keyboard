package io.github.phonemousekeyboard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.phonemousekeyboard.MainViewModel
import io.github.phonemousekeyboard.PermissionStatus
import io.github.phonemousekeyboard.bluetooth.ConnectionState
import io.github.phonemousekeyboard.ui.bluetooth.BluetoothActions
import io.github.phonemousekeyboard.ui.bluetooth.BluetoothScreen
import io.github.phonemousekeyboard.ui.keyboard.KeyboardScreen
import io.github.phonemousekeyboard.ui.keyboard.KeyboardSettingsSheet
import io.github.phonemousekeyboard.ui.mouse.MouseScreen
import io.github.phonemousekeyboard.ui.mouse.MouseSettingsSheet
import kotlinx.coroutines.launch

/** Actions that need the Activity, because they open system dialogs or other apps. */
data class SystemActions(
    val requestPermissions: () -> Unit,
    val turnOnBluetooth: () -> Unit,
    val makeDiscoverable: () -> Unit,
    val openBluetoothSettings: () -> Unit,
)

/**
 * Top-level layout: the header on top (swipe its title to switch between Mouse and Keyboard),
 * the current page below, and a menu that slides in from the left.
 */
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    permissions: PermissionStatus,
    systemActions: SystemActions,
) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val isSendingInput by viewModel.isSendingInput.collectAsStateWithLifecycle()
    val bluetoothState by viewModel.bluetoothUiState.collectAsStateWithLifecycle()
    val mouseSettings by viewModel.mouseSettings.collectAsStateWithLifecycle()
    val keyboardSettings by viewModel.keyboardSettings.collectAsStateWithLifecycle()
    val pendingKeystrokes by viewModel.pendingKeystrokes.collectAsStateWithLifecycle()

    var currentScreen by rememberSaveable { mutableStateOf(Screen.MOUSE) }
    // Where the back button returns to from a menu page.
    var lastMainPage by rememberSaveable { mutableStateOf(Screen.MOUSE) }

    var isMouseSettingsOpen by rememberSaveable { mutableStateOf(false) }
    var isKeyboardSettingsOpen by rememberSaveable { mutableStateOf(false) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val navigateTo: (Screen) -> Unit = { screen ->
        currentScreen = screen
        if (screen.isMainPage) lastMainPage = screen
    }

    BackHandler(enabled = !currentScreen.isMainPage) { currentScreen = lastMainPage }

    val bluetoothActions = remember(viewModel, systemActions) {
        BluetoothActions(
            onGrantPermissions = systemActions.requestPermissions,
            onTurnOnBluetooth = systemActions.turnOnBluetooth,
            onRetryService = viewModel::retryBluetoothService,
            onSwitchMode = viewModel::switchMode,
            onMakeDiscoverable = systemActions.makeDiscoverable,
            onOpenSystemSettings = systemActions.openBluetoothSettings,
            onStartScan = viewModel::startScan,
            onStopScan = viewModel::stopScan,
            onPair = viewModel::pair,
            onConnect = viewModel::connect,
            onDisconnect = viewModel::disconnect,
        )
    }

    AppDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppMenuContent(
                currentScreen = currentScreen,
                onNavigate = { screen ->
                    navigateTo(screen)
                    scope.launch { drawerState.close() }
                },
            )
        },
    ) {
        Scaffold(
            topBar = {
                AppHeader(
                    currentScreen = currentScreen,
                    isConnected = connectionState is ConnectionState.Connected,
                    isSendingInput = isSendingInput,
                    onScreenSelected = navigateTo,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    overflowItems = when (currentScreen) {
                        Screen.MOUSE -> settingsOverflowItems(
                            settingsLabel = "Mouse settings",
                            onOpenSettings = { isMouseSettingsOpen = true },
                            onReset = viewModel::resetMouseSettings,
                        )
                        Screen.KEYBOARD -> settingsOverflowItems(
                            settingsLabel = "Keyboard settings",
                            onOpenSettings = { isKeyboardSettingsOpen = true },
                            onReset = viewModel::resetKeyboardSettings,
                        )
                        Screen.BLUETOOTH -> null
                    },
                )
            },
        ) { innerPadding ->
            val contentModifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)

            when (currentScreen) {
                Screen.MOUSE -> MouseScreen(
                    settings = mouseSettings,
                    onMove = viewModel::moveMouse,
                    onScroll = viewModel::scroll,
                    onClick = viewModel::clickMouseButton,
                    onButtonPress = viewModel::pressMouseButton,
                    onButtonRelease = viewModel::releaseMouseButton,
                    modifier = contentModifier,
                )

                Screen.KEYBOARD -> KeyboardScreen(
                    settings = keyboardSettings,
                    pendingCharacters = pendingKeystrokes,
                    onTextChange = viewModel::sendTextChange,
                    onStopSending = viewModel::stopTyping,
                    // Keep the text box above the on-screen keyboard.
                    modifier = contentModifier.imePadding(),
                )

                Screen.BLUETOOTH -> BluetoothScreen(
                    state = bluetoothState,
                    permissions = permissions,
                    actions = bluetoothActions,
                    modifier = contentModifier,
                )
            }
        }
    }

    if (isMouseSettingsOpen) {
        MouseSettingsSheet(
            settings = mouseSettings,
            onChange = viewModel::updateMouseSettings,
            onReset = viewModel::resetMouseSettings,
            onDismiss = { isMouseSettingsOpen = false },
        )
    }

    if (isKeyboardSettingsOpen) {
        KeyboardSettingsSheet(
            settings = keyboardSettings,
            onChange = viewModel::updateKeyboardSettings,
            onReset = viewModel::resetKeyboardSettings,
            onDismiss = { isKeyboardSettingsOpen = false },
        )
    }
}

/** A tab's ⋮ menu: open its settings, or put them back to the defaults. */
private fun settingsOverflowItems(
    settingsLabel: String,
    onOpenSettings: () -> Unit,
    onReset: () -> Unit,
): OverflowItems = { closeMenu ->
    DropdownMenuItem(
        text = { Text(settingsLabel) },
        onClick = {
            closeMenu()
            onOpenSettings()
        },
    )
    DropdownMenuItem(
        text = { Text("Reset to defaults") },
        onClick = {
            closeMenu()
            onReset()
        },
    )
}
