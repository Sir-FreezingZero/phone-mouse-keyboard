package io.github.phonemousekeyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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

/** The tabs shown along the bottom of the app. */
enum class AppTab(val label: String) {
    MOUSE("Mouse"),
    KEYBOARD("Keyboard"),
}

/**
 * Text-only bottom tabs. The selected tab is marked with a bar along its top edge.
 */
@Composable
fun BottomTabBar(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
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
            AppTab.entries.forEach { tab ->
                TabItem(
                    tab = tab,
                    isSelected = tab == selectedTab,
                    onClick = { onTabSelected(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TabItem(
    tab: AppTab,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val textColor = if (isSelected) colors.primary else colors.onSurfaceVariant
    val indicatorColor = if (isSelected) colors.primary else Color.Transparent

    Box(
        modifier = modifier
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
            text = tab.label,
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
