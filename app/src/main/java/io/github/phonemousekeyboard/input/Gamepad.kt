package io.github.phonemousekeyboard.input

import io.github.phonemousekeyboard.bluetooth.HidDescriptor
import kotlin.math.roundToInt

/**
 * The gamepad's buttons, in the order of the HID buttons they're sent as (button 1 first).
 * The order follows the common layout most games and Steam expect from a generic gamepad.
 */
enum class GamepadButton(val label: String) {
    A("A"),
    B("B"),
    X("X"),
    Y("Y"),
    L1("L1"),
    R1("R1"),
    L2("L2"),
    R2("R2"),
    SELECT("Select"),
    START("Start"),
    L3("L3"),
    R3("R3"),
    HOME("Home"),
    ;

    /** This button's bit in the report's button field. */
    val bit: Int get() = 1 shl ordinal
}

/** The D-pad's eight directions, numbered as the HID hat switch expects: up, then clockwise. */
enum class DpadDirection {
    UP,
    UP_RIGHT,
    RIGHT,
    DOWN_RIGHT,
    DOWN,
    DOWN_LEFT,
    LEFT,
    UP_LEFT,
}

enum class GamepadStick { LEFT, RIGHT }

/**
 * Everything currently held or tilted on the gamepad. A gamepad report always carries the
 * whole state, so each change sends a fresh copy of it.
 */
class GamepadState {

    private var pressedButtons = 0
    private var dpad: DpadDirection? = null

    /** Stick positions, each -1 (left / up) to 1 (right / down). */
    private var leftX = 0f
    private var leftY = 0f
    private var rightX = 0f
    private var rightY = 0f

    fun press(button: GamepadButton) {
        pressedButtons = pressedButtons or button.bit
    }

    fun release(button: GamepadButton) {
        pressedButtons = pressedButtons and button.bit.inv()
    }

    /** Sets the D-pad direction held, or null when it's let go. */
    fun setDpad(direction: DpadDirection?) {
        dpad = direction
    }

    /** Sets [stick]'s position; [x] and [y] run from -1 (left / up) to 1 (right / down). */
    fun setStick(stick: GamepadStick, x: Float, y: Float) {
        when (stick) {
            GamepadStick.LEFT -> {
                leftX = x
                leftY = y
            }
            GamepadStick.RIGHT -> {
                rightX = x
                rightY = y
            }
        }
    }

    /** Lets go of everything: no buttons, D-pad centred, sticks centred. */
    fun reset() {
        pressedButtons = 0
        dpad = null
        setStick(GamepadStick.LEFT, 0f, 0f)
        setStick(GamepadStick.RIGHT, 0f, 0f)
    }

    /** The state as a gamepad report, laid out as described in [HidDescriptor]. */
    fun toReport(): ByteArray {
        val report = ByteArray(HidDescriptor.GAMEPAD_REPORT_SIZE)
        report[0] = (pressedButtons and 0xFF).toByte()
        report[1] = (pressedButtons shr 8).toByte()
        report[HidDescriptor.GAMEPAD_HAT_BYTE] =
            (dpad?.ordinal ?: HidDescriptor.GAMEPAD_HAT_CENTERED).toByte()
        report[3] = axisByte(leftX)
        report[4] = axisByte(leftY)
        report[5] = axisByte(rightX)
        report[6] = axisByte(rightY)
        return report
    }

    private fun axisByte(value: Float): Byte =
        (value.coerceIn(-1f, 1f) * MAX_AXIS).roundToInt().toByte()

    private companion object {
        const val MAX_AXIS = 127
    }
}
