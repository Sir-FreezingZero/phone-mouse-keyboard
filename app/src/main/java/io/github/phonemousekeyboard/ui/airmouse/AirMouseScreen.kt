package io.github.phonemousekeyboard.ui.airmouse

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import io.github.phonemousekeyboard.input.AirMotionProcessor
import io.github.phonemousekeyboard.input.AirMouseSensors
import io.github.phonemousekeyboard.input.MouseButton
import io.github.phonemousekeyboard.ui.mouse.FractionalAccumulator
import io.github.phonemousekeyboard.ui.mouse.MouseButtonRow
import java.util.Locale

private val UnitShape = RoundedCornerShape(8.dp)

/**
 * The "Air mouse" page: point the phone at the screen and turn or tilt it to move the pointer.
 *
 *   ╭───────────────────────────╮
 *   │ hint                Pause │
 *   │    ┌───────────────┐      │
 *   │    │   ↖ ~~~~      │      │   illustration of the pointer on a screen
 *   │    └───────────────┘      │
 *   │ Turn +12°/s · Tilt −3°/s  │   live readings
 *   ├─────────────┬─────────────┤
 *   │    Left     │    Right    │
 *   ╰─────────────┴─────────────╯
 *
 * Sensors only run while this page is visible, the app is in front, and the Air mouse isn't
 * paused (or, with "move only while touching", while a finger is on the page).
 */
@Composable
fun AirMouseScreen(
    settings: AirMouseSettings,
    vibrateOnClick: Boolean,
    onMove: (dx: Int, dy: Int) -> Unit,
    onButtonPress: (MouseButton) -> Unit,
    onButtonRelease: (MouseButton) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sensors = remember(context) { AirMouseSensors(context) }
    val processor = remember { AirMotionProcessor() }
    val illustration = remember { AirMouseIllustrationState() }
    val xAccumulator = remember { FractionalAccumulator() }
    val yAccumulator = remember { FractionalAccumulator() }

    var isPaused by rememberSaveable { mutableStateOf(false) }
    var isTouching by remember { mutableStateOf(false) }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()

    val isActive = sensors.isAvailable &&
        lifecycleState.isAtLeast(Lifecycle.State.RESUMED) &&
        !isPaused &&
        (!settings.moveOnlyWhileTouching || isTouching)

    // Sensor callbacks are set up once per activation, so always use the latest values.
    val currentSettings by rememberUpdatedState(settings)
    val currentOnMove by rememberUpdatedState(onMove)

    DisposableEffect(isActive) {
        if (isActive) {
            sensors.start(
                onGravity = processor::onGravity,
                onRotation = { x, y, z, timestamp ->
                    val motion = processor.onRotation(
                        x, y, z, timestamp,
                        currentSettings.toProcessorSettings(),
                    )
                    if (motion != null) {
                        illustration.onMotion(motion)
                        val stepX = xAccumulator.add(motion.dx)
                        val stepY = yAccumulator.add(motion.dy)
                        if (stepX != 0 || stepY != 0) currentOnMove(stepX, stepY)
                    }
                },
            )
        }
        onDispose {
            sensors.stop()
            processor.reset()
            illustration.settle()
        }
    }

    val view = LocalView.current
    val lineColor = MaterialTheme.colorScheme.outline

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .clip(UnitShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, lineColor, UnitShape),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .detectHolding { holding -> isTouching = holding },
        ) {
            if (sensors.isAvailable) {
                AirMouseIllustration(
                    state = illustration,
                    isActive = isActive,
                    frameColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    cursorColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxSize(),
                )
                Hint(modifier = Modifier.align(Alignment.TopStart))
                TextButton(
                    onClick = { isPaused = !isPaused },
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Text(if (isPaused) "Resume" else "Pause")
                }
                StatusText(
                    text = statusText(settings, isPaused, isActive, illustration),
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            } else {
                StatusText(
                    text = "This phone has no gyroscope, so the Air mouse can't work on it.",
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        HorizontalDivider(color = lineColor)

        MouseButtonRow(
            onPress = { button ->
                if (vibrateOnClick) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onButtonPress(button)
            },
            onRelease = onButtonRelease,
        )
    }
}

@Composable
private fun Hint(modifier: Modifier = Modifier) {
    Text(
        text = "Point the top of the phone at the screen.\nTurn to move sideways, tilt to move up and down.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 12.dp, top = 12.dp, end = 88.dp),
    )
}

@Composable
private fun StatusText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.padding(12.dp),
    )
}

/** The line under the illustration: live readings while moving, otherwise what to do. */
private fun statusText(
    settings: AirMouseSettings,
    isPaused: Boolean,
    isActive: Boolean,
    illustration: AirMouseIllustrationState,
): String = when {
    isPaused -> "Paused"
    !isActive && settings.moveOnlyWhileTouching -> "Touch and hold here to move the pointer"
    !isActive -> "Waiting…"
    else -> String.format(
        Locale.US,
        "Turn %+.0f°/s  ·  Tilt %+.0f°/s",
        illustration.turnDegreesPerSecond,
        illustration.tiltDegreesPerSecond,
    )
}

/** Reports whether any finger is on this element, without taking the touches away from children. */
private fun Modifier.detectHolding(onHoldingChanged: (Boolean) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            onHoldingChanged(true)
            do {
                val event = awaitPointerEvent()
            } while (event.changes.any { it.pressed })
            onHoldingChanged(false)
        }
    }

private fun AirMouseSettings.toProcessorSettings() = AirMotionProcessor.Settings(
    speed = speed,
    steadiness = steadiness,
    smoothing = smoothing,
    invertHorizontal = invertHorizontal,
    invertVertical = invertVertical,
)
