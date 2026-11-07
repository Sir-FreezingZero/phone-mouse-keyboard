package io.github.phonemousekeyboard.ui.gamepad

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.phonemousekeyboard.input.DpadDirection
import io.github.phonemousekeyboard.input.GamepadButton
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/*
 * The gamepad's on-screen controls. Each control follows its own finger, so several can be
 * used at once (a stick with one thumb and buttons with the other).
 */

/** The stick's knob, as a share of the stick's size. */
private const val KNOB_FRACTION = 0.4f

/** Stick movement smaller than this share of its range counts as centred, against jitter. */
private const val STICK_DEAD_ZONE = 0.08f

/** Touches on the D-pad nearer its centre than this share of its radius press nothing. */
private const val DPAD_DEAD_ZONE = 0.2f

/** Width of each arm of the D-pad's cross, as a share of its size. */
private const val DPAD_ARM_FRACTION = 0.34f

/**
 * A thumbstick. Touch anywhere in it and the knob follows the finger; letting go springs it
 * back to the centre.
 *
 * @param onMove the stick's position: x and y from -1 (left / up) to 1 (right / down).
 */
@Composable
fun Thumbstick(
    size: Dp,
    description: String,
    onMove: (x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnMove by rememberUpdatedState(onMove)
    var position by remember { mutableStateOf(Offset.Zero) }
    val colors = MaterialTheme.colorScheme

    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                val radius = this.size.width / 2f
                // How far the knob's centre can travel from the middle.
                val travel = radius * (1f - KNOB_FRACTION)

                fun moveTo(touch: Offset) {
                    var offset = (touch - Offset(radius, radius)) / travel
                    val distance = offset.getDistance()
                    if (distance > 1f) offset /= distance
                    position = offset
                    if (distance < STICK_DEAD_ZONE) {
                        currentOnMove(0f, 0f)
                    } else {
                        currentOnMove(offset.x, offset.y)
                    }
                }

                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    moveTo(down.position)
                    try {
                        while (true) {
                            val change = awaitPointerEvent().changes
                                .firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            moveTo(change.position)
                        }
                    } finally {
                        position = Offset.Zero
                        currentOnMove(0f, 0f)
                    }
                }
            },
    ) {
        val radius = this.size.minDimension / 2f
        val travel = radius * (1f - KNOB_FRACTION)

        drawCircle(colors.surfaceVariant, radius)
        drawCircle(colors.outline, radius - 0.5.dp.toPx(), style = Stroke(1.dp.toPx()))
        // A small cross marks the centre.
        val mark = radius * 0.08f
        drawLine(colors.outline, center - Offset(mark, 0f), center + Offset(mark, 0f), 1.dp.toPx())
        drawLine(colors.outline, center - Offset(0f, mark), center + Offset(0f, mark), 1.dp.toPx())

        drawCircle(
            color = colors.onSurfaceVariant,
            radius = radius * KNOB_FRACTION,
            center = center + position * travel,
        )
    }
}

/**
 * A D-pad: a cross whose arms light up while held. Sliding the thumb around it rolls between
 * directions, including the diagonals, as on a real D-pad.
 *
 * @param onChange the direction held, or null when let go.
 */
