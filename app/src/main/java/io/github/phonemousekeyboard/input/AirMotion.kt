package io.github.phonemousekeyboard.input

/*
 * Shared types for the Air mouse: what the sensors report, what the app sends, and how a
 * method of turning one into the other plugs in.
 */

/** The ways the Air mouse can turn phone movement into pointer movement. */
enum class AirMouseMethod {
    /** Point the phone at the screen; rotation measured by the gyroscope. Most precise. */
    GYROSCOPE_POINTING,

    /**
     * Point the phone at the screen; rotation worked out from the compass and accelerometer,
     * for phones without a gyroscope. Works, but compasses are jittery and affected by metal.
     */
    COMPASS_POINTING,

    /** Tilt the phone like a joystick; needs only the accelerometer. Works on any phone. */
    TILT,
}

/** The kinds of sensor the Air mouse reads. */
enum class SensorKind { GYROSCOPE, GRAVITY, ACCELEROMETER, MAGNETOMETER }

/** One sensor reading: three values (x, y, z in phone coordinates) at a moment in time. */
class SensorReading(
    val kind: SensorKind,
    val x: Float,
    val y: Float,
    val z: Float,
    val timestampNanos: Long,
)

/**
 * One step of Air mouse movement.
 *
 * @param dx pointer movement to send, in pointer units (positive = right).
 * @param dy pointer movement to send, in pointer units (positive = down).
 * @param horizontalReading what's driving the sideways movement, for display: degrees per
 *   second of turning when pointing, degrees of tilt when tilting.
 * @param verticalReading the same for up/down movement.
 */
data class AirMotion(
    val dx: Float,
    val dy: Float,
    val horizontalReading: Float,
    val verticalReading: Float,
)

/** The user's Air mouse preferences, as the motion code needs them. */
data class AirMotionSettings(
    val speed: Float,
    /** 0–1: how much small movement is ignored. */
    val steadiness: Float,
    /** 0–1: how much movement is smoothed. */
    val smoothing: Float,
    val invertHorizontal: Boolean,
    val invertVertical: Boolean,
)

/** Turns sensor readings into pointer movement in one particular way ([AirMouseMethod]). */
interface AirMouseController {

    /** Processes one reading. Returns movement to send, or null if this reading produces none. */
    fun onReading(reading: SensorReading, settings: AirMotionSettings): AirMotion?

    /** Forgets everything, e.g. after pausing, so the next reading starts fresh. */
    fun reset()

    companion object {
        fun create(method: AirMouseMethod): AirMouseController = when (method) {
            AirMouseMethod.GYROSCOPE_POINTING -> GyroscopePointing()
            AirMouseMethod.COMPASS_POINTING -> CompassPointing()
            AirMouseMethod.TILT -> TiltControl()
        }
    }
}

/** Exponential smoothing: keeps [keep] of the old value and takes the rest from [new]. */
internal fun smooth(old: Float, new: Float, keep: Float): Float = old * keep + new * (1f - keep)

/** Values within [deadZone] of zero become 0; beyond it, they start smoothly from 0. */
internal fun withoutDeadZone(value: Float, deadZone: Float): Float = when {
    value > deadZone -> value - deadZone
    value < -deadZone -> value + deadZone
    else -> 0f
}
