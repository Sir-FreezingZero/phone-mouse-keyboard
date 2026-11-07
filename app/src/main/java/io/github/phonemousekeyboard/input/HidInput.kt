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

    // ---- Media keys ----

    /**
     * Holds [key] down until [releaseMediaKey]. Holding Volume up, for example, keeps raising
     * the volume, just as on a real keyboard.
     */
    fun pressMediaKey(key: MediaKey) {
        val report = ByteArray(HidDescriptor.MEDIA_REPORT_SIZE)
        report[0] = (key.usage and 0xFF).toByte()
        report[1] = (key.usage shr 8).toByte()
        send(HidDescriptor.MEDIA_REPORT_ID, report)
    }

    /** Lets go of the media key held down. */
    fun releaseMediaKey() {
        send(HidDescriptor.MEDIA_REPORT_ID, ByteArray(HidDescriptor.MEDIA_REPORT_SIZE))
    }

    // ---- Gamepad ----

    private val gamepad = GamepadState()

    /** The last gamepad report sent, so unchanged states aren't sent again. */
    private var lastGamepadReport: ByteArray? = null

    fun pressGamepadButton(button: GamepadButton) = updateGamepad { press(button) }

    fun releaseGamepadButton(button: GamepadButton) = updateGamepad { release(button) }

    /** Holds the D-pad in [direction], or lets it go when null. */
    fun setGamepadDpad(direction: DpadDirection?) = updateGamepad { setDpad(direction) }

    /** Moves [stick]; [x] and [y] run from -1 (left / up) to 1 (right / down). */
    fun setGamepadStick(stick: GamepadStick, x: Float, y: Float) =
        updateGamepad { setStick(stick, x, y) }

    /** Lets go of every gamepad button, the D-pad and both sticks. */
    fun resetGamepad() = updateGamepad { reset() }

    private fun updateGamepad(change: GamepadState.() -> Unit) {
        gamepad.change()
        val report = gamepad.toReport()
        if (report.contentEquals(lastGamepadReport)) return
        lastGamepadReport = report
        send(HidDescriptor.GAMEPAD_REPORT_ID, report)
    }

    private fun send(reportId: Int, report: ByteArray) {
        transport.sendReport(reportId, report)
        onInputSent()
    }

    private companion object {
        const val MAX_REPORT_DELTA = 127
    }
}
