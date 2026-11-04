package io.github.phonemousekeyboard.ui.keyboard

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
import io.github.phonemousekeyboard.ui.components.GroupDivider
import io.github.phonemousekeyboard.ui.components.GroupNote
import io.github.phonemousekeyboard.ui.components.GroupTitleButton
import io.github.phonemousekeyboard.ui.components.SettingsGroup
import io.github.phonemousekeyboard.ui.components.SliderRow
import io.github.phonemousekeyboard.ui.components.SwitchRow
import kotlin.math.roundToInt

/** Delay sliders move in steps of this many milliseconds. */
private const val DELAY_STEP_MILLIS = 5

/**
 * Keyboard settings, opened from the ⋮ menu on the Keyboard tab. Changes apply immediately
 * and are saved automatically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardSettingsSheet(
    settings: KeyboardSettings,
    onChange: ((KeyboardSettings) -> KeyboardSettings) -> Unit,
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
                    text = "Keyboard settings",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                GroupTitleButton(label = "Reset", onClick = onReset)
            }

            TypingSpeedGroup(settings, onChange)
            TextBoxGroup(settings, onChange)
        }
    }
}

@Composable
private fun TypingSpeedGroup(
    settings: KeyboardSettings,
    onChange: ((KeyboardSettings) -> KeyboardSettings) -> Unit,
) {
    val range = KeyboardSettings.DELAY_RANGE_MILLIS
    val sliderRange = range.first.toFloat()..range.last.toFloat()

    SettingsGroup(title = "Typing speed") {
        SliderRow(
            title = "Delay per character",
            value = settings.characterDelayMillis.toFloat(),
            valueRange = sliderRange,
            valueLabel = "${settings.characterDelayMillis} ms",
            onValueChange = { delay ->
                onChange { it.copy(characterDelayMillis = delay.toDelayStep()) }
            },
        )
        GroupDivider()
        SliderRow(
            title = "Random extra delay",
            value = settings.randomExtraDelayMillis.toFloat(),
            valueRange = sliderRange,
            valueLabel = "0–${settings.randomExtraDelayMillis} ms",
            onValueChange = { delay ->
                onChange { it.copy(randomExtraDelayMillis = delay.toDelayStep()) }
            },
        )
        GroupNote(typingSpeedSummary(settings))
    }
}

@Composable
private fun TextBoxGroup(
    settings: KeyboardSettings,
    onChange: ((KeyboardSettings) -> KeyboardSettings) -> Unit,
) {
    SettingsGroup(title = "Text box") {
        SwitchRow(
            title = "Phone autocorrect and suggestions",
            detail = "Turn off to send exactly what you type",
            checked = settings.phoneAutocorrect,
            onCheckedChange = { enabled -> onChange { it.copy(phoneAutocorrect = enabled) } },
        )
        GroupDivider()
        SwitchRow(
            title = "Clear after Enter",
            detail = "Empty the box each time you press Enter",
            checked = settings.clearAfterEnter,
            onCheckedChange = { enabled -> onChange { it.copy(clearAfterEnter = enabled) } },
        )
    }
}

/** Explains in words what the two delay sliders add up to. */
private fun typingSpeedSummary(settings: KeyboardSettings): String {
    val fixed = settings.characterDelayMillis
    val random = settings.randomExtraDelayMillis
    return when {
        fixed == 0 && random == 0 -> "Characters are sent as fast as possible."
        random == 0 -> "Each character waits $fixed ms."
        else -> "Each character waits ${fixed}–${fixed + random} ms, at random. Useful when " +
            "pasting long text into apps that drop fast input, or for a more natural pace."
    }
}

private fun Float.toDelayStep(): Int = (this / DELAY_STEP_MILLIS).roundToInt() * DELAY_STEP_MILLIS
