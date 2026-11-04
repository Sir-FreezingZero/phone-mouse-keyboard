package io.github.phonemousekeyboard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Scaffold
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
import io.github.phonemousekeyboard.ui.mouse.MouseScreen
import kotlinx.coroutines.launch

/** Actions that need the Activity, because they open system dialogs or other apps. */
data class SystemActions(
    val requestPermissions: () -> Unit,
    val turnOnBluetooth: () -> Unit,
    val makeDiscoverable: () -> Unit,
    val openBluetoothSettings: () -> Unit,
)

/**
 * Top-level layout: header on top, the current page in the middle, Mouse / Keyboard tabs at
 * the bottom, and a menu that slides in from the left.
 */
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    permissions: PermissionStatus,
    systemActions: SystemActions,
) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val bluetoothState by viewModel.bluetoothUiState.collectAsStateWithLifecycle()

    var currentScreen by rememberSaveable { mutableStateOf(Screen.MOUSE) }
    // Where the back button returns to from a menu page.
    var lastTab by rememberSaveable { mutableStateOf(Screen.MOUSE) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val navigateTo: (Screen) -> Unit = { screen ->
        currentScreen = screen
        if (screen.isTab) lastTab = screen
    }

    BackHandler(enabled = !currentScreen.isTab) { currentScreen = lastTab }

    val bluetoothActions = remember(viewModel, systemActions) {
        BluetoothActions(
            onGrantPermissions = systemActions.requestPermissions,
            onTurnOnBluetooth = systemActions.turnOnBluetooth,
            onRetryService = viewModel::retryBluetoothService,
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
                    title = currentScreen.title,
                    isConnected = connectionState is ConnectionState.Connected,
                    onMenuClick = { scope.launch { drawerState.open() } },
                )
            },
            bottomBar = {
                BottomTabBar(
                    currentScreen = currentScreen,
                    onTabSelected = navigateTo,
                )
            },
        ) { innerPadding ->
            val contentModifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)

            when (currentScreen) {
                Screen.MOUSE -> MouseScreen(
                    onMove = viewModel::moveMouse,
                    onScroll = viewModel::scroll,
                    onClick = viewModel::clickMouseButton,
                    onButtonPress = viewModel::pressMouseButton,
                    onButtonRelease = viewModel::releaseMouseButton,
                    modifier = contentModifier,
                )

                Screen.KEYBOARD -> KeyboardScreen(
                    onTextChange = viewModel::sendTextChange,
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
}
