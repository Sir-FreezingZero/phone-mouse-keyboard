package io.github.phonemousekeyboard.input

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * The phone sensors the Air mouse can read, and which [AirMouseMethod]s they make possible.
 * Readings are delivered on the main thread.
 */
class AirMouseSensors(context: Context) {

    private val sensorManager = context.getSystemService(SensorManager::class.java)

    private val gyroscope = sensor(Sensor.TYPE_GYROSCOPE)
    private val gravity = sensor(Sensor.TYPE_GRAVITY)
    private val accelerometer = sensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensor(Sensor.TYPE_MAGNETIC_FIELD)

    val hasGyroscope: Boolean get() = gyroscope != null
    val hasCompass: Boolean get() = magnetometer != null && accelerometer != null
    val hasAccelerometer: Boolean get() = accelerometer != null

    private var listener: SensorEventListener? = null

    /**
     * The best way to point at the screen on this phone: the gyroscope if there is one,
     * otherwise the compass. Null if neither is available.
     */
    val pointingMethod: AirMouseMethod?
        get() = when {
            hasGyroscope -> AirMouseMethod.GYROSCOPE_POINTING
            hasCompass -> AirMouseMethod.COMPASS_POINTING
            else -> null
        }

    /** Starts delivering the readings [method] needs to [onReading]. */
    fun start(method: AirMouseMethod, onReading: (SensorReading) -> Unit) {
        stop()
        val manager = sensorManager ?: return

        val newListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val kind = when (event.sensor.type) {
                    Sensor.TYPE_GYROSCOPE -> SensorKind.GYROSCOPE
                    Sensor.TYPE_GRAVITY -> SensorKind.GRAVITY
                    Sensor.TYPE_ACCELEROMETER -> SensorKind.ACCELEROMETER
                    Sensor.TYPE_MAGNETIC_FIELD -> SensorKind.MAGNETOMETER
                    else -> return
                }
                onReading(
                    SensorReading(kind, event.values[0], event.values[1], event.values[2], event.timestamp),
                )
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        listener = newListener

        // "Game" rate (about 50 readings a second) is smooth without flooding Bluetooth.
        sensorsFor(method).forEach { sensor ->
            manager.registerListener(newListener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        listener?.let { sensorManager?.unregisterListener(it) }
        listener = null
    }

    private fun sensorsFor(method: AirMouseMethod): List<Sensor> = when (method) {
        // Gravity tells which way is up; the accelerometer is a fine stand-in.
        AirMouseMethod.GYROSCOPE_POINTING -> listOfNotNull(gyroscope, gravity ?: accelerometer)
        AirMouseMethod.COMPASS_POINTING -> listOfNotNull(accelerometer, magnetometer)
        // The raw accelerometer: on phones without a gyroscope, "gravity" is often only an
        // estimate built from it anyway.
        AirMouseMethod.TILT -> listOfNotNull(accelerometer)
    }

    private fun sensor(type: Int): Sensor? = sensorManager?.getDefaultSensor(type)
}
