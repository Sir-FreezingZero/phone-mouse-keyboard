package io.github.phonemousekeyboard.input

import io.github.phonemousekeyboard.bluetooth.HidDescriptor
import io.github.phonemousekeyboard.bluetooth.HidTransport

/**
 * Turns mouse and keyboard actions into the input reports described by [HidDescriptor] and
 * hands them to a [HidTransport]. Knows nothing about how the reports travel.
 *
 * @param onInputSent called after every report, e.g. to show that input is flowing.
 */
class HidInput(
    private val transport: HidTransport,
    private val onInputSent: () -> Unit = {},
) {

    /** Bit field of mouse buttons currently held down. Included in every mouse report. */
    private var pressedButtons = 0

    /** Scrolling that hasn't added up to a whole wheel unit yet, in wheel units. */
    private var unsentWheelUnits = 0f

    // ---- Mouse ----

    /** Moves the pointer by ([dx], [dy]). */
    fun moveMouse(dx: Int, dy: Int) = sendMovement(dx, dy, wheel = 0)

    /**
     * Scrolls by [notches], which may be fractional. Positive scrolls up.
     *
     * If the computer has switched on high-resolution scrolling, fractions of a notch are sent
     * as they come, for smooth scrolling. With [smooth] off, or on computers without it,
     * scrolling is sent in whole notches.
     */
    fun scroll(notches: Float, smooth: Boolean) {
        val unitsPerNotch = transport.wheelUnitsPerNotch
        val unitsPerStep = if (smooth) 1 else unitsPerNotch

        unsentWheelUnits += notches * unitsPerNotch
        val steps = (unsentWheelUnits / unitsPerStep).toInt()
        if (steps == 0) return

        val units = steps * unitsPerStep
        unsentWheelUnits -= units
        sendMovement(dx = 0, dy = 0, wheel = units)
    }

    /**
     * Sends pointer movement and wheel units. Large values are split into several reports
     * because each report is limited to ±127.
     */
    private fun sendMovement(dx: Int, dy: Int, wheel: Int) {
        var remainingX = dx
        var remainingY = dy
        var remainingWheel = wheel

        while (remainingX != 0 || remainingY != 0 || remainingWheel != 0) {
            val stepX = remainingX.coerceIn(-MAX_REPORT_DELTA, MAX_REPORT_DELTA)
            val stepY = remainingY.coerceIn(-MAX_REPORT_DELTA, MAX_REPORT_DELTA)
            val stepWheel = remainingWheel.coerceIn(-MAX_REPORT_DELTA, MAX_REPORT_DELTA)

            sendMouseReport(stepX, stepY, stepWheel)

            remainingX -= stepX
            remainingY -= stepY
            remainingWheel -= stepWheel
        }
    }

    fun pressMouseButton(button: MouseButton) {
        pressedButtons = pressedButtons or button.bit
        sendMouseReport(dx = 0, dy = 0, wheel = 0)
    }

    fun releaseMouseButton(button: MouseButton) {
        pressedButtons = pressedButtons and button.bit.inv()
        sendMouseReport(dx = 0, dy = 0, wheel = 0)
    }

    fun clickMouseButton(button: MouseButton) {
        pressMouseButton(button)
        releaseMouseButton(button)
    }

    private fun sendMouseReport(dx: Int, dy: Int, wheel: Int) {
        val report = ByteArray(HidDescriptor.MOUSE_REPORT_SIZE)
        report[0] = pressedButtons.toByte()
        report[1] = dx.toByte()
        report[2] = dy.toByte()
        report[3] = wheel.toByte()
        send(HidDescriptor.MOUSE_REPORT_ID, report)
    }

    // ---- Keyboard ----

    /** Presses and releases a single key, holding any modifiers in [keyStroke] while doing so. */
    fun typeKey(keyStroke: KeyStroke) {
        val press = ByteArray(HidDescriptor.KEYBOARD_REPORT_SIZE)
        press[0] = keyStroke.modifiers.toByte()
        press[2] = keyStroke.keyCode.toByte()
        send(HidDescriptor.KEYBOARD_REPORT_ID, press)

        // An all-zero report means "no keys held".
        send(HidDescriptor.KEYBOARD_REPORT_ID, ByteArray(HidDescriptor.KEYBOARD_REPORT_SIZE))
    }

    private fun send(reportId: Int, report: ByteArray) {
        transport.sendReport(reportId, report)
        onInputSent()
    }

    private companion object {
        const val MAX_REPORT_DELTA = 127
    }
}
