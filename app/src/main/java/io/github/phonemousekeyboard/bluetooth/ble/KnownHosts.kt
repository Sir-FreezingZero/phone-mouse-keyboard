package io.github.phonemousekeyboard.bluetooth.ble

import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.core.content.edit

/**
 * Remembers which computers have paired with the Bluetooth Low Energy keyboard/mouse, so that
 * when one reconnects later it is recognised straight away.
 */
internal class KnownHosts(context: Context) {

    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun contains(device: BluetoothDevice): Boolean = device.address in addresses()

    fun add(device: BluetoothDevice) {
        val updated = addresses() + device.address
        preferences.edit { putStringSet(KEY_ADDRESSES, updated) }
    }

    private fun addresses(): Set<String> =
        preferences.getStringSet(KEY_ADDRESSES, emptySet()).orEmpty()

    private companion object {
        const val PREFERENCES_NAME = "ble_known_hosts"
        const val KEY_ADDRESSES = "addresses"
    }
}
