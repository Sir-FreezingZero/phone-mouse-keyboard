package io.github.phonemousekeyboard.ui.airmouse

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
import io.github.phonemousekeyboard.ui.components.ChoiceRow
import io.github.phonemousekeyboard.ui.components.GroupDivider
import io.github.phonemousekeyboard.ui.components.GroupTitleButton
import io.github.phonemousekeyboard.ui.components.SettingsGroup
import io.github.phonemousekeyboard.ui.components.SliderRow
import io.github.phonemousekeyboard.ui.components.SwitchRow
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Air mouse settings, opened from the ⋮ menu on the Air mouse page or from the side menu.
 * Changes apply immediately and are saved automatically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirMouseSettingsSheet(
    settings: AirMouseSettings,
    hasGyroscope: Boolean,
    hasCompass: Boolean,
    onChange: ((AirMouseSettings) -> AirMouseSettings) -> Unit,
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
                    text = "Air mouse settings",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                GroupTitleButton(label = "Reset", onClick = onReset)
            }

            StyleGroup(settings, hasGyroscope, hasCompass, onChange)
            MovementGroup(settings, onChange)
            DirectionGroup(settings, onChange)
        }
    }
}

@Composable
private fun StyleGroup(
    settings: AirMouseSettings,
    hasGyroscope: Boolean,
    hasCompass: Boolean,
    onChange: ((AirMouseSettings) -> AirMouseSettings) -> Unit,
) {
    val style = settings.effectiveStyle(hasGyroscope)
    val detail = when (style) {
        AirMouseStyle.POINT -> when {
            hasGyroscope -> "Aim the top of the phone at the screen; turn and tilt to move."
            hasCompass -> "Aim the top of the phone at the screen; turn and tilt to move. " +
                "This phone has no gyroscope, so pointing uses the compass: less precise, " +
                "and thrown off by metal and magnets. Tilt may work better."
            else -> "This phone can't point (no gyroscope or compass), so Tilt is used."
        }
        AirMouseStyle.TILT -> "Tip the phone like a joystick; the further you tip it, the " +
            "faster the pointer moves. Recenter sets what counts as level."
    }

    SettingsGroup(title = "Style") {
        ChoiceRow(
            title = "How to move the pointer",
            options = AirMouseStyle.entries,
            selected = style,
            label = AirMouseStyle::label,
            onSelect = { chosen -> onChange { it.copy(style = chosen) } },
            detail = detail,
        )
    }
}

@Composable
private fun MovementGroup(
    settings: AirMouseSettings,
    onChange: ((AirMouseSettings) -> AirMouseSettings) -> Unit,
) {
    SettingsGroup(title = "Movement") {
        SliderRow(
            title = "Speed",
            value = settings.speed,
            valueRange = AirMouseSettings.SPEED_RANGE,
            valueLabel = settings.speed.asMultiplier(),
            onValueChange = { speed -> onChange { it.copy(speed = speed.roundToStep()) } },
        )
        GroupDivider()
        SliderRow(
            title = "Steadiness",
            value = settings.steadiness,
            valueRange = 0f..1f,
            valueLabel = settings.steadiness.asPercent(),
            onValueChange = { value -> onChange { it.copy(steadiness = value.roundToStep()) } },
        )
        GroupDivider()
        SliderRow(
            title = "Smoothing",
            value = settings.smoothing,
            valueRange = 0f..1f,
            valueLabel = settings.smoothing.asPercent(),
            onValueChange = { value -> onChange { it.copy(smoothing = value.roundToStep()) } },
        )
        GroupDivider()
        SwitchRow(
            title = "Move only while touching",
            detail = "The pointer only moves while a finger is on the Air mouse page",
            checked = settings.moveOnlyWhileTouching,
            onCheckedChange = { enabled -> onChange { it.copy(moveOnlyWhileTouching = enabled) } },
        )
    }
}

@Composable
private fun DirectionGroup(
    settings: AirMouseSettings,
    onChange: ((AirMouseSettings) -> AirMouseSettings) -> Unit,
) {
    SettingsGroup(title = "Direction") {
        SwitchRow(
            title = "Invert left and right",
            checked = settings.invertHorizontal,
            onCheckedChange = { enabled -> onChange { it.copy(invertHorizontal = enabled) } },
        )
        GroupDivider()
        SwitchRow(
            title = "Invert up and down",
            checked = settings.invertVertical,
            onCheckedChange = { enabled -> onChange { it.copy(invertVertical = enabled) } },
        )
    }
}

/** Snaps slider values to steps of 0.05 so labels read cleanly. */
private fun Float.roundToStep(): Float = (this * 20f).roundToInt() / 20f

/** "1×", "1.5×", "0.25×". */
private fun Float.asMultiplier(): String =
    String.format(Locale.US, "%.2f", this).trimEnd('0').trimEnd('.') + "×"

/** "30%". */
private fun Float.asPercent(): String = "${(this * 100).roundToInt()}%"
