package io.github.phonemousekeyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.phonemousekeyboard.bluetooth.ConnectionState
import io.github.phonemousekeyboard.ui.icons.AppIcons
import io.github.phonemousekeyboard.ui.theme.ConnectedGreen
import io.github.phonemousekeyboard.ui.theme.IdleGrey

/*
 * The slide-out menu. A floating panel, inset from the screen edges:
 *
 *   ╭──────────────────────────╮
 *   │ Phone Mouse & Keyboard   │   app name
 *   │ ● Connected to ZERO      │   connection summary (tap → Bluetooth)
 *   │──────────────────────────│
 *   │ CONTROL                  │
 *   │  ──────────────────────  │   underline, inset from both sides
 *   │  🖱 Mouse           [●]  │   current page highlighted; the switch shows or
 *   │  ⌨ Keyboard        [●]  │   hides the page in the header's swipeable title
 *   │  📱 Air mouse       [●]  │
 *   │  ↗ Links           [●]  │
 *   │  ⏯ Media           [●]  │
 *   │ CONNECT                  │
 *   │  ᛒ Bluetooth            │
 *   │ SETTINGS                 │
 *   │  ≡ Mouse settings        │
 *   │  ≡ Keyboard settings     │
 *   │  ≡ Air mouse settings    │
 *   │  ≡ Links settings        │
 *   │ ABOUT                    │
 *   │  </> Source code         │
 *   │                          │
 *   │ Version 0.1.0            │
 *   ╰──────────────────────────╯
 */

private val PanelShape = RoundedCornerShape(24.dp)
private val ItemShape = RoundedCornerShape(12.dp)

/** Share of the screen width the open menu covers. */
private const val PANEL_WIDTH_FRACTION = 0.75f

/** Left edge of headings and text in the panel. Items are inset a little less, for their highlight. */
private val ContentInset = 20.dp
private val ItemOuterInset = 8.dp

/** Height of a menu item: compact, but still comfortable to tap. */
private val ItemHeight = 38.dp

/** The header switches are shown at this fraction of a normal switch's size. */
private const val SMALL_SWITCH_SCALE = 0.7f

/** Where the project's source code lives. Shown under About. */
const val SOURCE_CODE_URL = "https://github.com/Sir-FreezingZero/phone-mouse-keyboard"

/** Everything the menu can do, grouped so [AppMenuContent] doesn't need a long parameter list. */
data class MenuActions(
    val onNavigate: (Screen) -> Unit,
    /** Shows or hides a main page in the header's swipeable title. */
    val onSetPageShown: (page: Screen, isShown: Boolean) -> Unit,
    val onOpenMouseSettings: () -> Unit,
    val onOpenAirMouseSettings: () -> Unit,
    val onOpenKeyboardSettings: () -> Unit,
    val onOpenLinksSettings: () -> Unit,
    val onOpenSourceCode: () -> Unit,
)

/**
 * The app's menu: a drawer that slides in from the left, opened by the header's menu button.
 */
@Composable
fun AppDrawer(
    drawerState: DrawerState,
    drawerContent: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        // Swiping can close the menu, but not open it: sideways swipes on the trackpad
        // must move the mouse rather than pull the menu out.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = drawerContent,
        content = content,
    )
}

@Composable
fun AppMenuContent(
    currentScreen: Screen,
    hiddenPages: Set<Screen>,
    connectionState: ConnectionState,
    actions: MenuActions,
) {
    val panelWidth = LocalConfiguration.current.screenWidthDp.dp * PANEL_WIDTH_FRACTION

    ModalDrawerSheet(
        drawerShape = PanelShape,
        drawerContainerColor = MaterialTheme.colorScheme.background,
        // The panel handles the status and navigation bars itself, by floating between them.
        windowInsets = WindowInsets(0),
        modifier = Modifier
            .width(panelWidth)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 12.dp, top = 8.dp, bottom = 12.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, PanelShape),
    ) {
        MenuHeader(
            connectionState = connectionState,
            onClick = { actions.onNavigate(Screen.BLUETOOTH) },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // Scrolls if the screen is short; the version stays at the bottom otherwise.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 4.dp),
        ) {
            MenuSectionLabel("Control")
            // The last page shown in the header can't be hidden too.
            val isLastShown = Screen.mainPages.count { it !in hiddenPages } == 1
            for ((page, icon) in ControlPageIcons) {
                val isShown = page !in hiddenPages
                ScreenItem(
                    screen = page,
                    icon = icon,
                    currentScreen = currentScreen,
                    actions = actions,
                    headerSwitch = HeaderSwitch(
                        isShown = isShown,
                        isEnabled = !(isShown && isLastShown),
                        onChange = { shown -> actions.onSetPageShown(page, shown) },
                    ),
                )
            }

            MenuSectionLabel("Connect")
            ScreenItem(Screen.BLUETOOTH, AppIcons.Bluetooth, currentScreen, actions)

            MenuSectionLabel("Settings")
            MenuItem("Mouse settings", AppIcons.Tune, onClick = actions.onOpenMouseSettings)
            MenuItem("Keyboard settings", AppIcons.Tune, onClick = actions.onOpenKeyboardSettings)
            MenuItem("Air mouse settings", AppIcons.Tune, onClick = actions.onOpenAirMouseSettings)
            MenuItem("Links settings", AppIcons.Tune, onClick = actions.onOpenLinksSettings)

            MenuSectionLabel("About")
            MenuItem("Source code", AppIcons.Code, onClick = actions.onOpenSourceCode)
        }

        Text(
            text = "Version ${appVersionName()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = ContentInset, vertical = 10.dp),
        )
    }
}

