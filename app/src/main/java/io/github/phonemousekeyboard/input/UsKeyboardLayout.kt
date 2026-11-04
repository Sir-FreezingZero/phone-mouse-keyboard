package io.github.phonemousekeyboard.input

/**
 * Converts characters to key strokes assuming the host computer uses a US keyboard layout.
 *
 * HID keyboards send *physical key positions*, not characters, so the host interprets them
 * using its own layout setting. Supporting other layouts means adding another mapping like this.
 */
object UsKeyboardLayout {

    /** Returns the key stroke that types [char], or null if it cannot be typed on a US keyboard. */
    fun keyStrokeFor(char: Char): KeyStroke? = when (char) {
        in 'a'..'z' -> KeyStroke(KEY_A + (char - 'a'))
        in 'A'..'Z' -> KeyStroke(KEY_A + (char - 'A'), Modifier.LEFT_SHIFT)
        in '1'..'9' -> KeyStroke(KEY_1 + (char - '1'))
        '0' -> KeyStroke(KEY_0)
        '\n' -> SpecialKeys.ENTER
        '\t' -> SpecialKeys.TAB
        else -> SYMBOLS[char]
    }

    private const val KEY_A = 0x04
    private const val KEY_1 = 0x1E
    private const val KEY_0 = 0x27

    private const val SHIFT = Modifier.LEFT_SHIFT

    private val SYMBOLS: Map<Char, KeyStroke> = mapOf(
        ' ' to KeyStroke(0x2C),

        // Symbols on the number row (Shift + digit).
        '!' to KeyStroke(0x1E, SHIFT),
        '@' to KeyStroke(0x1F, SHIFT),
        '#' to KeyStroke(0x20, SHIFT),
        '$' to KeyStroke(0x21, SHIFT),
        '%' to KeyStroke(0x22, SHIFT),
        '^' to KeyStroke(0x23, SHIFT),
        '&' to KeyStroke(0x24, SHIFT),
        '*' to KeyStroke(0x25, SHIFT),
        '(' to KeyStroke(0x26, SHIFT),
        ')' to KeyStroke(0x27, SHIFT),

        // Punctuation keys, unshifted and shifted.
        '-' to KeyStroke(0x2D),
        '_' to KeyStroke(0x2D, SHIFT),
        '=' to KeyStroke(0x2E),
        '+' to KeyStroke(0x2E, SHIFT),
        '[' to KeyStroke(0x2F),
        '{' to KeyStroke(0x2F, SHIFT),
        ']' to KeyStroke(0x30),
        '}' to KeyStroke(0x30, SHIFT),
        '\\' to KeyStroke(0x31),
        '|' to KeyStroke(0x31, SHIFT),
        ';' to KeyStroke(0x33),
        ':' to KeyStroke(0x33, SHIFT),
        '\'' to KeyStroke(0x34),
        '"' to KeyStroke(0x34, SHIFT),
        '`' to KeyStroke(0x35),
        '~' to KeyStroke(0x35, SHIFT),
        ',' to KeyStroke(0x36),
        '<' to KeyStroke(0x36, SHIFT),
        '.' to KeyStroke(0x37),
        '>' to KeyStroke(0x37, SHIFT),
        '/' to KeyStroke(0x38),
        '?' to KeyStroke(0x38, SHIFT),

        // Phone keyboards often auto-insert "smart" punctuation; type the plain equivalent.
        '‘' to KeyStroke(0x34),          // ‘
        '’' to KeyStroke(0x34),          // ’
        '“' to KeyStroke(0x34, SHIFT),   // “
        '”' to KeyStroke(0x34, SHIFT),   // ”
    )
}
