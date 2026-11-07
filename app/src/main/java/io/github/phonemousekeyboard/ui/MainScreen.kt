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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.phonemousekeyboard.MainViewModel
import io.github.phonemousekeyboard.PermissionStatus
import io.github.phonemousekeyboard.bluetooth.ConnectionState
import io.github.phonemousekeyboard.input.AirMouseSensors
import io.github.phonemousekeyboard.ui.airmouse.AirMouseScreen
import io.github.phonemousekeyboard.ui.airmouse.AirMouseSettingsSheet
import io.github.phonemousekeyboard.ui.bluetooth.BluetoothActions
import io.github.phonemousekeyboard.ui.bluetooth.BluetoothScreen
import io.github.phonemousekeyboard.ui.keyboard.KeyboardScreen
import io.github.phonemousekeyboard.ui.keyboard.KeyboardSettingsSheet
import io.github.phonemousekeyboard.ui.links.LinksActions
import io.github.phonemousekeyboard.ui.links.LinksScreen
import io.github.phonemousekeyboard.ui.links.LinksSettingsSheet
import io.github.phonemousekeyboard.ui.media.MediaScreen
import io.github.phonemousekeyboard.ui.mouse.MouseScreen
import io.github.phonemousekeyboard.ui.mouse.MouseSettingsSheet
import kotlinx.coroutines.launch

