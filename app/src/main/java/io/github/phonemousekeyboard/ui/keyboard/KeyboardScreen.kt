package io.github.phonemousekeyboard.ui.keyboard

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/** The text box keeps this height; longer text scrolls inside it. */
private val TextBoxHeight = 160.dp

/**
 * The "Keyboard" tab: a text box that mirrors every edit to the connected computer.
 *
 * Typing a letter types it on the computer; deleting a letter sends Backspace; autocorrect
 * replacing a word sends the Backspaces and new letters needed to make the same change.
 * Backspace keeps working when the box is empty, see [ANCHOR].
 *
 * @param pendingCharacters key presses still waiting to be sent, e.g. while a long paste is
 *   being typed out with a delay between characters.
 */
@Composable
fun KeyboardScreen(
    settings: KeyboardSettings,
    pendingCharacters: Int,
    onTextChange: (oldText: String, newText: String) -> Unit,
    onBackspace: () -> Unit,
    onStopSending: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(emptyTextBox())
    }
    val ages = remember { CharacterAges() }
    val focusRequester = remember { FocusRequester() }

    // Open the phone's keyboard as soon as this tab is shown.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val fade = FadeTiming(settings.fadeDelayMillis, settings.fadeDurationMillis)
    val nowMillis = rememberFadeClock(
        isRunning = settings.fadeOutText && value.content.isNotEmpty(),
        onTick = { now ->
            // Remove characters that have finished fading, but never part of a word the
            // phone keyboard is still composing.
            val composingFrom = value.composition?.let { it.min - ANCHOR.length }
                ?: value.content.length
            val faded = ages.fadedPrefixLength(now, fade).coerceAtMost(composingFrom)
            if (faded > 0) {
                value = value.removeOldestCharacters(faded)
                ages.dropFirst(faded)
            }
        },
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Text input",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TextBoxHeight),
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = { newValue ->
                    val oldText = value.content
                    // The anchor is gone when Backspace was pressed with nothing left to delete.
                    val anchorDeleted = !newValue.hasAnchor
                    val newText = if (anchorDeleted) newValue.text else newValue.content

                    when {
                        newText != oldText -> {
                            onTextChange(oldText, newText)
                            ages.onTextChanged(oldText, newText, SystemClock.uptimeMillis())
                        }
                        anchorDeleted -> onBackspace()
                    }

                    val pressedEnter = newText.length > oldText.length && newText.endsWith('\n')
                    value = if (settings.clearAfterEnter && pressedEnter) {
                        ages.reset(0, 0)
                        emptyTextBox()
                    } else {
                        newValue.withAnchor()
                    }
                },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = settings.phoneAutocorrect),
                visualTransformation = if (settings.fadeOutText) {
                    fadingTextTransformation(ages, nowMillis, fade, MaterialTheme.colorScheme.onSurface)
                } else {
                    VisualTransformation.None
                },
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester),
            )

            // The box always holds the invisible anchor, so the text field's own placeholder
            // would never show. This one appears whenever nothing has been typed.
            if (value.content.isEmpty()) {
                Text(
                    text = "Start typing…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        StatusRow(
            pendingCharacters = pendingCharacters,
            onStopSending = onStopSending,
            onClear = {
                ages.reset(0, 0)
                value = emptyTextBox()
            },
        )
    }
}

/**
 * The current time, updated every frame while [isRunning], so fading text can be redrawn.
 * [onTick] runs on every update. Costs nothing while stopped.
 */
@Composable
private fun rememberFadeClock(isRunning: Boolean, onTick: (nowMillis: Long) -> Unit): Long {
    var nowMillis by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    val currentOnTick by rememberUpdatedState(onTick)

    LaunchedEffect(isRunning) {
        while (isRunning) {
            withFrameMillis { }
            nowMillis = SystemClock.uptimeMillis()
            currentOnTick(nowMillis)
        }
    }
    return nowMillis
}

/** What's happening with the typed text, with Stop (while sending) and Clear. */
@Composable
private fun StatusRow(
    pendingCharacters: Int,
    onStopSending: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val isSending = pendingCharacters > 0
        Text(
            text = if (isSending) {
                "Sending… $pendingCharacters left"
            } else {
                "Text typed here is sent to the computer."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (isSending) {
            TextButton(onClick = onStopSending) { Text("Stop") }
        }
        // Clearing only resets the text box; nothing is deleted on the computer.
        TextButton(onClick = onClear) { Text("Clear") }
    }
}
