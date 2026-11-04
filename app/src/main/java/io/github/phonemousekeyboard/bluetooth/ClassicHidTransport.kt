package io.github.phonemousekeyboard.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Presents the phone as a classic Bluetooth keyboard + mouse using Android's
 * [BluetoothHidDevice] API ("HID Device" profile, Android 9+).
 *
 * Some manufacturers disable this profile; [BleHidTransport] is the alternative then.
 */
@SuppressLint("MissingPermission")
class ClassicHidTransport(
    context: Context,
    private val scrollResolution: ScrollResolutionMemory,
) : HidTransport {

    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? =
        appContext.getSystemService(BluetoothManager::class.java)?.adapter

    /**
     * Bluetooth callbacks run on the main thread, the same thread as the UI calls, so the
     * fields below are only ever touched from one thread.
     */
    private val callbackExecutor = ContextCompat.getMainExecutor(appContext)
    private val mainHandler = Handler(Looper.getMainLooper())

    /** True once we've asked Android for the HID profile, until it is released again. */
    private var isProxyRequested = false

    /** Which attempt at getting the HID profile we're on, counting from 1. */
    private var proxyAttempt = 0
    private var hidDevice: BluetoothHidDevice? = null

    /** The host input is being sent to. */
    private var connectedHost: BluetoothDevice? = null

    /** A host we're in the middle of connecting to. */
    private var pendingHost: BluetoothDevice? = null

    /** True while a disconnect the user asked for is in progress, so it isn't reported as a problem. */
    private var isDisconnectRequested = false

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Starting("Waiting…"))
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    // ------------------------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------------------------

    /*
     * Start-up happens in two steps, each of which can silently never finish on some phones:
     *
     *  1. Ask Android for its HID Device service      → profileListener.onServiceConnected
     *  2. Register this app as a keyboard + mouse     → hidCallback.onAppStatusChanged
     *
     * Only after step 2 does the phone advertise itself as a keyboard/mouse. Pairing before
     * then makes the computer remember the phone as just a phone. Each step therefore has a
     * time limit, after which the user is told what went wrong and can retry.
     */

    /** Starts the keyboard/mouse service. Safe to call more than once. */
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
            isProxyRequested -> return
        }

        isProxyRequested = true
        proxyAttempt = 0
        requestProxy(adapter)
    }

    /**
     * Step 1. A refusal means the phone doesn't have the feature. No answer at all is not
     * conclusive: some phones' Bluetooth stacks are just slow or stuck, so try a few times.
     */
    private fun requestProxy(adapter: BluetoothAdapter) {
        proxyAttempt++
        startStep(
            step = "Connecting to Android's Bluetooth keyboard/mouse feature " +
                "(attempt $proxyAttempt of $MAX_PROXY_ATTEMPTS)…",
        ) {
            if (proxyAttempt < MAX_PROXY_ATTEMPTS) {
                mainHandler.postDelayed({ requestProxy(adapter) }, proxyRetryDelayMillis())
            } else {
                failStartUp(
                    "Android's classic Bluetooth keyboard/mouse feature didn't respond. " +
                        "The phone's manufacturer has probably disabled it.",
                    isFeatureMissing = true,
                )
            }
        }

        val accepted = try {
            adapter.getProfileProxy(appContext, profileListener, BluetoothProfile.HID_DEVICE)
        } catch (e: RuntimeException) {
            Log.w(TAG, "getProfileProxy threw", e)
            false
        }
        if (!accepted) {
            failStartUp(
                "This phone doesn't support the classic Bluetooth keyboard/mouse feature: " +
                    "Android refused the \"HID Device\" profile.",
                isFeatureMissing = true,
            )
        }
    }

    /** 300 ms, then 600 ms: short pauses so a slow Bluetooth stack can settle between tries. */
    private fun proxyRetryDelayMillis(): Long = PROXY_RETRY_BASE_DELAY_MILLIS * proxyAttempt

    /** Throws away everything and starts again from scratch. Used by the Retry button. */
    override fun restart() {
        hidDevice?.unregisterApp()
        releaseProxy()
        start()
    }

    /** Forgets all Bluetooth state. Call when the Bluetooth radio has been switched off. */
    override fun onBluetoothTurnedOff() {
        releaseProxy()
        _state.value = ConnectionState.Unavailable("Bluetooth is turned off.")
    }

    /** Disconnects and unregisters. Call when the app is finished with Bluetooth. */
    override fun stop() {
        hidDevice?.let { device ->
            connectedHost?.let(device::disconnect)
            device.unregisterApp()
        }
        releaseProxy()
    }

    /**
     * Registers with quality-of-service settings first, which Windows prefers. If a phone
     * rejects or ignores that, tries once more with Android's defaults before giving up.
     */
    private fun register(device: BluetoothHidDevice, useQos: Boolean = true) {
        val onFailure = {
            if (useQos) {
                Log.w(TAG, "Registration with QoS failed; retrying without it")
                device.unregisterApp()
                register(device, useQos = false)
            } else {
                failStartUp(
                    "Android didn't accept this app as a keyboard and mouse. Only one app " +
                        "can do this at a time: close other Bluetooth keyboard/mouse apps, " +
                        "turn Bluetooth off and on, then tap Retry.",
                )
            }
        }

        startStep(step = "Registering as a keyboard and mouse…", onStuck = onFailure)
        val outQos = if (useQos) QOS_OUT else null
        val accepted = try {
            device.registerApp(SDP_SETTINGS, null, outQos, callbackExecutor, hidCallback)
        } catch (e: RuntimeException) {
            Log.w(TAG, "registerApp threw", e)
            false
        }
        if (!accepted) onFailure()
    }

    /** Shows [step] as progress, and calls [onStuck] if it hasn't finished in time. */
    private fun startStep(step: String, onStuck: () -> Unit) {
        mainHandler.removeCallbacksAndMessages(null)
        _state.value = ConnectionState.Starting(step)
        mainHandler.postDelayed(
            { if (_state.value is ConnectionState.Starting) onStuck() },
            START_STEP_TIMEOUT_MILLIS,
        )
    }

    private fun finishStartUp() {
        mainHandler.removeCallbacksAndMessages(null)
        _state.value = ConnectionState.Disconnected()
    }

    private fun failStartUp(problem: String, isFeatureMissing: Boolean = false) {
        mainHandler.removeCallbacksAndMessages(null)
        Log.w(TAG, "Start-up failed: $problem")
        _state.value = ConnectionState.Unavailable(problem, isFeatureMissing)
    }

    private fun releaseProxy() {
        mainHandler.removeCallbacksAndMessages(null)
        hidDevice?.let { adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) }
        hidDevice = null
        isProxyRequested = false
        connectedHost = null
        pendingHost = null
    }

    // ------------------------------------------------------------------------------------------
    // Connecting to a host
    // ------------------------------------------------------------------------------------------

    /** Connects to a host that is already paired with this phone. */
    override fun connect(host: BluetoothDevice) {
        val device = hidDevice ?: return

        // Only one host at a time: drop the current one before switching.
        connectedHost?.takeIf { it != host }?.let(device::disconnect)

        pendingHost = host
        isDisconnectRequested = false
        _state.value = ConnectionState.Connecting(host.displayName(), host.address)
        if (!device.connect(host)) {
            pendingHost = null
            _state.value = ConnectionState.Disconnected(
                problem = "Couldn't start a connection to ${host.displayName()}.",
            )
        }
    }

    /** Disconnects from the current host, or cancels a connection in progress. */
    override fun disconnect() {
        val device = hidDevice ?: return
        val host = connectedHost ?: pendingHost ?: return
        isDisconnectRequested = true
        device.disconnect(host)
    }

    // ------------------------------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------------------------------

    override fun sendReport(reportId: Int, data: ByteArray) {
        val device = hidDevice ?: return
        val host = connectedHost ?: return
        device.sendReport(host, reportId, data)
    }

    override val wheelUnitsPerNotch: Int
        get() = scrollResolution.wheelUnitsPerNotch(connectedHost?.address)

    // ------------------------------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------------------------------

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            // A late answer to an earlier attempt, or one arriving after we gave up or
            // Bluetooth went off: we already have what we need, or no longer want it.
            if (hidDevice != null || !isProxyRequested) {
                adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, proxy)
                return
            }
            val device = proxy as BluetoothHidDevice
            hidDevice = device
            register(device)
        }

        override fun onServiceDisconnected(profile: Int) {
            releaseProxy()
            _state.value = ConnectionState.Unavailable("Bluetooth HID service stopped.")
        }
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.d(TAG, "App registered: $registered")

            if (registered) {
                finishStartUp()
                // Android may hand us the last host so we can reconnect automatically.
                pluggedDevice?.let(::connect)
                return
            }

            // "Unregistered" also arrives as a late echo of our own unregisterApp() during a
            // restart; that's harmless while starting up. Losing a working registration
            // means something else took over.
            if (_state.value !is ConnectionState.Starting) {
                connectedHost = null
                pendingHost = null
                _state.value = ConnectionState.Unavailable(
                    "Another app took over the Bluetooth keyboard/mouse feature. Tap Retry.",
                )
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            Log.d(TAG, "Connection state for ${device.displayName()}: $state")
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedHost = device
                    pendingHost = null
                    _state.value = ConnectionState.Connected(device.displayName(), device.address)
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    pendingHost = device
                    _state.value = ConnectionState.Connecting(device.displayName(), device.address)
                }
                BluetoothProfile.STATE_DISCONNECTED -> onDisconnected(device)
            }
        }

        // Hosts — Windows in particular — query and configure the device while connecting.
        // Unanswered requests can make the host give up, so every request gets a reply.

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            val hid = hidDevice ?: return
            val report = if (type == BluetoothHidDevice.REPORT_TYPE_FEATURE) {
                mouseFeatureReport(device, id.toInt())
            } else {
                // "Nothing pressed" is always a valid answer for an input device's state.
                HidDescriptor.emptyReport(id.toInt())
            }
            if (report != null) {
                hid.replyReport(device, type, id, report)
            } else {
                hid.reportError(device, BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID)
            }
        }

        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            val isMouseFeature = type == BluetoothHidDevice.REPORT_TYPE_FEATURE &&
                id.toInt() == HidDescriptor.MOUSE_REPORT_ID
            if (isMouseFeature) {
                // The computer is switching high-resolution scrolling on or off.
                scrollResolution.set(device.address, HidDescriptor.isHighResolutionScrollRequested(data))
            }
            // Anything else (e.g. keyboard LEDs) needs nothing but an acknowledgement.
            hidDevice?.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS)
        }

        override fun onSetProtocol(device: BluetoothDevice, protocol: Byte) {
            Log.d(TAG, "Host set protocol mode to $protocol")
        }
    }

    /** The mouse feature report for [device], or null if [reportId] has no feature report. */
    private fun mouseFeatureReport(device: BluetoothDevice, reportId: Int): ByteArray? =
        if (reportId == HidDescriptor.MOUSE_REPORT_ID) {
            HidDescriptor.mouseFeatureReport(scrollResolution.isHighResolution(device.address))
        } else {
            null
        }

    private fun onDisconnected(device: BluetoothDevice) {
        val wasConnecting = pendingHost == device
        val wasConnected = connectedHost == device

        if (wasConnected) connectedHost = null
        if (wasConnecting) pendingHost = null

        // When switching hosts, the old host disconnecting must not hide the connection that
        // is still in progress to the new one.
        if (connectedHost != null || pendingHost != null) return

        val problem = when {
            isDisconnectRequested -> null
            wasConnecting -> "Couldn't connect to ${device.displayName()}. Make sure it's on, " +
                "in range, and still paired with this phone."
            wasConnected -> "Lost the connection to ${device.displayName()}."
            else -> null
        }
        isDisconnectRequested = false
        _state.value = ConnectionState.Disconnected(problem)
    }

    companion object {
        private const val TAG = "ClassicHidTransport"

        /** How long each start-up step may take before it is reported as stuck. */
        private const val START_STEP_TIMEOUT_MILLIS = 8_000L

        private const val MAX_PROXY_ATTEMPTS = 3
        private const val PROXY_RETRY_BASE_DELAY_MILLIS = 300L

        private val SDP_SETTINGS = BluetoothHidDeviceAppSdpSettings(
            "Phone Mouse & Keyboard",
            "Phone used as a Bluetooth mouse and keyboard",
            "Phone Mouse & Keyboard",
            BluetoothHidDevice.SUBCLASS1_COMBO,
            HidDescriptor.REPORT_MAP,
        )

        /**
         * Quality-of-service for data sent to the host. Some hosts, including Windows, are
         * unreliable without it. Values are the ones commonly used for HID mice and keyboards:
         * best effort, 800 bytes/s, 9-byte bucket, no peak limit, 11.25 ms latency.
         */
        private val QOS_OUT = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            800,
            9,
            0,
            11_250,
            BluetoothHidDeviceAppQosSettings.MAX,
        )
    }
}
