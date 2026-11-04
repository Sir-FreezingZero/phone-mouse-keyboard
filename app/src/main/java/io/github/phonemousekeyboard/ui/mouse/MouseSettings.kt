package io.github.phonemousekeyboard.ui.mouse

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How the trackpad and mouse buttons behave. Changed from the Mouse tab's ⋮ menu. */
data class MouseSettings(
    /** Multiplier from finger movement to pointer movement. */
    val pointerSpeed: Float = 1.0f,
    /** Fast finger movements move the pointer further, slow ones stay precise. */
    val pointerAcceleration: Boolean = false,
    /** Multiplier for how far a two-finger drag scrolls. */
    val scrollSpeed: Float = 1.0f,
    /** Content follows the fingers, as on phones and Mac trackpads. Off = classic wheel direction. */
    val naturalScrolling: Boolean = true,
    /** A quick one-finger tap on the trackpad left-clicks. */
    val tapToClick: Boolean = true,
    /** A quick two-finger tap on the trackpad right-clicks. */
    val twoFingerTapRightClick: Boolean = true,
    /** Show the Left/Right buttons under the trackpad. */
    val showButtons: Boolean = true,
    /** A short vibration on every click and button press. */
    val vibrateOnClick: Boolean = true,
) {
    companion object {
        val SPEED_RANGE = 0.25f..3f
    }
}

/** Saves [MouseSettings] between launches. */
class MouseSettingsStore(context: Context) {

    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<MouseSettings> = _settings.asStateFlow()

    fun update(change: (MouseSettings) -> MouseSettings) {
        val updated = change(_settings.value)
        _settings.value = updated
        save(updated)
    }

    fun reset() = update { MouseSettings() }

    private fun load(): MouseSettings {
        val defaults = MouseSettings()
        return with(preferences) {
            MouseSettings(
                pointerSpeed = getFloat(KEY_POINTER_SPEED, defaults.pointerSpeed),
                pointerAcceleration = getBoolean(KEY_POINTER_ACCELERATION, defaults.pointerAcceleration),
                scrollSpeed = getFloat(KEY_SCROLL_SPEED, defaults.scrollSpeed),
                naturalScrolling = getBoolean(KEY_NATURAL_SCROLLING, defaults.naturalScrolling),
                tapToClick = getBoolean(KEY_TAP_TO_CLICK, defaults.tapToClick),
                twoFingerTapRightClick = getBoolean(KEY_TWO_FINGER_TAP, defaults.twoFingerTapRightClick),
                showButtons = getBoolean(KEY_SHOW_BUTTONS, defaults.showButtons),
                vibrateOnClick = getBoolean(KEY_VIBRATE, defaults.vibrateOnClick),
            )
        }
    }

    private fun save(settings: MouseSettings) {
        preferences.edit {
            putFloat(KEY_POINTER_SPEED, settings.pointerSpeed)
            putBoolean(KEY_POINTER_ACCELERATION, settings.pointerAcceleration)
            putFloat(KEY_SCROLL_SPEED, settings.scrollSpeed)
            putBoolean(KEY_NATURAL_SCROLLING, settings.naturalScrolling)
            putBoolean(KEY_TAP_TO_CLICK, settings.tapToClick)
            putBoolean(KEY_TWO_FINGER_TAP, settings.twoFingerTapRightClick)
            putBoolean(KEY_SHOW_BUTTONS, settings.showButtons)
            putBoolean(KEY_VIBRATE, settings.vibrateOnClick)
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "mouse_settings"
        const val KEY_POINTER_SPEED = "pointer_speed"
        const val KEY_POINTER_ACCELERATION = "pointer_acceleration"
        const val KEY_SCROLL_SPEED = "scroll_speed"
        const val KEY_NATURAL_SCROLLING = "natural_scrolling"
        const val KEY_TAP_TO_CLICK = "tap_to_click"
        const val KEY_TWO_FINGER_TAP = "two_finger_tap_right_click"
        const val KEY_SHOW_BUTTONS = "show_buttons"
        const val KEY_VIBRATE = "vibrate_on_click"
    }
}
