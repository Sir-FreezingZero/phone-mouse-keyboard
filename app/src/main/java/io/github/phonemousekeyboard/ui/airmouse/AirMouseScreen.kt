package io.github.phonemousekeyboard.ui.airmouse

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import io.github.phonemousekeyboard.input.AirMotionSettings
import io.github.phonemousekeyboard.input.AirMouseController
import io.github.phonemousekeyboard.input.AirMouseMethod
import io.github.phonemousekeyboard.input.AirMouseSensors
import io.github.phonemousekeyboard.input.TiltControl
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
 * How phone movement is read depends on the chosen style and the phone's sensors, see
 * [AirMouseMethod]. Sensors only run while this page is visible, the app is in front, and the Air mouse isn't
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
    val method = remember(sensors, settings.style) { methodFor(settings, sensors) }
    val controller = remember(method) { method?.let(AirMouseController::create) }
    val illustration = remember { AirMouseIllustrationState() }
    val xAccumulator = remember { FractionalAccumulator() }
    val yAccumulator = remember { FractionalAccumulator() }

    var isPaused by rememberSaveable { mutableStateOf(false) }
    var isTouching by remember { mutableStateOf(false) }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()

    val isActive = controller != null &&
        lifecycleState.isAtLeast(Lifecycle.State.RESUMED) &&
        !isPaused &&
        (!settings.moveOnlyWhileTouching || isTouching)

    // Sensor callbacks are set up once per activation, so always use the latest values.
    val currentSettings by rememberUpdatedState(settings)
    val currentOnMove by rememberUpdatedState(onMove)

    DisposableEffect(isActive, controller) {
        if (isActive && method != null && controller != null) {
            sensors.start(method) { reading ->
                val motion = controller.onReading(reading, currentSettings.toMotionSettings())
                if (motion != null) {
                    illustration.onMotion(motion)
                    val stepX = xAccumulator.add(motion.dx)
                    val stepY = yAccumulator.add(motion.dy)
                    if (stepX != 0 || stepY != 0) currentOnMove(stepX, stepY)
                }
            }
        }
        onDispose {
            sensors.stop()
            controller?.reset()
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
            if (method != null) {
                AirMouseIllustration(
                    state = illustration,
                    isActive = isActive,
                    frameColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    cursorColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxSize(),
                )
                Hint(method = method, modifier = Modifier.align(Alignment.TopStart))
                Row(modifier = Modifier.align(Alignment.TopEnd)) {
                    // Tilt measures from a "level" position; let the user reset it at will.
                    if (controller is TiltControl) {
                        TextButton(onClick = controller::recenter, enabled = isActive) {
                            Text("Recenter")
                        }
                    }
                    TextButton(onClick = { isPaused = !isPaused }) {
                        Text(if (isPaused) "Resume" else "Pause")
                    }
                }
                StatusText(
                    text = statusText(method, settings, isPaused, isActive, illustration),
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            } else {
                StatusText(
                    text = "This phone has no motion sensors the Air mouse can use.",
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
private fun Hint(method: AirMouseMethod, modifier: Modifier = Modifier) {
    val text = when (method) {
        AirMouseMethod.GYROSCOPE_POINTING ->
            "Point the top of the phone at the screen.\nTurn to move sideways, tilt to move up and down."
        AirMouseMethod.COMPASS_POINTING ->
            "Point the top of the phone at the screen.\nTurn and tilt to move. Uses the compass: " +
                "keep away from metal and magnets."
        AirMouseMethod.TILT ->
            "Tip the phone like a joystick.\nThe further you tip it, the faster the pointer moves."
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        // Leaves room on the right for the Recenter and Pause buttons.
        modifier = modifier.padding(start = 12.dp, top = 12.dp, end = 176.dp),
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
    method: AirMouseMethod,
    settings: AirMouseSettings,
    isPaused: Boolean,
    isActive: Boolean,
    illustration: AirMouseIllustrationState,
): String {
    val format = when (method) {
        AirMouseMethod.GYROSCOPE_POINTING,
        AirMouseMethod.COMPASS_POINTING -> "Turn %+.0f°/s  ·  Tilt %+.0f°/s"
        AirMouseMethod.TILT -> "Sideways %+.0f°  ·  Forward %+.0f°"
    }
    return when {
        isPaused -> "Paused"
        !isActive && settings.moveOnlyWhileTouching -> "Touch and hold here to move the pointer"
        !isActive -> "Waiting…"
        else -> String.format(
            Locale.US,
            format,
            illustration.horizontalReading,
            illustration.verticalReading,
        )
    }
}

/**
 * The method for the chosen style on this phone. Falls back to the other style if this phone
 * lacks the sensors for the chosen one; null if it has none of them.
 */
private fun methodFor(settings: AirMouseSettings, sensors: AirMouseSensors): AirMouseMethod? {
    val tilt = AirMouseMethod.TILT.takeIf { sensors.hasAccelerometer }
    return when (settings.effectiveStyle(sensors.hasGyroscope)) {
        AirMouseStyle.POINT -> sensors.pointingMethod ?: tilt
        AirMouseStyle.TILT -> tilt ?: sensors.pointingMethod
    }
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

private fun AirMouseSettings.toMotionSettings() = AirMotionSettings(
    speed = speed,
    steadiness = steadiness,
    smoothing = smoothing,
    invertHorizontal = invertHorizontal,
    invertVertical = invertVertical,
)
