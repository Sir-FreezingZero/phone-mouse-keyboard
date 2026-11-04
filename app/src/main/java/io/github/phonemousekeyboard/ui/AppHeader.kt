package io.github.phonemousekeyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.phonemousekeyboard.ui.theme.ConnectedGreen

private val DotSize = 8.dp
private val TitleDotGap = 8.dp

/**
 * The bar across the top of the app:
 *
 *   [menu]          ● Page title             [⋮]
 *
 * The dot shows whether a computer is connected. The title is centred on the screen
 * regardless of the dot and the buttons on either side.
 */
@Composable
fun AppHeader(
    title: String,
    isConnected: Boolean,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    overflowItems: OverflowItems? = null,
) {
    Column(modifier = modifier.statusBarsPadding()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
        ) {
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                Icon(imageVector = Icons.Default.Menu, contentDescription = "Open menu")
            }

            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TitleDotGap),
            ) {
                ConnectionDot(isConnected = isConnected)
                Text(text = title, style = MaterialTheme.typography.titleLarge)
                // An invisible twin of the dot keeps the title itself centred on screen.
                Spacer(modifier = Modifier.size(DotSize))
            }

            Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                OverflowMenu(overflowItems)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

/** Green when a computer is connected, grey otherwise. */
@Composable
private fun ConnectionDot(isConnected: Boolean) {
    val color = if (isConnected) ConnectedGreen else MaterialTheme.colorScheme.outline
    val description = if (isConnected) "Connected" else "Not connected"

    Box(
        modifier = Modifier
            .size(DotSize)
            .background(color, CircleShape)
            .semantics { contentDescription = description },
    )
}

/**
 * Options for the "⋮" menu, supplied by the current page. Receives a function that closes the
 * menu, to call when an option is chosen.
 */
typealias OverflowItems = @Composable ColumnScope.(closeMenu: () -> Unit) -> Unit

/** The "⋮" menu, showing the current page's [items], or a placeholder if it has none. */
@Composable
private fun OverflowMenu(items: OverflowItems?) {
    var isExpanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { isExpanded = true }) {
            Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More options")
        }
        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false },
        ) {
            if (items != null) {
                items(this) { isExpanded = false }
            } else {
                DropdownMenuItem(
                    text = { Text("No options for this page") },
                    onClick = {},
                    enabled = false,
                )
            }
        }
    }
}
