package io.github.phonemousekeyboard.bluetooth

import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.core.content.edit
import io.github.phonemousekeyboard.bluetooth.ble.BleHidTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The app's single keyboard/mouse connection. Uses classic Bluetooth or Bluetooth Low Energy
 * ([HidMode]) and delegates everything to the matching [HidTransport].
 *
 * Classic Bluetooth is tried first. If the phone turns out not to have it, this switches to
 * Bluetooth Low Energy by itself and remembers that, so later launches start there directly.
 * The user can also switch manually.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HidConnection(context: Context, scope: CoroutineScope) : HidTransport {

    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val classic = ClassicHidTransport(context)
    private val ble = BleHidTransport(context)

    private val _mode = MutableStateFlow(loadMode())
    val mode: StateFlow<HidMode> = _mode.asStateFlow()

    private val _classicProblem = MutableStateFlow<String?>(null)

    /** Why classic Bluetooth couldn't be used, if that's why Bluetooth Low Energy is in use. */
    val classicProblem: StateFlow<String?> = _classicProblem.asStateFlow()

    private val active: HidTransport get() = transportFor(_mode.value)

    override val state: StateFlow<ConnectionState> = _mode
        .flatMapLatest { mode -> transportFor(mode).state }
        .stateIn(scope, SharingStarted.Eagerly, active.state.value)

    /** Whether [start] has been called, so a mode switch knows to start the new transport. */
    private var isStarted = false

    init {
        scope.launch {
            classic.state.collect { classicState ->
                val isMissing = classicState is ConnectionState.Unavailable &&
                    classicState.isFeatureMissing
                if (isMissing && _mode.value == HidMode.CLASSIC) {
                    _classicProblem.value = (classicState as ConnectionState.Unavailable).reason
                    switchTo(HidMode.BLE)
                }
            }
        }
    }

    /** Stops the current mode and starts [mode] instead. The choice is remembered. */
    fun switchTo(mode: HidMode) {
        if (mode == _mode.value) return
        active.stop()
        _mode.value = mode
        preferences.edit { putString(KEY_MODE, mode.name) }
        if (isStarted) active.start()
    }

    override fun start() {
        isStarted = true
        active.start()
    }

    override fun restart() {
        isStarted = true
        active.restart()
    }

    override fun onBluetoothTurnedOff() {
        classic.onBluetoothTurnedOff()
        ble.onBluetoothTurnedOff()
    }

    override fun stop() {
        isStarted = false
        classic.stop()
        ble.stop()
    }

    override fun connect(host: BluetoothDevice) = active.connect(host)

    override fun disconnect() = active.disconnect()

    override fun sendReport(reportId: Int, data: ByteArray) = active.sendReport(reportId, data)

    private fun transportFor(mode: HidMode): HidTransport = when (mode) {
        HidMode.CLASSIC -> classic
        HidMode.BLE -> ble
    }

    private fun loadMode(): HidMode =
        preferences.getString(KEY_MODE, null)
            ?.let { saved -> HidMode.entries.firstOrNull { it.name == saved } }
            ?: HidMode.CLASSIC

    private companion object {
        const val PREFERENCES_NAME = "hid_connection"
        const val KEY_MODE = "mode"
    }
}
