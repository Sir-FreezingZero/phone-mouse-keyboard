package io.github.phonemousekeyboard.input

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * The phone sensors the Air mouse reads: the gyroscope for rotation, and gravity to know
 * which way is up. Phones without a dedicated gravity sensor fall back to the
 * accelerometer, which measures gravity too while the phone is held fairly still.
 *
 * Readings are delivered on the main thread.
 */
class AirMouseSensors(context: Context) {

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val gyroscope: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val gravity: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    /** Without a gyroscope there is no Air mouse. */
    val isAvailable: Boolean get() = gyroscope != null

    private var listener: SensorEventListener? = null

    /**
     * Starts delivering readings: [onGravity] with the gravity direction and [onRotation]
     * with rotation rates in radians per second around the phone's x, y and z axes.
     */
    fun start(
        onGravity: (x: Float, y: Float, z: Float) -> Unit,
        onRotation: (x: Float, y: Float, z: Float, timestampNanos: Long) -> Unit,
    ) {
        stop()
        val manager = sensorManager ?: return
        val gyroscope = gyroscope ?: return

        val newListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val (x, y, z) = Triple(event.values[0], event.values[1], event.values[2])
                when (event.sensor.type) {
                    Sensor.TYPE_GYROSCOPE -> onRotation(x, y, z, event.timestamp)
                    else -> onGravity(x, y, z)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        listener = newListener

        // "Game" rate (about 50 readings a second) is smooth without flooding Bluetooth.
        manager.registerListener(newListener, gyroscope, SensorManager.SENSOR_DELAY_GAME)
        gravity?.let { manager.registerListener(newListener, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        listener?.let { sensorManager?.unregisterListener(it) }
        listener = null
    }
}
