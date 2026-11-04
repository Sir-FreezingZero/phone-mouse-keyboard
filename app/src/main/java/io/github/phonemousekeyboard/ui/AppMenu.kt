package io.github.phonemousekeyboard.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp

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
 * The menu's contents. Items are grouped under headings that can be collapsed.
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

    ModalDrawerSheet(drawerShape = RectangleShape) {
        Text(
            text = "Menu",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        MenuGroupHeader(
            label = "Connect",
            isExpanded = isConnectExpanded,
            onToggle = { isConnectExpanded = !isConnectExpanded },
        )
        AnimatedVisibility(visible = isConnectExpanded) {
            MenuChildItem(
                label = Screen.BLUETOOTH.title,
                isSelected = currentScreen == Screen.BLUETOOTH,
                onClick = { onNavigate(Screen.BLUETOOTH) },
            )
        }
    }
}

/** A heading that shows or hides the items beneath it. */
@Composable
private fun MenuGroupHeader(
    label: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 24.dp, vertical = 16.dp),
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
}

/** An item nested under a [MenuGroupHeader]; indented to show that it belongs to the group. */
@Composable
private fun MenuChildItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = { Text(label) },
        selected = isSelected,
        onClick = onClick,
        shape = RectangleShape,
        modifier = Modifier.padding(start = 24.dp),
    )
}
