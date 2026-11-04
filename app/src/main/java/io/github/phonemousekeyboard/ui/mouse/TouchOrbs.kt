package io.github.phonemousekeyboard.ui.mouse

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.phonemousekeyboard.ui.theme.ActiveBlue

/** Radius of the soft glow around each finger, and of the brighter core at its centre. */
private val GlowRadius = 44.dp
private val CoreRadius = 10.dp

private const val APPEAR_MILLIS = 120
private const val FADE_MILLIS = 400

/**
 * Keeps track of the orbs to draw: one per finger on the trackpad, plus orbs of lifted
 * fingers that are still fading out where the finger left.
 */
class TouchOrbState {

    /** Last known position of every orb, including ones that are fading out. */
    internal val positions = mutableStateMapOf<Long, Offset>()

    /** IDs of fingers currently touching. */
    internal var touching by mutableStateOf(emptySet<Long>())
        private set

    fun update(touches: Map<Long, Offset>) {
        positions.putAll(touches)
        touching = touches.keys
    }

    internal fun remove(id: Long) {
        positions.remove(id)
    }
}

@Composable
fun rememberTouchOrbState(): TouchOrbState = remember { TouchOrbState() }

/** Glowing orbs under the fingers. They appear quickly and fade out after a finger lifts. */
@Composable
fun TouchOrbs(state: TouchOrbState, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        for ((id, position) in state.positions) {
            key(id) {
                val isTouching = id in state.touching
                val visibility by animateFloatAsState(
                    targetValue = if (isTouching) 1f else 0f,
                    animationSpec = tween(if (isTouching) APPEAR_MILLIS else FADE_MILLIS),
                    label = "touch orb",
                    finishedListener = { finalValue -> if (finalValue == 0f) state.remove(id) },
                )
                Orb(position = position, visibility = visibility)
            }
        }
    }
}

@Composable
private fun Orb(position: Offset, visibility: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val glowRadius = GlowRadius.toPx()
        val coreRadius = CoreRadius.toPx()

        drawCircle(
            brush = Brush.radialGradient(
                0f to ActiveBlue.copy(alpha = 0.45f * visibility),
                1f to Color.Transparent,
                center = position,
                radius = glowRadius,
            ),
            radius = glowRadius,
            center = position,
        )
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color.White.copy(alpha = 0.55f * visibility),
                1f to Color.Transparent,
                center = position,
                radius = coreRadius,
            ),
            radius = coreRadius,
            center = position,
        )
    }
}
