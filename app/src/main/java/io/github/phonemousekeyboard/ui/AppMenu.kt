package io.github.phonemousekeyboard.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/*
 * Menu layout. The panel floats slightly inset from the screen edges, with rounded corners
 * and a thin border:
 *
 *   ╭──────────────────────────╮
 *   │ Menu                     │   title
 *   │──────────────────────────│
 *   │  Connect               ⌃ │   section heading (tap to collapse)
 *   │  ────────────────────    │
 *   │     Bluetooth            │   item
 *   ╰──────────────────────────╯
 */

private val PanelShape = RoundedCornerShape(24.dp)
private val ItemShape = RoundedCornerShape(12.dp)

/** Share of the screen width the open menu covers. */
private const val PANEL_WIDTH_FRACTION = 0.75f

/** Left edge of section headings; items are indented further. */
private val SectionInset = 24.dp
private val ItemInset = 36.dp

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

/**
 * The menu's contents. Items are grouped under section headings that can be collapsed.
 *
 *  Connect
 *    Bluetooth
 */
@Composable
fun AppMenuContent(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
) {
    var isConnectExpanded by rememberSaveable { mutableStateOf(true) }
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
        Text(
            text = "Menu",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = SectionInset, vertical = 12.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(6.dp))

        MenuSection(
            label = "Connect",
            isExpanded = isConnectExpanded,
            onToggle = { isConnectExpanded = !isConnectExpanded },
        ) {
            MenuItem(
                label = Screen.BLUETOOTH.title,
                isSelected = currentScreen == Screen.BLUETOOTH,
                onClick = { onNavigate(Screen.BLUETOOTH) },
            )
        }
    }
}

/** A heading with a line under it, and the items that belong to it. */
@Composable
private fun MenuSection(
    label: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    items: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = SectionInset, end = 20.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (isExpanded) "Collapse $label" else "Expand $label",
        )
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    AnimatedVisibility(visible = isExpanded) {
        Column(modifier = Modifier.padding(top = 2.dp)) {
            items()
        }
    }
}

/** A plain text item. The current page gets a soft highlight. */
@Composable
private fun MenuItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val highlight = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
    // Slightly dimmer than headings, so the structure reads at a glance.
    val textColor = if (isSelected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
    }

    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = textColor,
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .clip(ItemShape)
            .background(highlight)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = ItemInset - 12.dp, top = 7.dp, bottom = 7.dp),
    )
}
