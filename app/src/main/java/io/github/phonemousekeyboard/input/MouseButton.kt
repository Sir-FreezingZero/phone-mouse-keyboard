package io.github.phonemousekeyboard.input

/** Mouse buttons and their bit position in the HID mouse report. */
enum class MouseButton(val bit: Int) {
    LEFT(0b001),
    RIGHT(0b010),
    MIDDLE(0b100),
}
