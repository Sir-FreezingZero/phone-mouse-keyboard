package io.github.phonemousekeyboard.input

import kotlin.math.sqrt

/**
 * The maths behind "point the phone at the screen", shared by [GyroscopePointing] and
 * [CompassPointing]: turning the phone left/right moves the pointer sideways, tilting it
 * up/down moves it vertically.
 *
 * "Left/right" is measured around the real vertical axis, worked out from gravity. That
 * keeps it right whether the phone is held flat like a remote or upright like a camera.
 * "Up/down" is rotation around the phone's own left-right axis, which stays horizontal in
 * either grip.
 *
 * Pure arithmetic with no Android dependencies, so it can be unit tested.
 */
class PointingProcessor {

    /** Latest "up" direction in phone coordinates, normalised. Starts as "lying flat". */
    private var upX = 0f
    private var upY = 0f
    private var upZ = 1f

    private var smoothedTurn = 0f
    private var smoothedTilt = 0f
    private var lastTimestampNanos = 0L

    /**
     * Updates which way is up, from a gravity (or accelerometer) reading in phone coordinates.
     * Android reports gravity as pointing up, away from the ground.
     */
    fun onGravity(x: Float, y: Float, z: Float) {
        val length = sqrt(x * x + y * y + z * z)
        if (length < MIN_GRAVITY_LENGTH) return
        upX = x / length
        upY = y / length
        upZ = z / length
    }

    /**
     * Processes one rotation rate (radians per second around the phone's x, y and z axes)
     * measured at [timestampNanos]. Returns the movement to send, or null for the very first
     * reading, which only sets the starting time.
     */
    fun onRotation(
        rateX: Float,
        rateY: Float,
        rateZ: Float,
        timestampNanos: Long,
        settings: AirMotionSettings,
    ): AirMotion? {
        val previous = lastTimestampNanos
        lastTimestampNanos = timestampNanos
        if (previous == 0L) return null

        // Ignore long gaps (e.g. after pausing) rather than turning them into a jump.
        val seconds = ((timestampNanos - previous) / NANOS_PER_SECOND).coerceIn(0f, MAX_STEP_SECONDS)

        // Turning: the part of the rotation that is around the vertical axis.
        val turn = rateX * upX + rateY * upY + rateZ * upZ
        // Tilting: rotation around the phone's left-right axis.
        val tilt = rateX

        val keep = settings.smoothing.coerceIn(0f, MAX_SMOOTHING)
        smoothedTurn = smooth(smoothedTurn, turn, keep)
        smoothedTilt = smooth(smoothedTilt, tilt, keep)

        val deadZone = settings.steadiness.coerceIn(0f, 1f) * MAX_DEAD_ZONE
        val scale = settings.speed * POINTER_UNITS_PER_RADIAN * seconds
        // Turning left (positive) moves the pointer left; tilting up (positive) moves it up.
        val horizontal = if (settings.invertHorizontal) 1f else -1f
        val vertical = if (settings.invertVertical) 1f else -1f

        return AirMotion(
            dx = horizontal * withoutDeadZone(smoothedTurn, deadZone) * scale,
            dy = vertical * withoutDeadZone(smoothedTilt, deadZone) * scale,
            horizontalReading = Math.toDegrees(smoothedTurn.toDouble()).toFloat(),
            verticalReading = Math.toDegrees(smoothedTilt.toDouble()).toFloat(),
        )
    }

    fun reset() {
        smoothedTurn = 0f
        smoothedTilt = 0f
        lastTimestampNanos = 0L
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000f
        const val MAX_STEP_SECONDS = 0.05f
        const val MIN_GRAVITY_LENGTH = 1f

        /** Pointer movement for one radian (about 57°) of rotation at speed 1×. */
        const val POINTER_UNITS_PER_RADIAN = 1_500f

        /** Steadiness 1 ignores rotation slower than this, in radians per second (~6°/s). */
        const val MAX_DEAD_ZONE = 0.1f

        /** Smoothing 1 would freeze the pointer; cap it so movement always gets through. */
        const val MAX_SMOOTHING = 0.9f
    }
}

/** Pointing with the gyroscope: rotation is measured directly. */
class GyroscopePointing : AirMouseController {

    private val pointing = PointingProcessor()

    override fun onReading(reading: SensorReading, settings: AirMotionSettings): AirMotion? =
        when (reading.kind) {
            SensorKind.GYROSCOPE -> pointing.onRotation(
                reading.x, reading.y, reading.z, reading.timestampNanos, settings,
            )
            SensorKind.GRAVITY, SensorKind.ACCELEROMETER -> {
                pointing.onGravity(reading.x, reading.y, reading.z)
                null
            }
            SensorKind.MAGNETOMETER -> null
        }

    override fun reset() = pointing.reset()
}