@Composable
fun Dpad(
    size: Dp,
    onChange: (DpadDirection?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnChange by rememberUpdatedState(onChange)
    var held by remember { mutableStateOf<DpadDirection?>(null) }
    val colors = MaterialTheme.colorScheme

    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "D-pad" }
            .pointerInput(Unit) {
                val radius = this.size.width / 2f

                fun hold(direction: DpadDirection?) {
                    if (direction == held) return
                    held = direction
                    currentOnChange(direction)
                }

                fun directionAt(touch: Offset): DpadDirection? {
                    val offset = touch - Offset(radius, radius)
                    if (hypot(offset.x, offset.y) < radius * DPAD_DEAD_ZONE) return null
                    // Angle clockwise from straight up, in eighths of a turn.
                    val angle = atan2(offset.x, -offset.y)
                    val eighth = ((angle / (2 * PI) * 8).roundToInt() + 8) % 8
                    return DpadDirection.entries[eighth]
                }

                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    hold(directionAt(down.position))
                    try {
                        while (true) {
                            val change = awaitPointerEvent().changes
                                .firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            hold(directionAt(change.position))
                        }
                    } finally {
                        hold(null)
                    }
                }
            },
    ) {
        val arm = this.size.width * DPAD_ARM_FRACTION
        val edge = (this.size.width - arm) / 2f
        val full = this.size.width
        val stroke = Stroke(1.dp.toPx())

        val tall = Size(arm, edge)
        val wide = Size(edge, arm)
        // Each arm: where it is, its size, and the held directions that light it up.
        val arms = listOf(
            Triple(Offset(edge, 0f), tall, UpDirections),
            Triple(Offset(full - edge, edge), wide, RightDirections),
            Triple(Offset(edge, full - edge), tall, DownDirections),
            Triple(Offset(0f, edge), wide, LeftDirections),
        )
        for ((topLeft, armSize, directions) in arms) {
            val isHeld = held in directions
            drawRect(if (isHeld) colors.outline else colors.surfaceVariant, topLeft, armSize)
            drawRect(colors.outline, topLeft, armSize, style = stroke)
        }
        // The middle square joins the arms.
        drawRect(colors.surfaceVariant, Offset(edge, edge), Size(arm, arm))
    }
}

// The D-pad directions that light up each arm: its own, and the diagonals either side.
private val UpDirections = setOf(DpadDirection.UP_LEFT, DpadDirection.UP, DpadDirection.UP_RIGHT)
private val RightDirections =
    setOf(DpadDirection.UP_RIGHT, DpadDirection.RIGHT, DpadDirection.DOWN_RIGHT)
private val DownDirections =
    setOf(DpadDirection.DOWN_RIGHT, DpadDirection.DOWN, DpadDirection.DOWN_LEFT)
private val LeftDirections = setOf(DpadDirection.DOWN_LEFT, DpadDirection.LEFT, DpadDirection.UP_LEFT)

/**
 * A button that holds its gamepad button down while touched.
 *
 * @param shape round for the face buttons, rounded rectangle for the others.
 */
@Composable
fun GamepadButtonPad(
    label: String,
    shape: Shape,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnPress by rememberUpdatedState(onPress)
    val currentOnRelease by rememberUpdatedState(onRelease)
    var isPressed by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .clip(shape)
            .background(if (isPressed) colors.outline else colors.surfaceVariant)
            .border(1.dp, colors.outline, shape)
            .semantics { role = Role.Button }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        currentOnPress()
                        try {
                            tryAwaitRelease()
                        } finally {
                            // Also runs if the page closes mid-press, so nothing stays held.
                            currentOnRelease()
                            isPressed = false
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
        )
    }
}

/**
 * A, B, X and Y in a diamond, as on an Xbox controller: Y on top, B on the right, A at the
 * bottom, X on the left.
 */
@Composable
fun FaceButtons(
    size: Dp,
    onPress: (GamepadButton) -> Unit,
    onRelease: (GamepadButton) -> Unit,
    modifier: Modifier = Modifier,
) {
    val buttonSize = size * 0.34f
    Box(modifier = modifier.size(size)) {
        for ((button, alignment) in FaceButtonPositions) {
            GamepadButtonPad(
                label = button.label,
                shape = CircleShape,
                onPress = { onPress(button) },
                onRelease = { onRelease(button) },
                modifier = Modifier
                    .align(alignment)
                    .size(buttonSize),
            )
        }
    }
}

private val FaceButtonPositions = listOf(
    GamepadButton.Y to Alignment.TopCenter,
    GamepadButton.B to Alignment.CenterEnd,
    GamepadButton.A to Alignment.BottomCenter,
    GamepadButton.X to Alignment.CenterStart,
)
