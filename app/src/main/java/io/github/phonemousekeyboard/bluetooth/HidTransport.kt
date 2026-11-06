package io.github.phonemousekeyboard.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.flow.StateFlow

/**
 * Something that can present this phone to a computer as a keyboard + mouse and deliver
 * input reports to it.
 *
 * The app uses Bluetooth Low Energy ([io.github.phonemousekeyboard.bluetooth.ble.BleHidTransport]).
 * A classic Bluetooth implementation existed before and can be restored from the
 * `classic-bluetooth` branch; this interface is what it plugged into.
 *
 * All methods must be called on the main thread. Methods that touch Bluetooth require the
 * Bluetooth permissions; the caller requests them before calling [start].
 */
interface HidTransport {

    val state: StateFlow<ConnectionState>

    /** Starts presenting the phone as a keyboard + mouse. Safe to call more than once. */
    fun start()

    /** Throws away everything and starts again from scratch. */
    fun restart()

    /** Forgets all Bluetooth state after the Bluetooth radio has been switched off. */
    fun onBluetoothTurnedOff()

    /** Disconnects and stops presenting the phone as a keyboard + mouse. */
    fun stop()

    /** Connects to a computer that is already paired with this phone. */
    fun connect(host: BluetoothDevice)

    /** Disconnects from the current computer, or cancels a connection in progress. */
    fun disconnect()

    /**
     * Sends one input report described by [HidDescriptor]. [data] excludes the report ID.
     * Reports are silently dropped while no computer is connected.
     */
    fun sendReport(reportId: Int, data: ByteArray)

    /**
     * Wheel units the connected computer expects per scroll notch: 1, or
     * [HidDescriptor.WHEEL_UNITS_PER_NOTCH] once it has switched on high-resolution scrolling.
     */
    val wheelUnitsPerNotch: Int
}

/** A device's friendly name, falling back to its hardware address. */
@SuppressLint("MissingPermission")
fun BluetoothDevice.displayName(): String = name?.takeIf { it.isNotBlank() } ?: address
