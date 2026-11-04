package io.github.phonemousekeyboard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Whether the user has granted everything the app needs to use Bluetooth. */
data class PermissionStatus(val canUseBluetooth: Boolean = false)

/** The runtime permissions the app asks for, which differ between Android versions. */
object BluetoothPermissions {

    /**
     * Requested together, because Bluetooth features fail silently when any of them is
     * missing rather than reporting an error:
     *
     *  - Android 12+: the three "nearby devices" permissions.
     *  - Android 9 – 11: Bluetooth itself is granted at install time, but location access is
     *    still required, both for scanning and, on some phones, for the keyboard/mouse feature
     *    to respond at all.
     */
    val all: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_SCAN,
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun status(context: Context): PermissionStatus = PermissionStatus(
        canUseBluetooth = all.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        },
    )
}
