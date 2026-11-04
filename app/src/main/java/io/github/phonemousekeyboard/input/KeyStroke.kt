package io.github.phonemousekeyboard.input

/**
 * A single key press as the host computer understands it.
 *
 * @param keyCode HID usage ID from the "Keyboard/Keypad" usage page (e.g. 0x04 = A).
 * @param modifiers Bit field of held modifier keys, see [Modifier].
 */
data class KeyStroke(val keyCode: Int, val modifiers: Int = Modifier.NONE)

/** Bits of the HID keyboard modifier byte. */
object Modifier {
    const val NONE = 0
    const val LEFT_CTRL = 1 shl 0
    const val LEFT_SHIFT = 1 shl 1
    const val LEFT_ALT = 1 shl 2
    const val LEFT_GUI = 1 shl 3
}

/** Frequently used non-printing keys. */
object SpecialKeys {
    val ENTER = KeyStroke(0x28)
    val BACKSPACE = KeyStroke(0x2A)
    val TAB = KeyStroke(0x2B)
}
