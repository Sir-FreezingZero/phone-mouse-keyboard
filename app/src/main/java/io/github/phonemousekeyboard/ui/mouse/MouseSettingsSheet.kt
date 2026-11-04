package io.github.phonemousekeyboard.ui.mouse

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
import io.github.phonemousekeyboard.ui.components.GroupTitleButton
import io.github.phonemousekeyboard.ui.components.SettingsGroup
import io.github.phonemousekeyboard.ui.components.SliderRow
import io.github.phonemousekeyboard.ui.components.SwitchRow
import java.util.Locale

/**
 * Mouse settings, opened from the ⋮ menu on the Mouse tab. Changes apply immediately and are
 * saved automatically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MouseSettingsSheet(
    settings: MouseSettings,
    onChange: ((MouseSettings) -> MouseSettings) -> Unit,
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
                    text = "Mouse settings",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                GroupTitleButton(label = "Reset", onClick = onReset)
            }

            PointerGroup(settings, onChange)
            ScrollingGroup(settings, onChange)
            ClickingGroup(settings, onChange)
            AppearanceGroup(settings, onChange)
        }
    }
}

@Composable
private fun PointerGroup(
    settings: MouseSettings,
    onChange: ((MouseSettings) -> MouseSettings) -> Unit,
) {
    SettingsGroup(title = "Pointer") {
        SliderRow(
            title = "Speed",
            value = settings.pointerSpeed,
            valueRange = MouseSettings.SPEED_RANGE,
            valueLabel = settings.pointerSpeed.asMultiplier(),
            onValueChange = { speed -> onChange { it.copy(pointerSpeed = speed.roundToStep()) } },
        )
        GroupDivider()
        SwitchRow(
            title = "Acceleration",
            detail = "Fast swipes move further; slow movements stay precise",
            checked = settings.pointerAcceleration,
            onCheckedChange = { enabled -> onChange { it.copy(pointerAcceleration = enabled) } },
        )
    }
}

@Composable
private fun ScrollingGroup(
    settings: MouseSettings,
    onChange: ((MouseSettings) -> MouseSettings) -> Unit,
) {
    SettingsGroup(title = "Scrolling") {
        SliderRow(
            title = "Speed",
            value = settings.scrollSpeed,
            valueRange = MouseSettings.SPEED_RANGE,
            valueLabel = settings.scrollSpeed.asMultiplier(),
            onValueChange = { speed -> onChange { it.copy(scrollSpeed = speed.roundToStep()) } },
        )
        GroupDivider()
        SwitchRow(
            title = "Natural scrolling",
            detail = "Content follows your fingers, like on a phone. Off: drag down to " +
                "scroll down, like a mouse wheel",
            checked = settings.naturalScrolling,
            onCheckedChange = { enabled -> onChange { it.copy(naturalScrolling = enabled) } },
        )
        GroupDivider()
        SwitchRow(
            title = "Smooth scrolling",
            detail = "Scroll in fine steps instead of whole wheel notches. If it has no " +
                "effect, remove and re-pair the phone on the computer once",
            checked = settings.smoothScrolling,
            onCheckedChange = { enabled -> onChange { it.copy(smoothScrolling = enabled) } },
        )
    }
}

@Composable
private fun ClickingGroup(
    settings: MouseSettings,
    onChange: ((MouseSettings) -> MouseSettings) -> Unit,
) {
    SettingsGroup(title = "Clicking") {
        SwitchRow(
            title = "Tap to click",
            detail = "Tap the trackpad with one finger to left-click",
            checked = settings.tapToClick,
            onCheckedChange = { enabled -> onChange { it.copy(tapToClick = enabled) } },
        )
        GroupDivider()
        SwitchRow(
            title = "Two-finger tap to right-click",
            checked = settings.twoFingerTapRightClick,
            onCheckedChange = { enabled -> onChange { it.copy(twoFingerTapRightClick = enabled) } },
        )
        GroupDivider()
        SwitchRow(
            title = "Show Left and Right buttons",
            checked = settings.showButtons,
            onCheckedChange = { enabled -> onChange { it.copy(showButtons = enabled) } },
        )
        GroupDivider()
        SwitchRow(
            title = "Vibrate on click",
            checked = settings.vibrateOnClick,
            onCheckedChange = { enabled -> onChange { it.copy(vibrateOnClick = enabled) } },
        )
    }
}

@Composable
private fun AppearanceGroup(
    settings: MouseSettings,
    onChange: ((MouseSettings) -> MouseSettings) -> Unit,
) {
    SettingsGroup(title = "Appearance") {
        SwitchRow(
            title = "Touch orbs",
            detail = "Glowing orbs under your fingers on the trackpad",
            checked = settings.showTouchOrbs,
            onCheckedChange = { enabled -> onChange { it.copy(showTouchOrbs = enabled) } },
        )
    }
}

/** Snaps slider values to steps of 0.05 so the label reads cleanly, e.g. "1.25×". */
private fun Float.roundToStep(): Float = Math.round(this * 20f) / 20f

/** "1×", "1.5×", "0.25×". */
private fun Float.asMultiplier(): String =
    String.format(Locale.US, "%.2f", this).trimEnd('0').trimEnd('.') + "×"
