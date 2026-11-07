package io.github.phonemousekeyboard.ui.mouse

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.github.phonemousekeyboard.input.MouseButton

/**
 * Left and Right mouse buttons side by side, split by a line. Meant to sit at the bottom of a
 * bordered unit (the trackpad, the Air mouse view), which draws the outer edges.
 *
 * Each button stays pressed for as long as the finger is down, so click-and-drag works.
 */
@Composable
fun MouseButtonRow(
    onPress: (MouseButton) -> Unit,
    onRelease: (MouseButton) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
    ) {
        MouseButtonPad(
            label = "Left",
            onPress = { onPress(MouseButton.LEFT) },
            onRelease = { onRelease(MouseButton.LEFT) },
            modifier = Modifier.weight(1f),
        )
        VerticalDivider(color = MaterialTheme.colorScheme.outline)
        MouseButtonPad(
            label = "Right",
            onPress = { onPress(MouseButton.RIGHT) },
            onRelease = { onRelease(MouseButton.RIGHT) },
            modifier = Modifier.weight(1f),
        )
    }
}

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
