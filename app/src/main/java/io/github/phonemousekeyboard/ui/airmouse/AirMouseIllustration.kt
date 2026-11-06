package io.github.phonemousekeyboard.ui.airmouse

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import io.github.phonemousekeyboard.input.AirMotion
import io.github.phonemousekeyboard.ui.theme.ActiveBlue

/**
 * Size of the imaginary computer screen the illustration shows, in pointer units. Movement
 * sent to the computer is applied to it 1:1, so the picture moves the way a pointer on a
 * typical 1080p screen would.
 */
private const val VIRTUAL_WIDTH = 1920f
private const val VIRTUAL_HEIGHT = 1080f

/** How many recent cursor positions the trail shows (about a second of movement). */
private const val TRAIL_LENGTH = 45

/**
 * What the Air mouse illustration shows: where the pointer would be on an imaginary screen,
 * where it has just been, and the latest sensor readings.
 */
class AirMouseIllustrationState {

    var cursor by mutableStateOf(Offset(VIRTUAL_WIDTH / 2, VIRTUAL_HEIGHT / 2))
        private set

    /** Recent cursor positions, oldest first. */
    val trail = mutableStateListOf<Offset>()

    /** Latest turn and tilt rates, in degrees per second. */
    var turnDegreesPerSecond by mutableFloatStateOf(0f)
        private set
    var tiltDegreesPerSecond by mutableFloatStateOf(0f)
        private set

    fun onMotion(motion: AirMotion) {
        cursor = Offset(
            (cursor.x + motion.dx).coerceIn(0f, VIRTUAL_WIDTH),
            (cursor.y + motion.dy).coerceIn(0f, VIRTUAL_HEIGHT),
        )
        trail += cursor
        while (trail.size > TRAIL_LENGTH) trail.removeAt(0)

        turnDegreesPerSecond = Math.toDegrees(motion.turnRate.toDouble()).toFloat()
        tiltDegreesPerSecond = Math.toDegrees(motion.tiltRate.toDouble()).toFloat()
    }

    /** Clears the trail and readings, e.g. when the Air mouse is paused. */
    fun settle() {
        trail.clear()
        turnDegreesPerSecond = 0f
        tiltDegreesPerSecond = 0f
    }
}

/**
 * A small picture of a computer screen with the pointer moving on it, mirroring what the Air
 * mouse is sending. Drawn dimmed while the Air mouse isn't moving the pointer.
 */
@Composable
fun AirMouseIllustration(
    state: AirMouseIllustrationState,
    isActive: Boolean,
    frameColor: Color,
    cursorColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val screen = screenRect()
        val opacity = if (isActive) 1f else 0.4f

        // The screen's frame.
        drawRoundRect(
            color = frameColor.copy(alpha = opacity),
            topLeft = screen.topLeft,
            size = screen.size,
            cornerRadius = CornerRadius(6.dp.toPx()),
            style = Stroke(width = 1.5.dp.toPx()),
        )

        fun toCanvas(point: Offset) = Offset(
            screen.topLeft.x + point.x / VIRTUAL_WIDTH * screen.size.width,
            screen.topLeft.y + point.y / VIRTUAL_HEIGHT * screen.size.height,
        )

        // The trail: older segments fainter.
        val points = state.trail.map(::toCanvas)
        for (index in 1 until points.size) {
            val age = index.toFloat() / points.size
            drawLine(
                color = ActiveBlue.copy(alpha = age * 0.8f * opacity),
                start = points[index - 1],
                end = points[index],
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        drawCursor(at = toCanvas(state.cursor), color = cursorColor.copy(alpha = opacity))
    }
}

private class ScreenRect(val topLeft: Offset, val size: Size)

/** The largest 16:9 rectangle that fits, with some margin, centred. */
private fun DrawScope.screenRect(): ScreenRect {
    val margin = 24.dp.toPx()
    val availableWidth = size.width - 2 * margin
    val availableHeight = size.height - 2 * margin
    val aspect = VIRTUAL_WIDTH / VIRTUAL_HEIGHT
    val width = minOf(availableWidth, availableHeight * aspect)
    val height = width / aspect
    return ScreenRect(
        topLeft = Offset((size.width - width) / 2, (size.height - height) / 2),
        size = Size(width, height),
    )
}

/** A classic arrow pointer with its tip at [at]. */
private fun DrawScope.drawCursor(at: Offset, color: Color) {
    val unit = 1.dp.toPx()
    val arrow = Path().apply {
        moveTo(0f, 0f)
        lineTo(0f, 16f * unit)
        lineTo(4f * unit, 12.5f * unit)
        lineTo(7f * unit, 18.5f * unit)
        lineTo(9.5f * unit, 17.5f * unit)
        lineTo(6.5f * unit, 11.5f * unit)
        lineTo(11.5f * unit, 11.5f * unit)
        close()
    }
    translate(left = at.x, top = at.y) {
        drawPath(arrow, color = color)
    }
}
