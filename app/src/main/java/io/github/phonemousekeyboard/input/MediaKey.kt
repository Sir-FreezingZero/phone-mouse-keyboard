package io.github.phonemousekeyboard.input

/**
 * Media keys, as on a keyboard's media row.
 *
 * @param usage the key's ID on the HID "Consumer" usage page, sent in the media report.
 *   Reference: USB HID Usage Tables, section 15 (Consumer Page).
 */
enum class MediaKey(val usage: Int) {
    PLAY_PAUSE(0xCD),
    STOP(0xB7),
    PREVIOUS_TRACK(0xB6),
    NEXT_TRACK(0xB5),
    REWIND(0xB4),
    FAST_FORWARD(0xB3),
    MUTE(0xE2),
    VOLUME_UP(0xE9),
    VOLUME_DOWN(0xEA),
}
