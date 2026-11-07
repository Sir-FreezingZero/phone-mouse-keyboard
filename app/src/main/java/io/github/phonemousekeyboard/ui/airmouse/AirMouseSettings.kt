package io.github.phonemousekeyboard.ui.airmouse

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How the user moves the phone to move the pointer. */
enum class AirMouseStyle(val label: String) {
    /** Aim the phone at the screen; turn and tilt it like a TV remote. */
    POINT("Point"),

    /** Tip the phone like a joystick; the further it's tipped, the faster the pointer moves. */
    TILT("Tilt"),
}

/** How the Air mouse turns phone movement into pointer movement. */
data class AirMouseSettings(
    /**
     * The chosen style, or null to pick automatically: Point on phones with a gyroscope,
     * Tilt on phones without one.
     */
    val style: AirMouseStyle? = null,
    /** Multiplier from phone rotation to pointer movement. */
    val speed: Float = 1.0f,
    /**
     * How much small, slow movement (hand tremor, sensor drift) is ignored, from 0 (none) to 1
     * (a lot). Higher keeps the pointer still while holding the phone still.
     */
    val steadiness: Float = 0.3f,
    /** How much movement is smoothed, from 0 (raw) to 1 (very smooth, slightly laggy). */
    val smoothing: Float = 0.3f,
    val invertHorizontal: Boolean = false,
    val invertVertical: Boolean = false,
    /** Only move the pointer while a finger is on the page, like holding a laser pointer's button. */
    val moveOnlyWhileTouching: Boolean = false,
) {
    companion object {
        val SPEED_RANGE = 0.25f..3f
    }
}

/** Saves [AirMouseSettings] between launches. */
class AirMouseSettingsStore(context: Context) {

    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AirMouseSettings> = _settings.asStateFlow()

    fun update(change: (AirMouseSettings) -> AirMouseSettings) {
        val updated = change(_settings.value)
        _settings.value = updated
        save(updated)
    }

    fun reset() = update { AirMouseSettings() }

    private fun load(): AirMouseSettings {
        val defaults = AirMouseSettings()
        return with(preferences) {
            AirMouseSettings(
                style = getString(KEY_STYLE, null)
                    ?.let { saved -> AirMouseStyle.entries.firstOrNull { it.name == saved } },
                speed = getFloat(KEY_SPEED, defaults.speed),
                steadiness = getFloat(KEY_STEADINESS, defaults.steadiness),
                smoothing = getFloat(KEY_SMOOTHING, defaults.smoothing),
                invertHorizontal = getBoolean(KEY_INVERT_HORIZONTAL, defaults.invertHorizontal),
                invertVertical = getBoolean(KEY_INVERT_VERTICAL, defaults.invertVertical),
                moveOnlyWhileTouching = getBoolean(KEY_WHILE_TOUCHING, defaults.moveOnlyWhileTouching),
            )
        }
    }

    private fun save(settings: AirMouseSettings) {
        preferences.edit {
            putString(KEY_STYLE, settings.style?.name)
            putFloat(KEY_SPEED, settings.speed)
            putFloat(KEY_STEADINESS, settings.steadiness)
            putFloat(KEY_SMOOTHING, settings.smoothing)
            putBoolean(KEY_INVERT_HORIZONTAL, settings.invertHorizontal)
            putBoolean(KEY_INVERT_VERTICAL, settings.invertVertical)
            putBoolean(KEY_WHILE_TOUCHING, settings.moveOnlyWhileTouching)
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "air_mouse_settings"
        const val KEY_STYLE = "style"
        const val KEY_SPEED = "speed"
        const val KEY_STEADINESS = "steadiness"
        const val KEY_SMOOTHING = "smoothing"
        const val KEY_INVERT_HORIZONTAL = "invert_horizontal"
        const val KEY_INVERT_VERTICAL = "invert_vertical"
        const val KEY_WHILE_TOUCHING = "move_only_while_touching"
    }
}

/** The style in use: the user's choice, or the automatic pick for this phone. */
fun AirMouseSettings.effectiveStyle(hasGyroscope: Boolean): AirMouseStyle =
    style ?: if (hasGyroscope) AirMouseStyle.POINT else AirMouseStyle.TILT
