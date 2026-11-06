package io.github.phonemousekeyboard.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A snapshot of the phone's Bluetooth radio and the devices paired with it. */
data class BluetoothDevicesState(
    val isBluetoothEnabled: Boolean = false,
    val pairedDevices: List<BluetoothDevice> = emptyList(),
    /** Why the last pairing attempt failed, if it did. */
    val pairingProblem: String? = null,
)

/**
 * Tracks the Bluetooth radio (on/off), the list of paired devices, and pairing as it happens.
 * Computers pair with the phone from their own Bluetooth settings; this only follows along.
 *
 * This is separate from [io.github.phonemousekeyboard.bluetooth.ble.BleHidTransport], which deals with acting as a keyboard/mouse.
 *
 * Requires BLUETOOTH_CONNECT on Android 12+. Call [start] once it's granted.
 */
@SuppressLint("MissingPermission")
class BluetoothDeviceManager(context: Context) {

    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? =
        appContext.getSystemService(BluetoothManager::class.java)?.adapter

    private val _state = MutableStateFlow(
        BluetoothDevicesState(isBluetoothEnabled = adapter?.isEnabled == true),
    )
    val state: StateFlow<BluetoothDevicesState> = _state.asStateFlow()

    private var isReceiverRegistered = false

    /** Address of the device currently pairing with the phone, if any. */
    private var pairingAddress: String? = null

    /** Called when a device finishes pairing with this phone, e.g. to connect straight away. */
    var onPaired: ((BluetoothDevice) -> Unit)? = null

    // ------------------------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------------------------

    /** Starts listening for Bluetooth events. Safe to call more than once. */
    fun start() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            }
            // These broadcasts are sent by the Bluetooth app rather than the core system,
            // so the receiver has to be exported to receive them on newer Android versions.
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED,
            )
            isReceiverRegistered = true
        }
        _state.update { it.copy(isBluetoothEnabled = adapter?.isEnabled == true) }
        refreshPairedDevices()
    }

    fun close() {
        if (isReceiverRegistered) {
            appContext.unregisterReceiver(receiver)
            isReceiverRegistered = false
        }
    }

    // ------------------------------------------------------------------------------------------
    // Devices
    // ------------------------------------------------------------------------------------------

    /** Looks up a device by its hardware address, e.g. "00:11:22:AA:BB:CC". */
    fun findDevice(address: String): BluetoothDevice? {
        if (!BluetoothAdapter.checkBluetoothAddress(address)) return null
        return adapter?.getRemoteDevice(address)
    }

    private fun refreshPairedDevices() {
        val paired = adapter?.bondedDevices
            .orEmpty()
            .sortedBy { it.displayName().lowercase() }
        _state.update { it.copy(pairedDevices = paired) }
    }

    // ------------------------------------------------------------------------------------------
    // Broadcast handling
    // ------------------------------------------------------------------------------------------

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> onAdapterStateChanged(
                    intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR),
                )

                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> intent.bluetoothDevice()?.let {
                    onBondStateChanged(
                        device = it,
                        bondState = intent.getIntExtra(
                            BluetoothDevice.EXTRA_BOND_STATE,
                            BluetoothDevice.BOND_NONE,
                        ),
                    )
                }
            }
        }
    }

    private fun onAdapterStateChanged(adapterState: Int) {
        when (adapterState) {
            BluetoothAdapter.STATE_ON -> {
                _state.update { it.copy(isBluetoothEnabled = true) }
                refreshPairedDevices()
            }
            BluetoothAdapter.STATE_OFF -> {
                pairingAddress = null
                _state.value = BluetoothDevicesState()
            }
        }
    }

    private fun onBondStateChanged(device: BluetoothDevice, bondState: Int) {
        val wasBeingPaired = pairingAddress == device.address

        when (bondState) {
            BluetoothDevice.BOND_BONDING -> {
                pairingAddress = device.address
                _state.update { it.copy(pairingProblem = null) }
            }

            BluetoothDevice.BOND_BONDED -> {
                if (wasBeingPaired) pairingAddress = null
            }

            BluetoothDevice.BOND_NONE -> if (wasBeingPaired) {
                pairingAddress = null
                _state.update {
                    it.copy(
                        pairingProblem = "Pairing with ${device.displayName()} failed. Accept " +
                            "the pairing prompts on both the phone and the computer within a " +
                            "few seconds, then try again.",
                    )
                }
            }
        }
        refreshPairedDevices()

        if (bondState == BluetoothDevice.BOND_BONDED && wasBeingPaired) {
            onPaired?.invoke(device)
        }
    }

    private fun Intent.bluetoothDevice(): BluetoothDevice? =
        IntentCompat.getParcelableExtra(
            this,
            BluetoothDevice.EXTRA_DEVICE,
            BluetoothDevice::class.java,
        )
}