/** Actions that need the Activity, because they open system dialogs or other apps. */
data class SystemActions(
    val requestPermissions: () -> Unit,
    val turnOnBluetooth: () -> Unit,
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
    val airMouseSettings by viewModel.airMouseSettings.collectAsStateWithLifecycle()
    val keyboardSettings by viewModel.keyboardSettings.collectAsStateWithLifecycle()
    val pendingKeystrokes by viewModel.pendingKeystrokes.collectAsStateWithLifecycle()
    val links by viewModel.links.collectAsStateWithLifecycle()
    val linksSettings by viewModel.linksSettings.collectAsStateWithLifecycle()
    val openingLinkAddress by viewModel.openingLinkAddress.collectAsStateWithLifecycle()
    val hiddenPages by viewModel.hiddenPages.collectAsStateWithLifecycle()

    var currentScreen by rememberSaveable { mutableStateOf(Screen.MOUSE) }
    // Where the back button returns to from a menu page.
    var lastMainPage by rememberSaveable { mutableStateOf(Screen.MOUSE) }

    var isMouseSettingsOpen by rememberSaveable { mutableStateOf(false) }
    var isAirMouseSettingsOpen by rememberSaveable { mutableStateOf(false) }
    // Only used to describe which Air mouse styles this phone supports.
    val context = LocalContext.current
    val airMouseSensors = remember(context) { AirMouseSensors(context) }
    var isKeyboardSettingsOpen by rememberSaveable { mutableStateOf(false) }
    var isLinksSettingsOpen by rememberSaveable { mutableStateOf(false) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val navigateTo: (Screen) -> Unit = { screen ->
        currentScreen = screen
        if (screen.isMainPage) lastMainPage = screen
    }

    BackHandler(enabled = !currentScreen.isMainPage) { currentScreen = lastMainPage }

    // Every menu choice also closes the menu.
    val uriHandler = LocalUriHandler.current
    fun closeMenuAnd(action: () -> Unit) {
        action()
        scope.launch { drawerState.close() }
    }
    val menuActions = MenuActions(
        onNavigate = { screen -> closeMenuAnd { navigateTo(screen) } },
        // Leaves the menu open, so several pages can be switched in one go.
        onSetPageShown = viewModel::setPageShown,
        onOpenMouseSettings = { closeMenuAnd { isMouseSettingsOpen = true } },
        onOpenAirMouseSettings = { closeMenuAnd { isAirMouseSettingsOpen = true } },
        onOpenKeyboardSettings = { closeMenuAnd { isKeyboardSettingsOpen = true } },
        onOpenLinksSettings = { closeMenuAnd { isLinksSettingsOpen = true } },
        onOpenSourceCode = {
            // Fails quietly if the phone has no browser.
            closeMenuAnd { runCatching { uriHandler.openUri(SOURCE_CODE_URL) } }
        },
    )

    val bluetoothActions = remember(viewModel, systemActions) {
        BluetoothActions(
            onGrantPermissions = systemActions.requestPermissions,
            onTurnOnBluetooth = systemActions.turnOnBluetooth,
            onRetryService = viewModel::retryBluetoothService,
            onOpenSystemSettings = systemActions.openBluetoothSettings,
            onConnect = viewModel::connect,
            onDisconnect = viewModel::disconnect,
        )
    }

    val linksActions = remember(viewModel) {
        LinksActions(
            onOpen = viewModel::openLink,
            onSave = viewModel::saveLink,
            onDelete = viewModel::deleteLink,
            onMove = viewModel::moveLink,
        )
    }

    AppDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppMenuContent(
                currentScreen = currentScreen,
                hiddenPages = hiddenPages,
                connectionState = connectionState,
                actions = menuActions,
            )
        },
    ) {
        Scaffold(
            topBar = {
                AppHeader(
                    currentScreen = currentScreen,
                    headerPages = shownMainPages(hiddenPages),
                    isConnected = connectionState is ConnectionState.Connected,
                    isSendingInput = isSendingInput,
                    onScreenSelected = navigateTo,
                    onBackClick = { currentScreen = lastMainPage },
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
                        Screen.AIR_MOUSE -> settingsOverflowItems(
                            settingsLabel = "Air mouse settings",
                            onOpenSettings = { isAirMouseSettingsOpen = true },
                            onReset = viewModel::resetAirMouseSettings,
                        )
                        Screen.LINKS -> settingsOverflowItems(
                            settingsLabel = "Links settings",
                            onOpenSettings = { isLinksSettingsOpen = true },
                            onReset = viewModel::resetLinksSettings,
                        )
                        Screen.MEDIA, Screen.BLUETOOTH -> null
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
                    onBackspace = viewModel::pressBackspace,
                    onStopSending = viewModel::stopTyping,
                    // Keep the text box above the on-screen keyboard.
                    modifier = contentModifier.imePadding(),
                )

                Screen.AIR_MOUSE -> AirMouseScreen(
                    settings = airMouseSettings,
                    vibrateOnClick = mouseSettings.vibrateOnClick,
                    onMove = viewModel::moveMouse,
                    onButtonPress = viewModel::pressMouseButton,
                    onButtonRelease = viewModel::releaseMouseButton,
                    modifier = contentModifier,
                )

                Screen.LINKS -> LinksScreen(
                    links = links,
                    target = linksSettings.target,
                    isConnected = connectionState is ConnectionState.Connected,
                    openingAddress = openingLinkAddress,
                    actions = linksActions,
                    modifier = contentModifier,
                )

                Screen.MEDIA -> MediaScreen(
                    vibrateOnPress = mouseSettings.vibrateOnClick,
                    onPress = viewModel::pressMediaKey,
                    onRelease = viewModel::releaseMediaKey,
                    modifier = contentModifier,
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

    if (isAirMouseSettingsOpen) {
        AirMouseSettingsSheet(
            settings = airMouseSettings,
            hasGyroscope = airMouseSensors.hasGyroscope,
            hasCompass = airMouseSensors.hasCompass,
            onChange = viewModel::updateAirMouseSettings,
            onReset = viewModel::resetAirMouseSettings,
            onDismiss = { isAirMouseSettingsOpen = false },
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

    if (isLinksSettingsOpen) {
        LinksSettingsSheet(
            settings = linksSettings,
            onChange = viewModel::updateLinksSettings,
            onReset = viewModel::resetLinksSettings,
            onDismiss = { isLinksSettingsOpen = false },
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
