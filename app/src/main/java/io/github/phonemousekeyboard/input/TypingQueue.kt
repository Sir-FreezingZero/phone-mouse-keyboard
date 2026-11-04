package io.github.phonemousekeyboard.input

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Types key strokes one after another, pausing between them.
 *
 * Without a pause everything is sent at once, as before. With one, a long paste is typed out
 * gradually; [pending] counts what's left and [cancel] drops it. New typing always queues
 * behind what's already waiting, so characters never arrive out of order.
 *
 * @param type sends one key stroke to the computer.
 * @param pauseAfterKeyMillis how long to wait after each key stroke, asked fresh every time
 *   so setting changes (and random variation) apply straight away.
 */
class TypingQueue(
    scope: CoroutineScope,
    private val type: (KeyStroke) -> Unit,
    private val pauseAfterKeyMillis: () -> Long,
) {
    private val keys = Channel<KeyStroke>(Channel.UNLIMITED)

    private val _pending = MutableStateFlow(0)

    /** Key strokes waiting to be typed. */
    val pending: StateFlow<Int> = _pending.asStateFlow()

    init {
        scope.launch {
            for (key in keys) {
                type(key)
                _pending.update { it - 1 }

                val pause = pauseAfterKeyMillis()
                if (pause > 0) delay(pause)
            }
        }
    }

    fun add(key: KeyStroke) {
        _pending.update { it + 1 }
        keys.trySend(key)
    }

    /** Drops every key stroke that hasn't been typed yet. */
    fun cancel() {
        while (keys.tryReceive().isSuccess) {
            _pending.update { it - 1 }
        }
    }
}
