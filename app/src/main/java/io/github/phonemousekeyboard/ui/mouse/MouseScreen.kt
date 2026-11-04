package io.github.phonemousekeyboard.ui.mouse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.phonemousekeyboard.input.MouseButton

/** Multiplier from phone-screen pixels to host pointer units. Raise to make the pointer faster. */
private const val POINTER_SPEED = 1.0f

/** Vertical finger travel, in phone pixels, that equals one scroll-wheel notch. */
private const val PIXELS_PER_SCROLL_NOTCH = 40f

private val PadShape = RoundedCornerShape(8.dp)

/**
 * The "Mouse" tab: a large trackpad with left and right buttons underneath.
 */
@Composable
fun MouseScreen(
    onMove: (dx: Int, dy: Int) -> Unit,
    onScroll: (notches: Int) -> Unit,
    onClick: (MouseButton) -> Unit,
    onButtonPress: (MouseButton) -> Unit,
    onButtonRelease: (MouseButton) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Trackpad(
            onMove = onMove,
            onScroll = onScroll,
            onClick = onClick,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MouseButtonPad(
                label = "Left",
                onPress = { onButtonPress(MouseButton.LEFT) },
                onRelease = { onButtonRelease(MouseButton.LEFT) },
                modifier = Modifier.weight(1f),
            )
            MouseButtonPad(
                label = "Right",
                onPress = { onButtonPress(MouseButton.RIGHT) },
                onRelease = { onButtonRelease(MouseButton.RIGHT) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Trackpad(
    onMove: (dx: Int, dy: Int) -> Unit,
    onScroll: (notches: Int) -> Unit,
    onClick: (MouseButton) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The gesture detector is created once, so always call the latest callbacks.
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnScroll by rememberUpdatedState(onScroll)
    val currentOnClick by rememberUpdatedState(onClick)

    val xAccumulator = remember { FractionalAccumulator() }
    val yAccumulator = remember { FractionalAccumulator() }
    val scrollAccumulator = remember { FractionalAccumulator() }

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, PadShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, PadShape)
            .trackpadGestures(
                onMove = { dx, dy ->
                    val stepX = xAccumulator.add(dx * POINTER_SPEED)
                    val stepY = yAccumulator.add(dy * POINTER_SPEED)
                    if (stepX != 0 || stepY != 0) currentOnMove(stepX, stepY)
                },
                onScroll = { dy ->
                    // Natural scrolling: dragging fingers up moves the content up.
                    val notches = scrollAccumulator.add(dy / PIXELS_PER_SCROLL_NOTCH)
                    if (notches != 0) currentOnScroll(notches)
                },
                onTap = { fingerCount ->
                    val button = if (fingerCount >= 2) MouseButton.RIGHT else MouseButton.LEFT
                    currentOnClick(button)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Trackpad\n\nTap to click · two-finger tap to right-click\nTwo-finger drag to scroll",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
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

    val background = if (isPressed) {
        MaterialTheme.colorScheme.outline
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(background, PadShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, PadShape)
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
