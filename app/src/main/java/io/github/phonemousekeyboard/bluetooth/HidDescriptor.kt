package io.github.phonemousekeyboard.bluetooth

/**
 * The HID report descriptor that tells the host computer what kind of device we are.
 *
 * It describes a composite device with two reports:
 *
 *  - Report [KEYBOARD_REPORT_ID]: a standard boot-style keyboard
 *      byte 0     modifier bit field (Ctrl, Shift, Alt, GUI — left and right)
 *      byte 1     reserved
 *      bytes 2-7  up to six simultaneously pressed key codes
 *
 *  - Report [MOUSE_REPORT_ID]: a three-button mouse with a scroll wheel
 *      byte 0     button bit field (bit 0 = left, bit 1 = right, bit 2 = middle)
 *      byte 1     X movement, signed (-127..127)
 *      byte 2     Y movement, signed (-127..127)
 *      byte 3     vertical wheel, signed (-127..127)
 *
 * Reference: USB HID Usage Tables, https://usb.org/document-library/hid-usage-tables-15
 */
object HidDescriptor {

    const val KEYBOARD_REPORT_ID = 1
    const val MOUSE_REPORT_ID = 2

    const val KEYBOARD_REPORT_SIZE = 8
    const val MOUSE_REPORT_SIZE = 4

    val REPORT_MAP: ByteArray = bytesOf(
        // ---- Keyboard ----
        0x05, 0x01,                     // Usage Page (Generic Desktop)
        0x09, 0x06,                     // Usage (Keyboard)
        0xA1, 0x01,                     // Collection (Application)
        0x85, KEYBOARD_REPORT_ID,       //   Report ID
        0x05, 0x07,                     //   Usage Page (Keyboard/Keypad)
        0x19, 0xE0,                     //   Usage Minimum (Left Control)
        0x29, 0xE7,                     //   Usage Maximum (Right GUI)
        0x15, 0x00,                     //   Logical Minimum (0)
        0x25, 0x01,                     //   Logical Maximum (1)
        0x75, 0x01,                     //   Report Size (1 bit)
        0x95, 0x08,                     //   Report Count (8)
        0x81, 0x02,                     //   Input (Data, Variable, Absolute) — modifiers
        0x95, 0x01,                     //   Report Count (1)
        0x75, 0x08,                     //   Report Size (8 bits)
        0x81, 0x01,                     //   Input (Constant) — reserved byte
        0x95, 0x06,                     //   Report Count (6)
        0x75, 0x08,                     //   Report Size (8 bits)
        0x15, 0x00,                     //   Logical Minimum (0)
        0x25, 0x65,                     //   Logical Maximum (101)
        0x05, 0x07,                     //   Usage Page (Keyboard/Keypad)
        0x19, 0x00,                     //   Usage Minimum (0)
        0x29, 0x65,                     //   Usage Maximum (101)
        0x81, 0x00,                     //   Input (Data, Array) — key codes
        0xC0,                           // End Collection

        // ---- Mouse ----
        0x05, 0x01,                     // Usage Page (Generic Desktop)
        0x09, 0x02,                     // Usage (Mouse)
        0xA1, 0x01,                     // Collection (Application)
        0x85, MOUSE_REPORT_ID,          //   Report ID
        0x09, 0x01,                     //   Usage (Pointer)
        0xA1, 0x00,                     //   Collection (Physical)
        0x05, 0x09,                     //     Usage Page (Buttons)
        0x19, 0x01,                     //     Usage Minimum (Button 1)
        0x29, 0x03,                     //     Usage Maximum (Button 3)
        0x15, 0x00,                     //     Logical Minimum (0)
        0x25, 0x01,                     //     Logical Maximum (1)
        0x95, 0x03,                     //     Report Count (3)
        0x75, 0x01,                     //     Report Size (1 bit)
        0x81, 0x02,                     //     Input (Data, Variable, Absolute) — buttons
        0x95, 0x01,                     //     Report Count (1)
        0x75, 0x05,                     //     Report Size (5 bits)
        0x81, 0x01,                     //     Input (Constant) — padding
        0x05, 0x01,                     //     Usage Page (Generic Desktop)
        0x09, 0x30,                     //     Usage (X)
        0x09, 0x31,                     //     Usage (Y)
        0x09, 0x38,                     //     Usage (Wheel)
        0x15, 0x81,                     //     Logical Minimum (-127)
        0x25, 0x7F,                     //     Logical Maximum (127)
        0x75, 0x08,                     //     Report Size (8 bits)
        0x95, 0x03,                     //     Report Count (3)
        0x81, 0x06,                     //     Input (Data, Variable, Relative) — X, Y, wheel
        0xC0,                           //   End Collection
        0xC0,                           // End Collection
    )

    /** A report meaning "nothing pressed, nothing moved" for [reportId], or null if unknown. */
    fun emptyReport(reportId: Int): ByteArray? = when (reportId) {
        KEYBOARD_REPORT_ID -> ByteArray(KEYBOARD_REPORT_SIZE)
        MOUSE_REPORT_ID -> ByteArray(MOUSE_REPORT_SIZE)
        else -> null
    }

    /** Lets the descriptor above be written as readable hex values instead of `.toByte()` calls. */
    private fun bytesOf(vararg values: Int): ByteArray =
        ByteArray(values.size) { index -> values[index].toByte() }
}
