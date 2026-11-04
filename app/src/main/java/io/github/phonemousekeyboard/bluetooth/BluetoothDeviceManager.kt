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

/** A snapshot of the phone's Bluetooth radio and the devices around it. */
data class BluetoothDevicesState(
    val isBluetoothEnabled: Boolean = false,
    val pairedDevices: List<BluetoothDevice> = emptyList(),
    val nearbyDevices: List<BluetoothDevice> = emptyList(),
    val isScanning: Boolean = false,
    /** Address of the device currently being paired with, if any. */
    val pairingAddress: String? = null,
    /** Why the last pairing attempt failed, if it did. */
    val pairingProblem: String? = null,
)

/**
 * Tracks the Bluetooth radio (on/off), the list of paired devices, and scanning for and
 * pairing with nearby devices.
 *
 * This is separate from [BluetoothHidController], which only deals with acting as a
 * keyboard/mouse once a device is paired.
 *
 * Requires BLUETOOTH_CONNECT for everything, plus BLUETOOTH_SCAN (Android 12+) or
 * ACCESS_FINE_LOCATION (Android 11 and older) for scanning. Call [start] once granted.
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
                addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                addAction(BluetoothDevice.ACTION_FOUND)
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
        stopScan()
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

    /** Looks for nearby discoverable devices. Results arrive in [state] over ~12 seconds. */
    fun startScan() {
        val adapter = adapter ?: return
        if (!adapter.isEnabled) return

        if (_state.value.isScanning) adapter.cancelDiscovery()
        _state.update { it.copy(nearbyDevices = emptyList(), pairingProblem = null) }
        adapter.startDiscovery()
    }

    fun stopScan() {
        if (_state.value.isScanning) adapter?.cancelDiscovery()
    }

    /** Starts pairing. Android shows its own confirmation dialog to the user. */
    fun pair(device: BluetoothDevice) {
        // Scanning slows down pairing considerably, so stop it first.
        stopScan()
        if (device.createBond()) {
            _state.update { it.copy(pairingAddress = device.address, pairingProblem = null) }
        } else {
            _state.update {
                it.copy(pairingProblem = "Couldn't start pairing with ${device.displayName()}.")
            }
        }
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

                BluetoothAdapter.ACTION_DISCOVERY_STARTED ->
                    _state.update { it.copy(isScanning = true) }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED ->
                    _state.update { it.copy(isScanning = false) }

                BluetoothDevice.ACTION_FOUND ->
                    intent.bluetoothDevice()?.let(::onDeviceFound)

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
            // Everything we knew about nearby devices is stale once the radio is off.
            BluetoothAdapter.STATE_OFF -> _state.value = BluetoothDevicesState()
        }
    }

    private fun onDeviceFound(device: BluetoothDevice) {
        // Paired devices already appear in their own list.
        if (device.bondState == BluetoothDevice.BOND_BONDED) return

        _state.update { current ->
            val alreadyListed = current.nearbyDevices.any { it.address == device.address }
            if (alreadyListed) current else current.copy(nearbyDevices = current.nearbyDevices + device)
        }
    }

    private fun onBondStateChanged(device: BluetoothDevice, bondState: Int) {
        val wasBeingPaired = _state.value.pairingAddress == device.address

        when (bondState) {
            BluetoothDevice.BOND_BONDING -> _state.update {
                it.copy(pairingAddress = device.address, pairingProblem = null)
            }

            BluetoothDevice.BOND_BONDED -> _state.update { current ->
                current.copy(
                    pairingAddress = current.pairingAddress.takeUnless { wasBeingPaired },
                    nearbyDevices = current.nearbyDevices.filterNot { it.address == device.address },
                )
            }

            BluetoothDevice.BOND_NONE -> _state.update { current ->
                current.copy(
                    pairingAddress = current.pairingAddress.takeUnless { wasBeingPaired },
                    pairingProblem = if (wasBeingPaired) {
                        "Pairing with ${device.displayName()} failed. On Windows, a " +
                            "notification asks you to allow the pairing; accept it within a " +
                            "few seconds and confirm the code matches."
                    } else {
                        current.pairingProblem
                    },
                )
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
