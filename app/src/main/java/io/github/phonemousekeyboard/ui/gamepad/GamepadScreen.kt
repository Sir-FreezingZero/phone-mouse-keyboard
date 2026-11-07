package io.github.phonemousekeyboard.ui.gamepad

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import io.github.phonemousekeyboard.input.DpadDirection
import io.github.phonemousekeyboard.input.GamepadButton
import io.github.phonemousekeyboard.input.GamepadStick
import io.github.phonemousekeyboard.ui.components.pagePadding

private val ButtonShape = RoundedCornerShape(8.dp)

private val ShoulderRowHeight = 40.dp
private val ShoulderButtonWidth = 64.dp
private val SmallButtonHeight = 30.dp
private val SmallButtonWidth = 56.dp
private val Spacing = 8.dp

/** Sticks, D-pad and face buttons are never drawn bigger than this. */
private val MaxControlSize = 180.dp

/** Everything the Gamepad page sends, grouped to keep the parameter list short. */
data class GamepadActions(
    val onPress: (GamepadButton) -> Unit,
    val onRelease: (GamepadButton) -> Unit,
    val onDpad: (DpadDirection?) -> Unit,
    val onStick: (stick: GamepadStick, x: Float, y: Float) -> Unit,
    /** Lets go of everything; called when the page closes. */
    val onReset: () -> Unit,
)

/**
 * The Gamepad page: a game controller the computer sees as a standard gamepad.
 *
 * Portrait:                          Landscape:
 *   [L2][L1]            [R1][R2]       [L2][L1]                          [R1][R2]
 *   [L3][Select][Home][Start][R3]      (L stick) (D-pad) [L3…R3] (A B X Y) (R stick)
 *   (L stick)          (A B X Y)
 *   (D-pad)            (R stick)
 */
@Composable
fun GamepadScreen(
    vibrateOnPress: Boolean,
    actions: GamepadActions,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val vibrate = {
        if (vibrateOnPress) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
    val press: (GamepadButton) -> Unit = { button ->
        vibrate()
        actions.onPress(button)
    }
    val dpad: (DpadDirection?) -> Unit = { direction ->
        if (direction != null) vibrate()
        actions.onDpad(direction)
    }

    // Nothing may stay held on the computer once the page is gone.
    val currentOnReset by rememberUpdatedState(actions.onReset)
    DisposableEffect(Unit) {
        onDispose { currentOnReset() }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .pagePadding(),
    ) {
        val isLandscape = maxWidth > maxHeight

        Column(verticalArrangement = Arrangement.spacedBy(Spacing)) {
            ShoulderButtons(press, actions.onRelease)

            if (isLandscape) {
                val controlSize = minOf(
                    (maxWidth - SmallButtonWidth - Spacing * 6) / 4,
                    maxHeight - ShoulderRowHeight - Spacing,
                    MaxControlSize,
                )
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Thumbstick(controlSize, "Left stick", leftStick(actions))
                    Dpad(controlSize, dpad)
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing / 2)) {
                        SmallButtons.forEach { SmallButton(it, press, actions.onRelease) }
                    }
                    FaceButtons(controlSize, press, actions.onRelease)
                    Thumbstick(controlSize, "Right stick", rightStick(actions))
                }
            } else {
                val controlSize = minOf(
                    (maxWidth - Spacing) / 2,
                    (maxHeight - ShoulderRowHeight - SmallButtonHeight - Spacing * 4) / 2,
                    MaxControlSize,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing / 2, Alignment.CenterHorizontally),
                ) {
                    SmallButtons.forEach { SmallButton(it, press, actions.onRelease) }
                }
                Row(modifier = Modifier.fillMaxSize()) {
                    ControlColumn(modifier = Modifier.weight(1f)) {
                        Thumbstick(controlSize, "Left stick", leftStick(actions))
                        Dpad(controlSize, dpad)
                    }
                    ControlColumn(modifier = Modifier.weight(1f)) {
                        FaceButtons(controlSize, press, actions.onRelease)
                        Thumbstick(controlSize, "Right stick", rightStick(actions))
                    }
                }
            }
        }
    }
}

/** The buttons between the two halves: stick clicks, Select, Home and Start. */
private val SmallButtons = listOf(
    GamepadButton.L3,
    GamepadButton.SELECT,
    GamepadButton.HOME,
    GamepadButton.START,
    GamepadButton.R3,
)

private fun leftStick(actions: GamepadActions): (Float, Float) -> Unit =
    { x, y -> actions.onStick(GamepadStick.LEFT, x, y) }

private fun rightStick(actions: GamepadActions): (Float, Float) -> Unit =
    { x, y -> actions.onStick(GamepadStick.RIGHT, x, y) }

/** L2 and L1 at the left, R1 and R2 at the right, as on a controller's top edge. */
@Composable
private fun ShoulderButtons(
    onPress: (GamepadButton) -> Unit,
    onRelease: (GamepadButton) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ShoulderRowHeight),
        horizontalArrangement = Arrangement.spacedBy(Spacing),
    ) {
        ShoulderButton(GamepadButton.L2, onPress, onRelease)
        ShoulderButton(GamepadButton.L1, onPress, onRelease)
        Spacer(modifier = Modifier.weight(1f))
        ShoulderButton(GamepadButton.R1, onPress, onRelease)
        ShoulderButton(GamepadButton.R2, onPress, onRelease)
    }
}

@Composable
private fun ShoulderButton(
    button: GamepadButton,
    onPress: (GamepadButton) -> Unit,
    onRelease: (GamepadButton) -> Unit,
) {
    GamepadButtonPad(
        label = button.label,
        shape = ButtonShape,
        onPress = { onPress(button) },
        onRelease = { onRelease(button) },
        modifier = Modifier
            .width(ShoulderButtonWidth)
            .height(ShoulderRowHeight),
    )
}

@Composable
private fun SmallButton(
    button: GamepadButton,
    onPress: (GamepadButton) -> Unit,
    onRelease: (GamepadButton) -> Unit,
) {
    GamepadButtonPad(
        label = button.label,
        shape = ButtonShape,
        onPress = { onPress(button) },
        onRelease = { onRelease(button) },
        modifier = Modifier.size(width = SmallButtonWidth, height = SmallButtonHeight),
    )
}

/** One thumb's half of the portrait layout: two controls, spread out vertically. */
@Composable
private fun ControlColumn(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        content()
    }
}
