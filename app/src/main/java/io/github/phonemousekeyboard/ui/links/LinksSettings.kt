package io.github.phonemousekeyboard.ui.links

import android.content.Context
import androidx.core.content.edit
import io.github.phonemousekeyboard.input.LinkTarget
import io.github.phonemousekeyboard.input.ShortcutKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How links are opened on the computer. Changed from the Links page's ⋮ menu. */
data class LinksSettings(
    /**
     * The default browser (via the Windows Run box, from any program), or a new tab or window
     * of the browser in front.
     */
    val target: LinkTarget = LinkTarget.DEFAULT_BROWSER,
    /** Ctrl, or ⌘ Command for a Mac. */
    val shortcutKey: ShortcutKey = ShortcutKey.CTRL,
    /** How long to wait for the new tab or window to appear before typing the address. */
    val waitMillis: Int = 500,
) {
    companion object {
        val WAIT_RANGE_MILLIS = 100..3_000
    }
}

/** Saves [LinksSettings] between launches. */
class LinksSettingsStore(context: Context) {

    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<LinksSettings> = _settings.asStateFlow()

    fun update(change: (LinksSettings) -> LinksSettings) {
        val updated = change(_settings.value)
        _settings.value = updated
        save(updated)
    }

    fun reset() = update { LinksSettings() }

    private fun load(): LinksSettings {
        val defaults = LinksSettings()
        return with(preferences) {
            LinksSettings(
                // "RUN_BOX" was the default browser option's earlier name.
                target = enumOrDefault(
                    getString(KEY_TARGET, null)?.replace("RUN_BOX", LinkTarget.DEFAULT_BROWSER.name),
                    defaults.target,
                ),
                shortcutKey = enumOrDefault(getString(KEY_SHORTCUT_KEY, null), defaults.shortcutKey),
                waitMillis = getInt(KEY_WAIT, defaults.waitMillis),
            )
        }
    }

    private fun save(settings: LinksSettings) {
        preferences.edit {
            putString(KEY_TARGET, settings.target.name)
            putString(KEY_SHORTCUT_KEY, settings.shortcutKey.name)
            putInt(KEY_WAIT, settings.waitMillis)
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "links_settings"
        const val KEY_TARGET = "target"
        const val KEY_SHORTCUT_KEY = "shortcut_key"
        const val KEY_WAIT = "wait_millis"

        /** The enum value saved as [name], or [default] if there's none or it's unknown. */
        inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
            enumValues<T>().firstOrNull { it.name == name } ?: default
    }
}
