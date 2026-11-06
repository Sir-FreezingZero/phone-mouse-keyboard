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
     * Android 12+ asks for "nearby devices" permissions at runtime: connecting (to use
     * Bluetooth at all) and advertising (so computers can find the phone). On Android 9 – 11,
     * Bluetooth Low Energy needs only install-time permissions, so there is nothing to ask.
     */
    val all: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
            )
        } else {
            emptyArray()
        }

    fun status(context: Context): PermissionStatus = PermissionStatus(
        canUseBluetooth = all.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        },
    )
}
