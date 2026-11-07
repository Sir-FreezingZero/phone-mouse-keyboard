package io.github.phonemousekeyboard.ui.links

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.phonemousekeyboard.input.LinkTarget
import io.github.phonemousekeyboard.input.ShortcutKey
import io.github.phonemousekeyboard.ui.components.ChoiceRow
import io.github.phonemousekeyboard.ui.components.GroupDivider
import io.github.phonemousekeyboard.ui.components.GroupNote
import io.github.phonemousekeyboard.ui.components.GroupTitleButton
import io.github.phonemousekeyboard.ui.components.SettingsGroup
import io.github.phonemousekeyboard.ui.components.SliderRow
import kotlin.math.roundToInt

/** The wait slider moves in steps of this many milliseconds. */
private const val WAIT_STEP_MILLIS = 50

/**
 * Links settings, opened from the ⋮ menu on the Links page. Changes apply immediately and are
 * saved automatically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinksSettingsSheet(
    settings: LinksSettings,
    onChange: ((LinksSettings) -> LinksSettings) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Links settings",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                GroupTitleButton(label = "Reset", onClick = onReset)
            }

            OpeningGroup(settings, onChange)
            TimingGroup(settings, onChange)
        }
    }
}

@Composable
private fun OpeningGroup(
    settings: LinksSettings,
    onChange: ((LinksSettings) -> LinksSettings) -> Unit,
) {
    SettingsGroup(title = "Opening") {
        ChoiceRow(
            title = "Open links in",
            options = LinkTarget.entries,
            selected = settings.target,
            label = ::targetLabel,
            onSelect = { target -> onChange { it.copy(target = target) } },
            detail = targetDetail(settings),
        )
        // The Run box always uses the Windows key, so this choice doesn't apply to it.
        if (settings.target != LinkTarget.RUN_BOX) {
            GroupDivider()
            ChoiceRow(
                title = "Shortcut key",
                options = ShortcutKey.entries,
                selected = settings.shortcutKey,
                label = ::shortcutKeyLabel,
                onSelect = { key -> onChange { it.copy(shortcutKey = key) } },
                detail = "Ctrl for Windows, Linux and ChromeOS; Cmd for a Mac.",
            )
        }
    }
}

@Composable
private fun TimingGroup(
    settings: LinksSettings,
    onChange: ((LinksSettings) -> LinksSettings) -> Unit,
) {
    val range = LinksSettings.WAIT_RANGE_MILLIS

    SettingsGroup(title = "Timing") {
        SliderRow(
            title = "Wait before typing the address",
            value = settings.waitMillis.toFloat(),
            valueRange = range.first.toFloat()..range.last.toFloat(),
            valueLabel = "${settings.waitMillis} ms",
            onValueChange = { millis ->
                val stepped = (millis / WAIT_STEP_MILLIS).roundToInt() * WAIT_STEP_MILLIS
                onChange { it.copy(waitMillis = stepped) }
            },
        )
        GroupNote(
            "If the start of the address goes missing, the new tab or window wasn't ready " +
                "yet: wait longer.",
        )
    }
}

private fun targetLabel(target: LinkTarget): String = when (target) {
    LinkTarget.NEW_TAB -> "New tab"
    LinkTarget.NEW_WINDOW -> "New window"
    LinkTarget.RUN_BOX -> "Run box"
}

private fun shortcutKeyLabel(key: ShortcutKey): String = when (key) {
    ShortcutKey.CTRL -> "Ctrl"
    ShortcutKey.COMMAND -> "Cmd (Mac)"
}

/** Explains the selected target, including the shortcut it presses. */
private fun targetDetail(settings: LinksSettings): String {
    val key = shortcutKeyLabel(settings.shortcutKey).substringBefore(" ")
    return when (settings.target) {
        LinkTarget.NEW_TAB ->
            "Presses $key+T, so a browser window must be the one in front on the computer."
        LinkTarget.NEW_WINDOW ->
            "Presses $key+N, so a browser window must be the one in front on the computer."
        LinkTarget.RUN_BOX ->
            "Windows only. Presses Windows+R and opens the link in the default browser, " +
                "starting it if it isn't open. Works whatever window is in front."
    }
}
