package io.github.phonemousekeyboard.input

import android.hardware.SensorManager

/**
 * Pointing for phones without a gyroscope.
 *
 * The accelerometer (which way is down) and the compass (which way is north) together give
 * the phone's orientation. Comparing each orientation with the previous one gives how fast
 * the phone is rotating, which is what a gyroscope would have measured, and that is fed into
 * the same [PointingProcessor] the gyroscope uses.
 *
 * Compass readings are jittery, so both sensors are smoothed first; this makes the pointer a
 * little slower to respond than with a gyroscope. Nearby metal or magnets can also bend the
 * compass and nudge the pointer.
 */
class CompassPointing : AirMouseController {

    private val pointing = PointingProcessor()

    /** Smoothed sensor values; null until the first reading arrives. */
    private var gravity: FloatArray? = null
    private var magnetic: FloatArray? = null

    /** Orientation (rotation matrix) at the previous step, and when it was measured. */
    private var previousOrientation: FloatArray? = null
    private var previousTimestampNanos = 0L

    override fun onReading(reading: SensorReading, settings: AirMotionSettings): AirMotion? {
        when (reading.kind) {
            SensorKind.MAGNETOMETER -> {
                magnetic = smoothInto(magnetic, reading)
                return null
            }
            SensorKind.ACCELEROMETER, SensorKind.GRAVITY -> gravity = smoothInto(gravity, reading)
            SensorKind.GYROSCOPE -> return null
        }

        // Each accelerometer reading is one step: work out the orientation and compare it
        // with the last one.
        val gravity = gravity ?: return null
        val magnetic = magnetic ?: return null
        val orientation = FloatArray(9)
        if (!SensorManager.getRotationMatrix(orientation, null, gravity, magnetic)) return null

        pointing.onGravity(gravity[0], gravity[1], gravity[2])

        val previous = previousOrientation
        val seconds = (reading.timestampNanos - previousTimestampNanos) / NANOS_PER_SECOND
        previousOrientation = orientation
        previousTimestampNanos = reading.timestampNanos
        if (previous == null || seconds <= 0f) return null

        val (rateX, rateY, rateZ) = rotationRate(previous, orientation, seconds)
        return pointing.onRotation(rateX, rateY, rateZ, reading.timestampNanos, settings)
    }

    override fun reset() {
        pointing.reset()
        gravity = null
        magnetic = null
        previousOrientation = null
        previousTimestampNanos = 0L
    }

    private fun smoothInto(current: FloatArray?, reading: SensorReading): FloatArray {
        val values = floatArrayOf(reading.x, reading.y, reading.z)
        if (current == null) return values
        for (index in values.indices) {
            values[index] = smooth(current[index], values[index], SENSOR_SMOOTHING)
        }
        return values
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000f

        /** How much of the previous value each new compass/accelerometer value keeps. */
        const val SENSOR_SMOOTHING = 0.8f
    }
}

/**
 * The rotation rate (radians per second around the phone's x, y and z axes) that turns
 * orientation [from] into orientation [to] in [seconds].
 *
 * Both are rotation matrices from phone coordinates to world coordinates (as produced by
 * [SensorManager.getRotationMatrix], row-major). The change between them, seen from the
 * phone, is fromᵀ · to; for a small rotation that is close to the identity plus a matrix
 * whose off-diagonal entries are the rotation angles.
 */
internal fun rotationRate(from: FloatArray, to: FloatArray, seconds: Float): Triple<Float, Float, Float> {
    // change[row][column] = Σ from[k][row] · to[k][column]
    fun change(row: Int, column: Int): Float =
        (0 until 3).sumOf { k -> (from[k * 3 + row] * to[k * 3 + column]).toDouble() }.toFloat()

    val angleX = (change(2, 1) - change(1, 2)) / 2f
    val angleY = (change(0, 2) - change(2, 0)) / 2f
    val angleZ = (change(1, 0) - change(0, 1)) / 2f
    return Triple(angleX / seconds, angleY / seconds, angleZ / seconds)
}
