package io.github.phonemousekeyboard.ui.mouse

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.phonemousekeyboard.input.MouseButton
import kotlin.math.hypot

/**
 * Pointer acceleration: finger movement per touch event (in phone pixels) at which the pointer
 * moves twice as far as normal, and the most it is ever multiplied by.
 */
private const val ACCELERATION_PIXELS = 20f
private const val MAX_ACCELERATION = 3f

/** Only the outside corners of the trackpad-and-buttons unit are rounded. */
private val UnitShape = RoundedCornerShape(8.dp)

/**
 * The "Mouse" tab: a large trackpad with left and right buttons underneath, drawn as one
 * connected unit like a laptop touchpad:
 *
 *   ╭───────────────────╮
 *   │                   │
 *   │     trackpad      │
 *   │                   │
 *   ├─────────┬─────────┤
 *   │  Left   │  Right  │
 *   ╰─────────┴─────────╯
 *
 * How it behaves is controlled by [settings].
 */
@Composable
fun MouseScreen(
    settings: MouseSettings,
    onMove: (dx: Int, dy: Int) -> Unit,
    onScroll: (fingerDeltaY: Float) -> Unit,
    onClick: (MouseButton) -> Unit,
    onButtonPress: (MouseButton) -> Unit,
    onButtonRelease: (MouseButton) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.outline
    val view = LocalView.current
    val vibrate = {
        if (settings.vibrateOnClick) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .clip(UnitShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, lineColor, UnitShape),
    ) {
        Trackpad(
            settings = settings,
            onMove = onMove,
            onScroll = onScroll,
            onClick = { button ->
                vibrate()
                onClick(button)
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )

        if (settings.showButtons) {
            HorizontalDivider(color = lineColor)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
            ) {
                MouseButtonPad(
                    label = "Left",
                    onPress = {
                        vibrate()
                        onButtonPress(MouseButton.LEFT)
                    },
                    onRelease = { onButtonRelease(MouseButton.LEFT) },
                    modifier = Modifier.weight(1f),
                )
                VerticalDivider(color = lineColor)
                MouseButtonPad(
                    label = "Right",
                    onPress = {
                        vibrate()
                        onButtonPress(MouseButton.RIGHT)
                    },
                    onRelease = { onButtonRelease(MouseButton.RIGHT) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Trackpad(
    settings: MouseSettings,
    onMove: (dx: Int, dy: Int) -> Unit,
    onScroll: (fingerDeltaY: Float) -> Unit,
    onClick: (MouseButton) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The gesture detector is created once, so always read the latest settings and callbacks.
    val currentSettings by rememberUpdatedState(settings)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnScroll by rememberUpdatedState(onScroll)
    val currentOnClick by rememberUpdatedState(onClick)

    val xAccumulator = remember { FractionalAccumulator() }
    val yAccumulator = remember { FractionalAccumulator() }
    val orbs = rememberTouchOrbState()

    Box(
        modifier = modifier.trackpadGestures(
            onMove = { dx, dy ->
                val scale = currentSettings.pointerSpeed * accelerationFor(dx, dy, currentSettings)
                val stepX = xAccumulator.add(dx * scale)
                val stepY = yAccumulator.add(dy * scale)
                if (stepX != 0 || stepY != 0) currentOnMove(stepX, stepY)
            },
            // Speed and direction are applied where the scroll is sent, from the saved settings.
            onScroll = { dy -> currentOnScroll(dy) },
            onTap = { fingerCount ->
                when {
                    fingerCount >= 2 && currentSettings.twoFingerTapRightClick ->
                        currentOnClick(MouseButton.RIGHT)
                    fingerCount == 1 && currentSettings.tapToClick ->
                        currentOnClick(MouseButton.LEFT)
                }
            },
            onTouchesChanged = orbs::update,
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (settings.showTouchOrbs) {
            TouchOrbs(state = orbs)
        }
        Text(
            text = trackpadHint(settings),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * With acceleration on, faster finger movement (more distance per touch event) is multiplied
 * more, up to [MAX_ACCELERATION]. Slow movement stays at 1× for precise positioning.
 */
private fun accelerationFor(dx: Float, dy: Float, settings: MouseSettings): Float {
    if (!settings.pointerAcceleration) return 1f
    val distance = hypot(dx, dy)
    return (1f + distance / ACCELERATION_PIXELS).coerceAtMost(MAX_ACCELERATION)
}

/** Explains only the gestures that are currently switched on. */
private fun trackpadHint(settings: MouseSettings): String {
    val clicks = listOfNotNull(
        "tap to click".takeIf { settings.tapToClick },
        "two-finger tap to right-click".takeIf { settings.twoFingerTapRightClick },
    ).joinToString(" · ").replaceFirstChar { it.uppercase() }

    return listOf("Trackpad", "", clicks, "Two-finger drag to scroll")
        .filterIndexed { index, line -> index < 2 || line.isNotEmpty() }
        .joinToString("\n")
}

/**
 * A mouse button that stays pressed for as long as the finger is down, so click-and-drag
 * works (hold Left here, drag on the trackpad with another finger).
 */
@Composable
private fun MouseButtonPad(
    label: String,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnPress by rememberUpdatedState(onPress)
    val currentOnRelease by rememberUpdatedState(onRelease)
    var isPressed by remember { mutableStateOf(false) }

    // Unpressed buttons share the unit's background; a press darkens just this button.
    val background = if (isPressed) MaterialTheme.colorScheme.outline else Color.Transparent

    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(background)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        currentOnPress()
                        tryAwaitRelease()
                        isPressed = false
                        currentOnRelease()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}
