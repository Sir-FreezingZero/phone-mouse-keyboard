package io.github.phonemousekeyboard.ui.media

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.phonemousekeyboard.input.MediaKey
import io.github.phonemousekeyboard.ui.icons.MediaIcons

private val ButtonShape = RoundedCornerShape(8.dp)
private val ButtonSpacing = 8.dp

/**
 * The Media page: the media keys of a keyboard, for whatever is playing on the computer.
 *
 *   PLAYBACK
 *   ──────────────────────────────
 *   [ ⏮ ]   [    ⏯ Play/pause   ]   [ ⏭ ]
 *   [ ⏪ ]   [       ■ Stop      ]   [ ⏩ ]
 *
 *   VOLUME
 *   ──────────────────────────────
 *   [ 🔉 ]   [      🔇 Mute      ]   [ 🔊 ]
 *
 * A button holds its key down for as long as it's touched, so holding a volume button keeps
 * changing the volume, as on a real keyboard.
 */
@Composable
fun MediaScreen(
    vibrateOnPress: Boolean,
    onPress: (MediaKey) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val press: (MediaKey) -> Unit = { key ->
        if (vibrateOnPress) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        onPress(key)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(ButtonSpacing),
    ) {
        SectionTitle("Playback")
        MediaButtonRow(
            left = MediaButtonSpec(MediaKey.PREVIOUS_TRACK, MediaIcons.Previous, "Previous"),
            middle = MediaButtonSpec(MediaKey.PLAY_PAUSE, MediaIcons.PlayPause, "Play/pause"),
            right = MediaButtonSpec(MediaKey.NEXT_TRACK, MediaIcons.Next, "Next"),
            height = 96.dp,
            onPress = press,
            onRelease = onRelease,
        )
        MediaButtonRow(
            left = MediaButtonSpec(MediaKey.REWIND, MediaIcons.Rewind, "Rewind"),
            middle = MediaButtonSpec(MediaKey.STOP, MediaIcons.Stop, "Stop"),
            right = MediaButtonSpec(MediaKey.FAST_FORWARD, MediaIcons.FastForward, "Fast forward"),
            height = 72.dp,
            onPress = press,
            onRelease = onRelease,
        )

        SectionTitle("Volume", modifier = Modifier.padding(top = 16.dp))
        MediaButtonRow(
            left = MediaButtonSpec(MediaKey.VOLUME_DOWN, MediaIcons.VolumeDown, "Volume down"),
            middle = MediaButtonSpec(MediaKey.MUTE, MediaIcons.Mute, "Mute"),
            right = MediaButtonSpec(MediaKey.VOLUME_UP, MediaIcons.VolumeUp, "Volume up"),
            height = 96.dp,
            onPress = press,
            onRelease = onRelease,
        )

        Text(
            text = "Rewind and fast forward work only in players that support those keys.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** A grey heading with a line under it, as on the Keyboard page. */
@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

/** One media button: the key it holds down, its icon, and its name (read by screen readers). */
private data class MediaButtonSpec(val key: MediaKey, val icon: ImageVector, val label: String)

/**
 * Three buttons in a row. The middle one is twice as wide and shows its name; the outer ones
 * show just their icon.
 */
@Composable
private fun MediaButtonRow(
    left: MediaButtonSpec,
    middle: MediaButtonSpec,
    right: MediaButtonSpec,
    height: Dp,
    onPress: (MediaKey) -> Unit,
    onRelease: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        horizontalArrangement = Arrangement.spacedBy(ButtonSpacing),
    ) {
        MediaButton(left, showLabel = false, onPress, onRelease, Modifier.weight(1f))
        MediaButton(middle, showLabel = true, onPress, onRelease, Modifier.weight(2f))
        MediaButton(right, showLabel = false, onPress, onRelease, Modifier.weight(1f))
    }
}

/** A bordered button that holds its media key down while touched. */
@Composable
private fun MediaButton(
    spec: MediaButtonSpec,
    showLabel: Boolean,
    onPress: (MediaKey) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnPress by rememberUpdatedState(onPress)
    val currentOnRelease by rememberUpdatedState(onRelease)
    var isPressed by remember { mutableStateOf(false) }

    val colors = MaterialTheme.colorScheme
    val background = if (isPressed) colors.outline else colors.surfaceVariant

    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(ButtonShape)
            .background(background)
            .border(1.dp, colors.outline, ButtonShape)
            .semantics { role = Role.Button }
            .pointerInput(spec.key) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        currentOnPress(spec.key)
                        try {
                            // Held until the finger lifts or slides off.
                            tryAwaitRelease()
                        } finally {
                            // Also runs if the page closes mid-press, so no key is left held.
                            currentOnRelease()
                            isPressed = false
                        }
                    },
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = spec.icon,
            contentDescription = spec.label,
            modifier = Modifier.size(28.dp),
        )
        if (showLabel) {
            Text(
                text = spec.label,
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
