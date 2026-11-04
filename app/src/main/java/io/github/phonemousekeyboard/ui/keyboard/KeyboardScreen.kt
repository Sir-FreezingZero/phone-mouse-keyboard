package io.github.phonemousekeyboard.ui.keyboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp

/**
 * The "Keyboard" tab: a text box that mirrors every edit to the connected computer.
 *
 * Typing a letter types it on the computer; deleting a letter sends Backspace; autocorrect
 * replacing a word sends the Backspaces and new letters needed to make the same change.
 *
 * @param pendingCharacters key presses still waiting to be sent, e.g. while a long paste is
 *   being typed out with a delay between characters.
 */
@Composable
fun KeyboardScreen(
    settings: KeyboardSettings,
    pendingCharacters: Int,
    onTextChange: (oldText: String, newText: String) -> Unit,
    onStopSending: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    // Open the phone's keyboard as soon as this tab is shown.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
            // Clearing only resets this box; nothing is deleted on the computer.
            TextButton(onClick = { text = "" }) { Text("Clear") }
        }

        OutlinedTextField(
            value = text,
            onValueChange = { newText ->
                onTextChange(text, newText)
                val pressedEnter = newText.length > text.length && newText.endsWith('\n')
                text = if (settings.clearAfterEnter && pressedEnter) "" else newText
            },
            placeholder = { Text("Start typing…") },
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = settings.phoneAutocorrect),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .focusRequester(focusRequester),
        )
    }
}
