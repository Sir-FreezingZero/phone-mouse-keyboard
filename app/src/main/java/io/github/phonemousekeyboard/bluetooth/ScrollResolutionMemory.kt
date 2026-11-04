package io.github.phonemousekeyboard.bluetooth

import android.content.Context
import androidx.core.content.edit

/**
 * Remembers which computers have switched on high-resolution scrolling (the wheel's
 * "Resolution Multiplier", see [HidDescriptor]).
 *
 * A computer usually sets this once, when it first sets the mouse up, and then expects the
 * mouse to keep it. Forgetting it on reconnect would make scrolling 16 times too slow, so it
 * is stored per computer and kept across app restarts.
 */
class ScrollResolutionMemory(context: Context) {

    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun isHighResolution(hostAddress: String): Boolean = hostAddress in addresses()

    fun set(hostAddress: String, isHighResolution: Boolean) {
        val updated = if (isHighResolution) addresses() + hostAddress else addresses() - hostAddress
        preferences.edit { putStringSet(KEY_ADDRESSES, updated) }
    }

    /** Wheel units per notch the computer at [hostAddress] expects. */
    fun wheelUnitsPerNotch(hostAddress: String?): Int =
        if (hostAddress != null && isHighResolution(hostAddress)) {
            HidDescriptor.WHEEL_UNITS_PER_NOTCH
        } else {
            1
        }

    private fun addresses(): Set<String> =
        preferences.getStringSet(KEY_ADDRESSES, emptySet()).orEmpty()

    private companion object {
        const val PREFERENCES_NAME = "scroll_resolution"
        const val KEY_ADDRESSES = "high_resolution_hosts"
    }
}
