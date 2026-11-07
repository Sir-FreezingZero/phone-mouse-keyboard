package io.github.phonemousekeyboard.input

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * Tilt the phone like a joystick, or a tray with a marble on it: tip the right edge down and
 * the pointer glides right, tip the top edge down and it glides up. The further it's tipped,
 * the faster the pointer moves; held level, it stops.
 *
 * "Level" is however the phone was held when the Air mouse started (or [recenter] was last
 * called), so it works sitting, standing or lying down. Needs only the accelerometer, so it
 * works on any phone.
 */
class TiltControl : AirMouseController {

    /** Smoothed "up" direction in phone coordinates, normalised; null until the first reading. */
    private var up: FloatArray? = null

    /** The "up" direction when the phone was held level; null until captured. */
    private var level: FloatArray? = null

    private var lastTimestampNanos = 0L

    /** Makes the phone's current position the new "level", where the pointer stays still. */
    fun recenter() {
        level = up?.copyOf()
    }

    override fun onReading(reading: SensorReading, settings: AirMotionSettings): AirMotion? {
        if (reading.kind != SensorKind.ACCELEROMETER && reading.kind != SensorKind.GRAVITY) return null

        val length = sqrt(reading.x * reading.x + reading.y * reading.y + reading.z * reading.z)
        if (length < MIN_GRAVITY_LENGTH) return null
        val measured = floatArrayOf(reading.x / length, reading.y / length, reading.z / length)

        val keep = BASE_SMOOTHING + settings.smoothing.coerceIn(0f, 1f) * EXTRA_SMOOTHING
        val up = up?.let { old -> FloatArray(3) { smooth(old[it], measured[it], keep) } } ?: measured
        this.up = up
        val level = level ?: up.copyOf().also { level = it }

        val previous = lastTimestampNanos
        lastTimestampNanos = reading.timestampNanos
        if (previous == 0L) return null
        val seconds = ((reading.timestampNanos - previous) / NANOS_PER_SECOND).coerceIn(0f, MAX_STEP_SECONDS)

        // How far "up" has swung along the phone's own axes since it was level: roughly the
        // sine of the tilt angle in each direction. Right edge down makes x negative; top
        // edge down makes y negative.
        val tiltSideways = up[0] - level[0]
        val tiltForward = up[1] - level[1]

        val deadZone = MIN_DEAD_ZONE + settings.steadiness.coerceIn(0f, 1f) * EXTRA_DEAD_ZONE
        val horizontal = if (settings.invertHorizontal) 1f else -1f
        val vertical = if (settings.invertVertical) -1f else 1f
        val distance = settings.speed * MAX_SPEED * seconds

        return AirMotion(
            dx = horizontal * speedFor(tiltSideways, deadZone) * distance,
            dy = vertical * speedFor(tiltForward, deadZone) * distance,
            horizontalReading = -degrees(tiltSideways),
            verticalReading = -degrees(tiltForward),
        )
    }

    override fun reset() {
        up = null
        level = null
        lastTimestampNanos = 0L
    }

    /**
     * Speed (as a share of [MAX_SPEED]) for a tilt: nothing inside the dead zone, then rising
     * gently at first (for precise, slow movement) and faster the further the phone is tipped.
     */
    private fun speedFor(tilt: Float, deadZone: Float): Float {
        val beyond = abs(withoutDeadZone(tilt, deadZone))
        val share = (beyond / FULL_SPEED_TILT).coerceAtMost(1f)
        return sign(tilt) * share.pow(SPEED_CURVE)
    }

    private fun degrees(tilt: Float): Float =
        Math.toDegrees(asin(tilt.coerceIn(-1f, 1f).toDouble())).toFloat()

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000f
        const val MAX_STEP_SECONDS = 0.05f
        const val MIN_GRAVITY_LENGTH = 1f

        /** Pointer units per second at full tilt and speed 1×. */
        const val MAX_SPEED = 2_000f

        /** Tilt (sine of the angle, ~30°) beyond the dead zone that reaches full speed. */
        const val FULL_SPEED_TILT = 0.5f

        /** Above 1, small tilts move slowly for precision. */
        const val SPEED_CURVE = 1.6f

        /** Tilt ignored even at steadiness 0 (~2°), plus up to this much more (~7°) at 1. */
        const val MIN_DEAD_ZONE = 0.03f
        const val EXTRA_DEAD_ZONE = 0.12f

        /** Accelerometers are noisy, so some smoothing always applies, plus the user's share. */
        const val BASE_SMOOTHING = 0.5f
        const val EXTRA_SMOOTHING = 0.4f
    }
}
