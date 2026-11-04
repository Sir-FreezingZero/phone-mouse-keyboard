package io.github.phonemousekeyboard.input

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Tracks whether input is being sent right now, for the header's status line.
 *
 * Call [ping] whenever input is sent. [isActive] turns true straight away and back to false
 * once no input has been sent for [holdMillis], so the indicator doesn't flicker between
 * individual key presses or pointer movements.
 */
class InputActivity(
    private val scope: CoroutineScope,
    private val holdMillis: Long = DEFAULT_HOLD_MILLIS,
) {
    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private var lastInputAtMillis = 0L

    fun ping() {
        lastInputAtMillis = SystemClock.uptimeMillis()
        if (_isActive.value) return

        _isActive.value = true
        scope.launch {
            // Keep waiting while input keeps arriving; stop once it has been quiet long enough.
            while (true) {
                val quietFor = SystemClock.uptimeMillis() - lastInputAtMillis
                if (quietFor >= holdMillis) break
                delay(holdMillis - quietFor)
            }
            _isActive.value = false
        }
    }

    private companion object {
        const val DEFAULT_HOLD_MILLIS = 400L
    }
}
