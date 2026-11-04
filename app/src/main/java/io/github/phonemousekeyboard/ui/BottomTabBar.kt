package io.github.phonemousekeyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Text-only bottom tabs. The selected tab is marked with a bar along its top edge.
 * When a non-tab page (such as Bluetooth) is open, no tab is marked.
 */
@Composable
fun BottomTabBar(
    currentScreen: Screen,
    onTabSelected: (Screen) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.navigationBarsPadding()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .selectableGroup(),
        ) {
            Screen.tabs.forEach { tab ->
                TabItem(
                    label = tab.title,
                    isSelected = tab == currentScreen,
                    onClick = { onTabSelected(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TabItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val textColor = if (isSelected) colors.primary else colors.onSurfaceVariant
    val indicatorColor = if (isSelected) colors.primary else Color.Transparent

    Box(
        modifier = modifier
            // Fill the bar's height so the label is centred and the whole tab is tappable.
            .fillMaxHeight()
            .selectable(selected = isSelected, onClick = onClick, role = Role.Tab),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(indicatorColor)
                .align(Alignment.TopCenter),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
