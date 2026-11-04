package io.github.phonemousekeyboard.bluetooth.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import io.github.phonemousekeyboard.bluetooth.ConnectionState
import io.github.phonemousekeyboard.bluetooth.HidDescriptor
import io.github.phonemousekeyboard.bluetooth.HidTransport
import io.github.phonemousekeyboard.bluetooth.ScrollResolutionMemory
import io.github.phonemousekeyboard.bluetooth.displayName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Presents the phone as a Bluetooth Low Energy keyboard + mouse ("HID over GATT"), the way
 * most modern wireless mice and keyboards work.
 *
 * This doesn't need Android's classic "HID Device" feature, which some manufacturers disable.
 * It needs the phone to support BLE peripheral mode (advertising), which most phones do.
 *
 * How it works:
 *  1. Publish the keyboard/mouse description ([HidGattProfile]) on a GATT server.
 *  2. Advertise as a HID device, so it shows up in the computer's "Add device" list.
 *  3. The computer connects and pairs; the phone shows Android's pairing prompt.
 *  4. Input reports are sent as GATT notifications, one at a time ([ReportQueue]).
 *
 * Android delivers GATT callbacks on background threads; every callback hops to the main
 * thread before touching any state, so all fields here are only used from the main thread.
 */
@SuppressLint("MissingPermission")
class BleHidTransport(
    context: Context,
    private val scrollResolution: ScrollResolutionMemory,
) : HidTransport {

    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val mainHandler = Handler(Looper.getMainLooper())
    private val knownHosts = KnownHosts(appContext)

    private var gattServer: BluetoothGattServer? = null
    private val servicesToAdd = ArrayDeque<BluetoothGattService>()
    private var reportCharacteristics: Map<Int, BluetoothGattCharacteristic> = emptyMap()
    private var mouseFeatureCharacteristic: BluetoothGattCharacteristic? = null
    private var isBondReceiverRegistered = false

    private var isAdvertising = false
    private var isUsingShortAdvertisement = false

    /** The computer input is sent to, once it has connected (and usually paired). */
    private var host: BluetoothDevice? = null

    /** Devices connected to the phone that may turn out to be the host, see [onDeviceConnected]. */
    private val hostCandidates = mutableSetOf<BluetoothDevice>()

    /** True while a disconnect the user asked for is in progress, so it isn't reported as a problem. */
    private var isDisconnectRequested = false

    private val reportQueue = ReportQueue()
    private var isNotificationInFlight = false

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Starting("Waiting…"))
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    // ------------------------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------------------------

    override fun start() {
        val adapter = adapter
        when {
            adapter == null -> {
                _state.value = ConnectionState.Unavailable("This phone has no Bluetooth.")
                return
            }
            !adapter.isEnabled -> {
                _state.value = ConnectionState.Unavailable("Bluetooth is turned off.")
                return
            }
            gattServer != null -> return
            adapter.bluetoothLeAdvertiser == null || !adapter.isMultipleAdvertisementSupported -> {
                _state.value = ConnectionState.Unavailable(
                    "This phone can't act as a Bluetooth Low Energy device (no BLE advertising).",
                    isFeatureMissing = true,
                )
                return
            }
        }

        _state.value = ConnectionState.Starting("Setting up the Bluetooth Low Energy keyboard and mouse…")
        mainHandler.postDelayed(startUpTimeout, START_UP_TIMEOUT_MILLIS)

        val server = try {
            bluetoothManager?.openGattServer(appContext, gattCallback)
        } catch (e: RuntimeException) {
            Log.w(TAG, "openGattServer threw", e)
            null
        }
        if (server == null) {
            failStartUp("Android couldn't open a Bluetooth Low Energy server.")
            return
        }
        gattServer = server
        registerBondReceiver()

        val hid = HidGattProfile.buildHidService()
        reportCharacteristics = hid.inputReports
        mouseFeatureCharacteristic = hid.mouseFeatureReport
        servicesToAdd.clear()
        servicesToAdd += hid.service
        servicesToAdd += HidGattProfile.buildBatteryService()
        servicesToAdd += HidGattProfile.buildDeviceInformationService()
        addNextService()
    }

    override fun restart() {
        stop()
        start()
    }

    override fun onBluetoothTurnedOff() {
        release()
        _state.value = ConnectionState.Unavailable("Bluetooth is turned off.")
    }

    override fun stop() {
        host?.let { gattServer?.cancelConnection(it) }
        stopAdvertising()
        release()
    }

    private fun release() {
        mainHandler.removeCallbacksAndMessages(null)
        unregisterBondReceiver()
        try {
            gattServer?.close()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Closing the GATT server threw", e)
        }
        gattServer = null
        servicesToAdd.clear()
        reportCharacteristics = emptyMap()
        mouseFeatureCharacteristic = null
        isAdvertising = false
        host = null
        hostCandidates.clear()
        clientConfigurations.clear()
        reportQueue.clear()
        isNotificationInFlight = false
    }

    /** Android requires services to be added one at a time, each after the last succeeds. */
    private fun addNextService() {
        val server = gattServer ?: return
        val service = servicesToAdd.firstOrNull()
        if (service == null) {
            startAdvertising()
            return
        }
        if (!server.addService(service)) {
            failStartUp("Android couldn't publish the keyboard/mouse description.")
        }
    }

    private fun onServiceAdded(status: Int) {
        if (status != BluetoothGatt.GATT_SUCCESS) {
            failStartUp("Android couldn't publish the keyboard/mouse description (error $status).")
            return
        }
        servicesToAdd.removeFirstOrNull()
        addNextService()
    }

    private fun failStartUp(problem: String) {
        Log.w(TAG, "Start-up failed: $problem")
        stop()
        _state.value = ConnectionState.Unavailable(problem)
    }

    private val startUpTimeout = Runnable {
        if (_state.value is ConnectionState.Starting) {
            failStartUp("Setting up Bluetooth Low Energy took too long. Tap Retry.")
        }
    }

    // ------------------------------------------------------------------------------------------
    // Advertising: how computers find the phone
    // ------------------------------------------------------------------------------------------

    private fun startAdvertising() {
        if (isAdvertising) return
        val advertiser = adapter?.bluetoothLeAdvertiser ?: return

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .setTimeout(0)
            .build()

        // The HID service UUID is what makes computers list the phone as an input device.
        // A long phone name may not fit next to it; then the name moves to the scan response.
        val advertisement = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(HidGattProfile.SERVICE_HID))
            .setIncludeDeviceName(!isUsingShortAdvertisement)
            .build()
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(isUsingShortAdvertisement)
            .build()

        try {
            advertiser.startAdvertising(settings, advertisement, scanResponse, advertiseCallback)
        } catch (e: RuntimeException) {
            Log.w(TAG, "startAdvertising threw", e)
            failStartUp("Android couldn't start Bluetooth Low Energy advertising.")
        }
    }

    private fun stopAdvertising() {
        if (!isAdvertising) return
        isAdvertising = false
        try {
            adapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        } catch (e: RuntimeException) {
            Log.w(TAG, "stopAdvertising threw", e)
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            mainHandler.post {
                isAdvertising = true
                if (_state.value is ConnectionState.Starting) {
                    mainHandler.removeCallbacks(startUpTimeout)
                    _state.value = ConnectionState.Disconnected()
                }
            }
        }

        override fun onStartFailure(errorCode: Int) {
            mainHandler.post { onAdvertisingFailed(errorCode) }
        }
    }

    private fun onAdvertisingFailed(errorCode: Int) {
        Log.w(TAG, "Advertising failed: $errorCode")
        when (errorCode) {
            AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> isAdvertising = true

            AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> {
                if (isUsingShortAdvertisement) {
                    failStartUp("Android couldn't start Bluetooth Low Energy advertising.")
                } else {
                    isUsingShortAdvertisement = true
                    startAdvertising()
                }
            }

            AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> failStartUp(
                "Too many apps are using Bluetooth Low Energy advertising. Close some, then " +
                    "tap Retry.",
            )

            else -> failStartUp("Android couldn't start Bluetooth Low Energy advertising (error $errorCode).")
        }
    }

    // ------------------------------------------------------------------------------------------
    // Connections
    // ------------------------------------------------------------------------------------------

    /**
     * Computers reconnect to a known keyboard/mouse by themselves when it advertises, so this
     * makes sure the phone is advertising and asks Android to connect as soon as it can.
     */
    override fun connect(host: BluetoothDevice) {
        val server = gattServer ?: return
        isDisconnectRequested = false
        startAdvertising()
        _state.value = ConnectionState.Connecting(host.displayName(), host.address)
        server.connect(host, true)
        mainHandler.postDelayed(connectTimeout, CONNECT_TIMEOUT_MILLIS)
    }

    private val connectTimeout = Runnable {
        val connecting = _state.value as? ConnectionState.Connecting ?: return@Runnable
        _state.value = ConnectionState.Disconnected(
            problem = "${connecting.deviceName} didn't connect. Make sure its Bluetooth is on; " +
                "on Windows, clicking the phone in Bluetooth settings also connects it.",
        )
    }

    override fun disconnect() {
        val server = gattServer ?: return
        mainHandler.removeCallbacks(connectTimeout)
        val current = host
        if (current == null) {
            _state.value = ConnectionState.Disconnected()
            return
        }
        isDisconnectRequested = true
        server.cancelConnection(current)
    }

    private fun onDeviceConnected(device: BluetoothDevice) {
        if (host != null) return

        // Android reports every Bluetooth LE connection to the phone here, including ones to
        // watches or earbuds that have nothing to do with us. A device becomes the host once
        // it is known to have used this keyboard/mouse before, starts pairing, or starts
        // using our services. Until then it is only a candidate.
        if (knownHosts.contains(device)) {
            becomeHost(device)
        } else {
            hostCandidates += device
        }
    }

    /** Called for every request a computer makes to our services. */
    private fun onHostActivity(device: BluetoothDevice) {
        if (host == null) becomeHost(device)
    }

    /**
     * Android answers a new computer's first requests itself ("pair first, please") without
     * telling this app, so pairing is the first sign that a candidate is our computer.
     */
    private fun onBondStateChanged(device: BluetoothDevice) {
        when {
            device == host -> updateConnectionState()
            host == null && device in hostCandidates -> becomeHost(device)
        }
    }

    private fun becomeHost(device: BluetoothDevice) {
        host = device
        hostCandidates.clear()
        mainHandler.removeCallbacks(connectTimeout)
        // Keyboards and mice talk to one computer at a time.
        stopAdvertising()
        // Tells Android this app wants the link kept open, so it isn't dropped as idle.
        gattServer?.connect(device, false)
        updateConnectionState()

        // Computers normally start pairing themselves. If one hasn't after a few seconds,
        // ask from this side instead.
        mainHandler.postDelayed(
            { if (host == device && device.bondState == BluetoothDevice.BOND_NONE) device.createBond() },
            PAIRING_NUDGE_DELAY_MILLIS,
        )
    }

    /** Connected once the link is paired, since keyboards and mice only work encrypted. */
    private fun updateConnectionState() {
        val device = host ?: return
        _state.value = if (device.bondState == BluetoothDevice.BOND_BONDED) {
            knownHosts.add(device)
            ConnectionState.Connected(device.displayName(), device.address)
        } else {
            ConnectionState.Connecting(device.displayName(), device.address)
        }
    }

    private fun onDeviceDisconnected(device: BluetoothDevice) {
        hostCandidates -= device
        if (device != host) return

        val wasConnected = _state.value is ConnectionState.Connected
        val problem = when {
            isDisconnectRequested -> null
            wasConnected -> "Lost the connection to ${device.displayName()}."
            else -> "${device.displayName()} disconnected before pairing finished. Try again, " +
                "and accept the pairing prompts on both the phone and the computer."
        }

        host = null
        isDisconnectRequested = false
        reportQueue.clear()
        isNotificationInFlight = false
        _state.value = ConnectionState.Disconnected(problem)

        // Let the computer find the phone again.
        startAdvertising()
    }

    private val bondReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = IntentCompat.getParcelableExtra(
                intent,
                BluetoothDevice.EXTRA_DEVICE,
                BluetoothDevice::class.java,
            )
            if (device != null) onBondStateChanged(device)
        }
    }

    private fun registerBondReceiver() {
        if (isBondReceiverRegistered) return
        ContextCompat.registerReceiver(
            appContext,
            bondReceiver,
            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED),
            // Sent by the Bluetooth app rather than the core system, so it must be exported.
            ContextCompat.RECEIVER_EXPORTED,
        )
        isBondReceiverRegistered = true
    }

    private fun unregisterBondReceiver() {
        if (!isBondReceiverRegistered) return
        appContext.unregisterReceiver(bondReceiver)
        isBondReceiverRegistered = false
    }

    // ------------------------------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------------------------------

    override val wheelUnitsPerNotch: Int
        get() = scrollResolution.wheelUnitsPerNotch(host?.address)

    override fun sendReport(reportId: Int, data: ByteArray) {
        if (_state.value !is ConnectionState.Connected) return
        if (reportId !in reportCharacteristics) return
        reportQueue.add(QueuedReport(reportId, data.copyOf()))
        sendNextReport()
    }

    /** Sends the next queued report, unless one is still on its way. */
    private fun sendNextReport() {
        if (isNotificationInFlight) return
        val server = gattServer ?: return
        val device = host ?: return
        val report = reportQueue.poll() ?: return
        val characteristic = reportCharacteristics.getValue(report.reportId)

        if (notify(server, device, characteristic, report.data)) {
            isNotificationInFlight = true
            mainHandler.postDelayed(notificationWatchdog, NOTIFICATION_TIMEOUT_MILLIS)
        } else {
            // Bluetooth is busy: try again in a moment.
            reportQueue.pushBack(report)
            mainHandler.postDelayed({ sendNextReport() }, NOTIFICATION_RETRY_MILLIS)
        }
    }

    private fun onNotificationSent() {
        mainHandler.removeCallbacks(notificationWatchdog)
        isNotificationInFlight = false
        sendNextReport()
    }

    /** If Android never confirms a notification, don't let input stall forever. */
    private val notificationWatchdog = Runnable {
        isNotificationInFlight = false
        sendNextReport()
    }

    private fun notify(
        server: BluetoothGattServer,
        device: BluetoothDevice,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            server.notifyCharacteristicChanged(device, characteristic, false, value) ==
                BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = value
            @Suppress("DEPRECATION")
            server.notifyCharacteristicChanged(device, characteristic, false)
        }
    } catch (e: RuntimeException) {
        Log.w(TAG, "Sending a report threw", e)
        false
    }

    // ------------------------------------------------------------------------------------------
    // Answering the computer
    // ------------------------------------------------------------------------------------------

    /** Notification on/off switches written by the computer, by descriptor. */
    private val clientConfigurations = mutableMapOf<BluetoothGattDescriptor, ByteArray>()

    private fun valueOf(device: BluetoothDevice, characteristic: BluetoothGattCharacteristic): ByteArray? =
        when {
            characteristic == mouseFeatureCharacteristic -> HidDescriptor.mouseFeatureReport(
                scrollResolution.isHighResolution(device.address),
            )
            else -> valueOf(characteristic)
        }

    private fun valueOf(characteristic: BluetoothGattCharacteristic): ByteArray? =
        when (characteristic.uuid) {
            HidGattProfile.CHARACTERISTIC_HID_INFORMATION -> HidGattProfile.HID_INFORMATION
            HidGattProfile.CHARACTERISTIC_REPORT_MAP -> HidDescriptor.REPORT_MAP
            HidGattProfile.CHARACTERISTIC_PROTOCOL_MODE -> HidGattProfile.PROTOCOL_MODE_REPORT
            HidGattProfile.CHARACTERISTIC_BATTERY_LEVEL -> HidGattProfile.BATTERY_FULL
            HidGattProfile.CHARACTERISTIC_MANUFACTURER_NAME -> HidGattProfile.MANUFACTURER_NAME
            HidGattProfile.CHARACTERISTIC_REPORT ->
                reportIdOf(characteristic)?.let(HidDescriptor::emptyReport)
            else -> null
        }

    private fun valueOf(descriptor: BluetoothGattDescriptor): ByteArray? =
        when (descriptor.uuid) {
            HidGattProfile.DESCRIPTOR_REPORT_REFERENCE -> when (descriptor.characteristic) {
                mouseFeatureCharacteristic -> HidGattProfile.MOUSE_FEATURE_REPORT_REFERENCE
                else -> reportIdOf(descriptor.characteristic)?.let(HidGattProfile::inputReportReference)
            }
            HidGattProfile.DESCRIPTOR_CLIENT_CONFIGURATION ->
                clientConfigurations[descriptor]
                    ?: BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            else -> null
        }

    private fun reportIdOf(characteristic: BluetoothGattCharacteristic): Int? =
        reportCharacteristics.entries.firstOrNull { it.value == characteristic }?.key

    /**
     * Answers a read. Long values such as the Report Map are read in pieces; [offset] says
     * where the requested piece starts.
     */
    private fun respondToRead(device: BluetoothDevice, requestId: Int, offset: Int, value: ByteArray?) {
        val server = gattServer ?: return
        when {
            value == null ->
                server.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null)
            offset > value.size ->
                server.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null)
            else ->
                server.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    offset,
                    value.copyOfRange(offset, value.size),
                )
        }
    }

    private fun respondToWrite(device: BluetoothDevice, requestId: Int, responseNeeded: Boolean) {
        if (responseNeeded) {
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
        }
    }

    private val gattCallback = object : BluetoothGattServerCallback() {

        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            mainHandler.post { onServiceAdded(status) }
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            Log.d(TAG, "Connection state ${device.address}: $newState (status $status)")
            mainHandler.post {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> onDeviceConnected(device)
                    BluetoothProfile.STATE_DISCONNECTED -> onDeviceDisconnected(device)
                }
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic,
        ) {
            mainHandler.post {
                onHostActivity(device)
                respondToRead(device, requestId, offset, valueOf(device, characteristic))
            }
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor,
        ) {
            mainHandler.post {
                onHostActivity(device)
                respondToRead(device, requestId, offset, valueOf(descriptor))
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?,
        ) {
            mainHandler.post {
                onHostActivity(device)
                if (characteristic == mouseFeatureCharacteristic && value != null) {
                    // The computer is switching high-resolution scrolling on or off.
                    scrollResolution.set(
                        device.address,
                        HidDescriptor.isHighResolutionScrollRequested(value),
                    )
                }
                // Protocol Mode and HID Control Point (suspend/resume) need no action.
                respondToWrite(device, requestId, responseNeeded)
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?,
        ) {
            mainHandler.post {
                onHostActivity(device)
                if (descriptor.uuid == HidGattProfile.DESCRIPTOR_CLIENT_CONFIGURATION && value != null) {
                    clientConfigurations[descriptor] = value.copyOf()
                }
                respondToWrite(device, requestId, responseNeeded)
            }
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            mainHandler.post { onNotificationSent() }
        }
    }

    private companion object {
        const val TAG = "BleHidTransport"

        const val START_UP_TIMEOUT_MILLIS = 8_000L
        const val CONNECT_TIMEOUT_MILLIS = 15_000L
        const val PAIRING_NUDGE_DELAY_MILLIS = 3_000L
        const val NOTIFICATION_TIMEOUT_MILLIS = 250L
        const val NOTIFICATION_RETRY_MILLIS = 10L
    }
}
