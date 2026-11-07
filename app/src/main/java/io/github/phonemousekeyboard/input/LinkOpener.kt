package io.github.phonemousekeyboard.input

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where a link opens on the computer. */
enum class LinkTarget {
    /** Ctrl+T: a new tab in the browser window that's in front. */
    NEW_TAB,

    /** Ctrl+N: a new window of the browser that's in front. */
    NEW_WINDOW,

    /**
     * Windows key + R: the Run box, which opens the address in the default browser, starting
     * the browser if it isn't open. Windows only.
     */
    RUN_BOX,
}

/** The key that browser shortcuts are pressed with. */
enum class ShortcutKey(val modifier: Int) {
    /** Windows, Linux and ChromeOS. */
    CTRL(Modifier.LEFT_CTRL),

    /** The ⌘ Command key on a Mac. */
    COMMAND(Modifier.LEFT_GUI),
}

/**
 * Opens a web address on the computer using only the keyboard, so no software is needed
 * there: a shortcut opens a new tab, window or the Run box, which puts the cursor in the
 * address box; then the address is typed and Enter pressed.
 *
 * @param typeKey sends one key stroke to the computer.
 */
class LinkOpener(
    private val scope: CoroutineScope,
    private val typeKey: (KeyStroke) -> Unit,
) {
    private val _openingAddress = MutableStateFlow<String?>(null)

    /** The address being opened right now, or null. */
    val openingAddress: StateFlow<String?> = _openingAddress.asStateFlow()

    private var job: Job? = null

    /**
     * Opens [address]. Ignored while another link is still opening, so a double tap doesn't
     * type the address twice.
     *
     * @param waitMillis pause between the shortcut and typing, giving the new tab, window or
     *   Run box time to appear. Typing too early would land in the wrong place.
     */
    fun open(address: String, target: LinkTarget, shortcutKey: ShortcutKey, waitMillis: Long) {
        if (job?.isActive == true) return

        job = scope.launch {
            _openingAddress.value = address
            try {
                typeKey(shortcutFor(target, shortcutKey))
                delay(waitMillis)
                // Characters with no key on a US layout are skipped, as on the Keyboard page.
                address.forEach { char -> UsKeyboardLayout.keyStrokeFor(char)?.let(typeKey) }
                typeKey(SpecialKeys.ENTER)
            } finally {
                _openingAddress.value = null
            }
        }
    }

    private companion object {
        // HID usage IDs of the letter keys used in the shortcuts.
        const val KEY_N = 0x11
        const val KEY_R = 0x15
        const val KEY_T = 0x17

        fun shortcutFor(target: LinkTarget, shortcutKey: ShortcutKey): KeyStroke = when (target) {
            LinkTarget.NEW_TAB -> KeyStroke(KEY_T, shortcutKey.modifier)
            LinkTarget.NEW_WINDOW -> KeyStroke(KEY_N, shortcutKey.modifier)
            // The Run box is always the Windows key, whatever the shortcut key setting.
            LinkTarget.RUN_BOX -> KeyStroke(KEY_R, Modifier.LEFT_GUI)
        }
    }
}
