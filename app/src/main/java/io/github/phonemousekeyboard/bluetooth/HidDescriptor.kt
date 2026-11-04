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
 *  - Feature report [MOUSE_REPORT_ID]: the wheel's "Resolution Multiplier"
 *      byte 0     bits 0-1: 0 = one wheel unit per notch, 1 = [WHEEL_UNITS_PER_NOTCH] units
 *
 * The Resolution Multiplier is how mice offer smooth, high-resolution scrolling. A computer
 * that supports it (Windows Vista and later, Linux, ...) switches it on by writing 1 to the
 * feature report; from then on each wheel unit is a fraction of a notch.
 *
 * Reference: USB HID Usage Tables, https://usb.org/document-library/hid-usage-tables-15
 */
object HidDescriptor {

    const val KEYBOARD_REPORT_ID = 1
    const val MOUSE_REPORT_ID = 2

    const val KEYBOARD_REPORT_SIZE = 8
    const val MOUSE_REPORT_SIZE = 4

    /** Wheel units per notch once the computer has enabled high-resolution scrolling. */
    const val WHEEL_UNITS_PER_NOTCH = 16

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
        0x15, 0x81,                     //     Logical Minimum (-127)
        0x25, 0x7F,                     //     Logical Maximum (127)
        0x75, 0x08,                     //     Report Size (8 bits)
        0x95, 0x02,                     //     Report Count (2)
        0x81, 0x06,                     //     Input (Data, Variable, Relative) — X, Y
        0xA1, 0x02,                     //     Collection (Logical) — wheel + its multiplier
        0x09, 0x48,                     //       Usage (Resolution Multiplier)
        0x15, 0x00,                     //       Logical Minimum (0)
        0x25, 0x01,                     //       Logical Maximum (1)
        0x35, 0x01,                     //       Physical Minimum (1)
        0x45, WHEEL_UNITS_PER_NOTCH,    //       Physical Maximum
        0x75, 0x02,                     //       Report Size (2 bits)
        0x95, 0x01,                     //       Report Count (1)
        0xB1, 0x02,                     //       Feature (Data, Variable, Absolute)
        0x35, 0x00,                     //       Physical Minimum (0) — reset
        0x45, 0x00,                     //       Physical Maximum (0) — reset
        0x75, 0x06,                     //       Report Size (6 bits)
        0xB1, 0x03,                     //       Feature (Constant) — padding
        0x09, 0x38,                     //       Usage (Wheel)
        0x15, 0x81,                     //       Logical Minimum (-127)
        0x25, 0x7F,                     //       Logical Maximum (127)
        0x75, 0x08,                     //       Report Size (8 bits)
        0x95, 0x01,                     //       Report Count (1)
        0x81, 0x06,                     //       Input (Data, Variable, Relative) — wheel
        0xC0,                           //     End Collection
        0xC0,                           //   End Collection
        0xC0,                           // End Collection
    )

    /** A report meaning "nothing pressed, nothing moved" for [reportId], or null if unknown. */
    fun emptyReport(reportId: Int): ByteArray? = when (reportId) {
        KEYBOARD_REPORT_ID -> ByteArray(KEYBOARD_REPORT_SIZE)
        MOUSE_REPORT_ID -> ByteArray(MOUSE_REPORT_SIZE)
        else -> null
    }

    /** Whether a feature report written by the computer switches high-resolution scrolling on. */
    fun isHighResolutionScrollRequested(featureReport: ByteArray): Boolean =
        featureReport.isNotEmpty() && (featureReport[0].toInt() and 0b11) == 1

    /** The mouse feature report as stored: high-resolution scrolling on or off. */
    fun mouseFeatureReport(isHighResolution: Boolean): ByteArray =
        byteArrayOf(if (isHighResolution) 1 else 0)

    /** Lets the descriptor above be written as readable hex values instead of `.toByte()` calls. */
    private fun bytesOf(vararg values: Int): ByteArray =
        ByteArray(values.size) { index -> values[index].toByte() }
}
