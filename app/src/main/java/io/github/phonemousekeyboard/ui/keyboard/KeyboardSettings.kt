package io.github.phonemousekeyboard.ui.keyboard

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How typed text is sent to the computer. Changed from the Keyboard tab's ⋮ menu. */
data class KeyboardSettings(
    /** Pause after every character sent, in milliseconds. */
    val characterDelayMillis: Int = 0,
    /**
     * Up to this many extra milliseconds, chosen at random for each character, added to
     * [characterDelayMillis]. Makes long pasted text arrive at an uneven, human-like pace.
     */
    val randomExtraDelayMillis: Int = 0,
    /** Let the phone's keyboard autocorrect and suggest words in the text box. */
    val phoneAutocorrect: Boolean = true,
    /** Empty the text box after Enter, so it doesn't fill up with sent text. */
    val clearAfterEnter: Boolean = false,
    /** Typed characters fade out of the text box after a while (they are already sent). */
    val fadeOutText: Boolean = true,
    /** How long a typed character stays fully visible before it starts to fade. */
    val fadeDelayMillis: Int = 1_500,
    /** How long the fade itself takes. */
    val fadeDurationMillis: Int = 800,
) {
    companion object {
        val DELAY_RANGE_MILLIS = 0..500
        val FADE_DELAY_RANGE_MILLIS = 0..10_000
        val FADE_DURATION_RANGE_MILLIS = 100..5_000
    }
}

/** Saves [KeyboardSettings] between launches. */
class KeyboardSettingsStore(context: Context) {

    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<KeyboardSettings> = _settings.asStateFlow()

    fun update(change: (KeyboardSettings) -> KeyboardSettings) {
        val updated = change(_settings.value)
        _settings.value = updated
        save(updated)
    }

    fun reset() = update { KeyboardSettings() }

    private fun load(): KeyboardSettings {
        val defaults = KeyboardSettings()
        return with(preferences) {
            KeyboardSettings(
                characterDelayMillis = getInt(KEY_CHARACTER_DELAY, defaults.characterDelayMillis),
                randomExtraDelayMillis = getInt(KEY_RANDOM_DELAY, defaults.randomExtraDelayMillis),
                phoneAutocorrect = getBoolean(KEY_AUTOCORRECT, defaults.phoneAutocorrect),
                clearAfterEnter = getBoolean(KEY_CLEAR_AFTER_ENTER, defaults.clearAfterEnter),
                fadeOutText = getBoolean(KEY_FADE_OUT, defaults.fadeOutText),
                fadeDelayMillis = getInt(KEY_FADE_DELAY, defaults.fadeDelayMillis),
                fadeDurationMillis = getInt(KEY_FADE_DURATION, defaults.fadeDurationMillis),
            )
        }
    }

    private fun save(settings: KeyboardSettings) {
        preferences.edit {
            putInt(KEY_CHARACTER_DELAY, settings.characterDelayMillis)
            putInt(KEY_RANDOM_DELAY, settings.randomExtraDelayMillis)
            putBoolean(KEY_AUTOCORRECT, settings.phoneAutocorrect)
            putBoolean(KEY_CLEAR_AFTER_ENTER, settings.clearAfterEnter)
            putBoolean(KEY_FADE_OUT, settings.fadeOutText)
            putInt(KEY_FADE_DELAY, settings.fadeDelayMillis)
            putInt(KEY_FADE_DURATION, settings.fadeDurationMillis)
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "keyboard_settings"
        const val KEY_CHARACTER_DELAY = "character_delay_millis"
        const val KEY_RANDOM_DELAY = "random_extra_delay_millis"
        const val KEY_AUTOCORRECT = "phone_autocorrect"
        const val KEY_CLEAR_AFTER_ENTER = "clear_after_enter"
        const val KEY_FADE_OUT = "fade_out_text"
        const val KEY_FADE_DELAY = "fade_delay_millis"
        const val KEY_FADE_DURATION = "fade_duration_millis"
    }
}
