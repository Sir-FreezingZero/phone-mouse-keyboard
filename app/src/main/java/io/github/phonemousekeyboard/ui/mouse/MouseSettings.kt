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
    /** Scroll in small steps instead of whole wheel notches, where the computer supports it. */
    val smoothScrolling: Boolean = true,
    /** A quick one-finger tap on the trackpad left-clicks. */
    val tapToClick: Boolean = true,
    /** A quick two-finger tap on the trackpad right-clicks. */
    val twoFingerTapRightClick: Boolean = true,
    /** Show the Left/Right buttons under the trackpad. */
    val showButtons: Boolean = true,
    /** A short vibration on every click and button press. */
    val vibrateOnClick: Boolean = true,
    /** Glowing orbs under the fingers while they touch the trackpad. */
    val showTouchOrbs: Boolean = true,
) {
    /**
     * How many wheel notches a two-finger drag of [fingerDeltaY] phone pixels scrolls, taking
     * scroll speed and direction into account. Positive scrolls up.
     */
    fun scrollNotchesFor(fingerDeltaY: Float): Float {
        // Natural: dragging up (negative y) moves the content up, i.e. scrolls down
        // (negative). Classic: the opposite, like turning a wheel.
        val direction = if (naturalScrolling) 1f else -1f
        return direction * fingerDeltaY * scrollSpeed / PIXELS_PER_NOTCH
    }

    companion object {
        val SPEED_RANGE = 0.25f..3f

        /** Vertical finger travel, in phone pixels, that scrolls one notch at speed 1x. */
        private const val PIXELS_PER_NOTCH = 40f
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
                smoothScrolling = getBoolean(KEY_SMOOTH_SCROLLING, defaults.smoothScrolling),
                tapToClick = getBoolean(KEY_TAP_TO_CLICK, defaults.tapToClick),
                twoFingerTapRightClick = getBoolean(KEY_TWO_FINGER_TAP, defaults.twoFingerTapRightClick),
                showButtons = getBoolean(KEY_SHOW_BUTTONS, defaults.showButtons),
                vibrateOnClick = getBoolean(KEY_VIBRATE, defaults.vibrateOnClick),
                showTouchOrbs = getBoolean(KEY_TOUCH_ORBS, defaults.showTouchOrbs),
            )
        }
    }

    private fun save(settings: MouseSettings) {
        preferences.edit {
            putFloat(KEY_POINTER_SPEED, settings.pointerSpeed)
            putBoolean(KEY_POINTER_ACCELERATION, settings.pointerAcceleration)
            putFloat(KEY_SCROLL_SPEED, settings.scrollSpeed)
            putBoolean(KEY_NATURAL_SCROLLING, settings.naturalScrolling)
            putBoolean(KEY_SMOOTH_SCROLLING, settings.smoothScrolling)
            putBoolean(KEY_TAP_TO_CLICK, settings.tapToClick)
            putBoolean(KEY_TWO_FINGER_TAP, settings.twoFingerTapRightClick)
            putBoolean(KEY_SHOW_BUTTONS, settings.showButtons)
            putBoolean(KEY_VIBRATE, settings.vibrateOnClick)
            putBoolean(KEY_TOUCH_ORBS, settings.showTouchOrbs)
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
        const val KEY_SMOOTH_SCROLLING = "smooth_scrolling"
        const val KEY_TOUCH_ORBS = "show_touch_orbs"
    }
}