/** App name and a one-line connection summary. Tapping it opens the Bluetooth page. */
@Composable
private fun MenuHeader(connectionState: ConnectionState, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = ContentInset, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = "Phone Mouse & Keyboard", style = MaterialTheme.typography.titleMedium)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val isConnected = connectionState is ConnectionState.Connected
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(if (isConnected) ConnectedGreen else IdleGrey, CircleShape),
            )
            Text(
                text = connectionState.summary(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A category heading with a thin line under it that stops short of the panel's edges. */
@Composable
private fun MenuSectionLabel(text: String) {
    Column(
        modifier = Modifier.padding(
            start = ContentInset,
            end = ContentInset,
            top = 10.dp,
            bottom = 2.dp,
        ),
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 3.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

/** The main pages listed under Control, with their icons, in header order. */
private val ControlPageIcons: List<Pair<Screen, ImageVector>> = listOf(
    Screen.MOUSE to AppIcons.Mouse,
    Screen.KEYBOARD to AppIcons.Keyboard,
    Screen.AIR_MOUSE to AppIcons.AirMouse,
    Screen.LINKS to AppIcons.Link,
    Screen.MEDIA to AppIcons.Media,
)

/** The small switch beside a Control item: whether the page appears in the header. */
private class HeaderSwitch(
    val isShown: Boolean,
    val isEnabled: Boolean,
    val onChange: (Boolean) -> Unit,
)

/**
 * An item that opens one of the app's pages, highlighted while that page is open, with an
 * optional [headerSwitch] on the right.
 */
@Composable
private fun ScreenItem(
    screen: Screen,
    icon: ImageVector,
    currentScreen: Screen,
    actions: MenuActions,
    headerSwitch: HeaderSwitch? = null,
) {
    MenuItem(
        label = screen.title,
        icon = icon,
        isSelected = screen == currentScreen,
        onClick = { actions.onNavigate(screen) },
        trailing = headerSwitch?.let { switch ->
            @Composable {
                SmallSwitch(
                    checked = switch.isShown,
                    enabled = switch.isEnabled,
                    onCheckedChange = switch.onChange,
                    description = "Show ${screen.title} in the header",
                )
            }
        },
    )
}

/**
 * A switch scaled down to sit inside a menu row without making it taller. Its own touch area
 * is kept small too, so tapping the rest of the row still opens the page.
 */
@Composable
private fun SmallSwitch(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier
                .scale(SMALL_SWITCH_SCALE)
                .semantics { contentDescription = description },
        )
    }
}

/**
 * An icon and a label, and optionally something on the right ([trailing]). The selected item
 * gets a soft rounded highlight.
 */
@Composable
private fun MenuItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val highlight = if (isSelected) colors.surfaceVariant else Color.Transparent
    val contentColor = if (isSelected) colors.onSurface else colors.onSurface.copy(alpha = 0.8f)

    Row(
        modifier = Modifier
            .padding(horizontal = ItemOuterInset)
            .fillMaxWidth()
            .heightIn(min = ItemHeight)
            .clip(ItemShape)
            .background(highlight)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = ContentInset - ItemOuterInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** A short description of the connection for the menu header. */
private fun ConnectionState.summary(): String = when (this) {
    is ConnectionState.Connected -> "Connected to $deviceName"
    is ConnectionState.Connecting -> "Connecting to $deviceName…"
    is ConnectionState.Disconnected -> "Not connected"
    is ConnectionState.Starting -> "Starting Bluetooth…"
    is ConnectionState.Unavailable -> reason
}

/** The app's version name from its package info, e.g. "0.1.0". */
@Composable
private fun appVersionName(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
}
