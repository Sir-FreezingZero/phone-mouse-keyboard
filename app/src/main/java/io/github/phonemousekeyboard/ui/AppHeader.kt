package io.github.phonemousekeyboard.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The bar across the top of the app:
 *
 *   [menu]            Page title             [⋮]      ([←] instead of [menu] on menu pages)
 *   ──────────────═══════════──────────────        status line
 *
 * On main pages shown in the header ([headerPages]) the title can be swiped to move between
 * them ([TitleSwitcher]); other pages show a plain title. The [StatusLine] underneath shows the connection and input activity.
 */
@Composable
fun AppHeader(
    currentScreen: Screen,
    headerPages: List<Screen>,
    isConnected: Boolean,
    isSendingInput: Boolean,
    onScreenSelected: (Screen) -> Unit,
    onMenuClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    overflowItems: OverflowItems? = null,
) {
    Column(modifier = modifier.statusBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Main pages open the menu from here; other pages (e.g. Bluetooth) go back instead.
            if (currentScreen.isMainPage) {
                IconButton(onClick = onMenuClick) {
                    Icon(imageVector = Icons.Default.Menu, contentDescription = "Open menu")
                }
            } else {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            }

            // Equal-width buttons on both sides keep this area, and the title, centred.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                if (currentScreen in headerPages) {
                    // Rebuilt when pages are shown or hidden, so swiping follows the new list.
                    key(headerPages) {
                        TitleSwitcher(
                            pages = headerPages,
                            currentPage = currentScreen,
                            onPageSelected = onScreenSelected,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else {
                    PageTitle(title = currentScreen.title)
                }
            }

            OverflowMenu(overflowItems)
        }
        StatusLine(isConnected = isConnected, isSendingInput = isSendingInput)
    }
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
