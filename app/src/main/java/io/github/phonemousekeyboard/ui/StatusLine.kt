package io.github.phonemousekeyboard.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.phonemousekeyboard.ui.theme.ActiveBlue
import io.github.phonemousekeyboard.ui.theme.ConnectedGreen
import io.github.phonemousekeyboard.ui.theme.IdleGrey
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

private val LineHeight = 2.dp

/**
 * The line is drawn as one smooth gradient through this many colour stops. Enough that the
 * ripples look smooth; a single gradient has no seams between pieces.
 */
private const val GRADIENT_STOPS = 61

/** Brightness at the very ends of the line, where it is dimmest. */
private const val EDGE_BRIGHTNESS = 0.12f

/** How sharply the brightness rises towards the centre. Higher = narrower glow. */
private const val GLOW_SHARPNESS = 1.6f

/**
 * Each point's brightness is raised to this power. Below 1 lights more of the line, while the
 * centre stays the brightest (1 would be the original, dimmer look).
 */
private const val BRIGHTNESS_POWER = 0.725f

/** While input is being sent: how many ripples fit across the line, and how fast they run. */
private const val RIPPLES_ACROSS_LINE = 5f
private const val RIPPLE_PERIOD_MILLIS = 900

/** Pause between ripple updates: about 30 updates a second, smooth but light on the battery. */
private const val RIPPLE_UPDATE_DELAY_MILLIS = 25L

private const val TRANSITION_MILLIS = 300

/**
 * The line under the header, which doubles as the connection indicator. It glows brightest in
 * the centre, like a sci-fi visor:
 *
 *  - grey:  not connected
 *  - green: connected
 *  - blue, with ripples of brightness running along it: input is being sent right now
 */
@Composable
fun StatusLine(
    isConnected: Boolean,
    isSendingInput: Boolean,
    modifier: Modifier = Modifier,
) {
    val isWaving = isConnected && isSendingInput

    val color by animateColorAsState(
        targetValue = when {
            isWaving -> ActiveBlue
            isConnected -> ConnectedGreen
            else -> IdleGrey
        },
        animationSpec = tween(TRANSITION_MILLIS),
        label = "status line colour",
    )

    // How much of the ripple effect is mixed in: 0 = still glow, 1 = full ripples.
    val waviness by animateFloatAsState(
        targetValue = if (isWaving) 1f else 0f,
        animationSpec = tween(TRANSITION_MILLIS),
        label = "status line waviness",
    )

    // The ripples' position along the line, 0 to 1. Only animated while they're visible, so
    // the line costs nothing when idle.
    var ripplePhase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isWaving) {
        while (isWaving) {
            withFrameMillis { frameMillis ->
                ripplePhase = (frameMillis % RIPPLE_PERIOD_MILLIS) / RIPPLE_PERIOD_MILLIS.toFloat()
            }
            delay(RIPPLE_UPDATE_DELAY_MILLIS)
        }
    }

    val description = when {
        isWaving -> "Connected, sending input"
        isConnected -> "Connected"
        else -> "Not connected"
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(LineHeight)
            // Its own layer, so the animation redraws only the line, not the header around it.
            .graphicsLayer()
            .semantics { contentDescription = description },
    ) {
        val colorStops = Array(GRADIENT_STOPS) { index ->
            val position = index / (GRADIENT_STOPS - 1f)
            position to color.copy(alpha = brightnessAt(position, ripplePhase, waviness))
        }
        drawRect(Brush.horizontalGradient(*colorStops))
    }
}

/**
 * Brightness (0–1) at [position] along the line (0 = left end, 1 = right end): a glow peaking
 * in the centre, optionally modulated by ripples travelling left to right.
 */
private fun brightnessAt(position: Float, ripplePhase: Float, waviness: Float): Float {
    val distanceFromCentre = abs(position - 0.5f) * 2f
    val glow = (1f - distanceFromCentre).pow(GLOW_SHARPNESS)

    val ripple = 0.5f + 0.5f * sin(2f * PI.toFloat() * (position * RIPPLES_ACROSS_LINE - ripplePhase))
    val rippleFactor = 1f - waviness + waviness * (0.35f + 0.65f * ripple)

    val brightness = EDGE_BRIGHTNESS + (1f - EDGE_BRIGHTNESS) * glow * rippleFactor
    return brightness.pow(BRIGHTNESS_POWER)
}
