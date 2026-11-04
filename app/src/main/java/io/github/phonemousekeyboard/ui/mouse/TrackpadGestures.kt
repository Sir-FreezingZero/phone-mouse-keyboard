package io.github.phonemousekeyboard.ui.mouse

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange

/** A touch shorter than this (and without real movement) counts as a tap. */
private const val TAP_TIMEOUT_MILLIS = 250L

/**
 * Laptop-style trackpad gestures:
 *
 *  - one finger drag  → [onMove] with the finger's movement in pixels
 *  - two finger drag  → [onScroll] with the vertical movement in pixels
 *  - one finger tap   → [onTap] with `1`
 *  - two finger tap   → [onTap] with `2`
 *
 * [onTouchesChanged] receives every finger's position, keyed by pointer ID, whenever fingers
 * move, land or lift; an empty map once all have lifted.
 *
 * Once a second finger touches down, the gesture stays a scroll/two-finger gesture until all
 * fingers lift, so the pointer doesn't jump when fingers are lifted one at a time.
 */
fun Modifier.trackpadGestures(
    onMove: (dx: Float, dy: Float) -> Unit,
    onScroll: (dy: Float) -> Unit,
    onTap: (fingerCount: Int) -> Unit,
    onTouchesChanged: (touches: Map<Long, Offset>) -> Unit = {},
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val firstDown = awaitFirstDown(requireUnconsumed = false)
        val startTime = firstDown.uptimeMillis
        var endTime = startTime
        var maxFingers = 1
        var totalTravel = 0f

        do {
            val event = awaitPointerEvent()
            val fingersDown = event.changes.filter { it.pressed }
            maxFingers = maxOf(maxFingers, fingersDown.size)
            onTouchesChanged(fingersDown.associate { it.id.value to it.position })

            if (fingersDown.isNotEmpty()) {
                val averageMovement = fingersDown
                    .map { it.positionChange() }
                    .fold(Offset.Zero, Offset::plus) / fingersDown.size.toFloat()
                totalTravel += averageMovement.getDistance()

                if (maxFingers == 1) {
                    onMove(averageMovement.x, averageMovement.y)
                } else if (fingersDown.size >= 2) {
                    onScroll(averageMovement.y)
                }
            }

            event.changes.forEach { it.consume() }
            endTime = event.changes.maxOf { it.uptimeMillis }
        } while (event.changes.any { it.pressed })

        val wasQuick = endTime - startTime < TAP_TIMEOUT_MILLIS
        val barelyMoved = totalTravel < viewConfiguration.touchSlop
        if (wasQuick && barelyMoved) {
            onTap(maxFingers)
        }
    }
}

/**
 * Turns fractional movement into whole steps while carrying the leftover fraction forward,
 * so slow, precise finger movements still move the pointer instead of rounding to zero.
 */
class FractionalAccumulator {
    private var remainder = 0f

    fun add(value: Float): Int {
        remainder += value
        val whole = remainder.toInt()
        remainder -= whole
        return whole
    }
}
