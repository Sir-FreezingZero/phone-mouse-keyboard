package io.github.phonemousekeyboard

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import io.github.phonemousekeyboard.bluetooth.BluetoothHidController
import io.github.phonemousekeyboard.bluetooth.ConnectionState
import io.github.phonemousekeyboard.input.MouseButton
import io.github.phonemousekeyboard.input.SpecialKeys
import io.github.phonemousekeyboard.input.TextDiff
import io.github.phonemousekeyboard.input.UsKeyboardLayout
import kotlinx.coroutines.flow.StateFlow

/**
 * Owns the Bluetooth controller so the connection survives screen rotation, and translates
 * UI events (finger movement, text edits) into HID input.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val controller = BluetoothHidController(application)

    val connectionState: StateFlow<ConnectionState> = controller.state

    /** Call once Bluetooth permissions are granted. */
    fun startBluetooth() = controller.start()

    // ---- Connection ----

    fun pairedDevices(): List<BluetoothDevice> = controller.pairedDevices()

    fun connect(device: BluetoothDevice) = controller.connect(device)

    fun disconnect() = controller.disconnect()

    // ---- Mouse ----

    fun moveMouse(dx: Int, dy: Int) = controller.moveMouse(dx, dy)

    fun scroll(notches: Int) = controller.moveMouse(dx = 0, dy = 0, wheel = notches)

    fun pressMouseButton(button: MouseButton) = controller.pressMouseButton(button)

    fun releaseMouseButton(button: MouseButton) = controller.releaseMouseButton(button)

    fun clickMouseButton(button: MouseButton) = controller.clickMouseButton(button)

    // ---- Keyboard ----

    /** Sends whatever key presses are needed to turn [oldText] into [newText] on the host. */
    fun sendTextChange(oldText: String, newText: String) {
        val diff = TextDiff.between(oldText, newText)

        repeat(diff.deletions) { controller.typeKey(SpecialKeys.BACKSPACE) }

        for (char in diff.insertion) {
            // Characters with no key on a US layout (e.g. emoji) are skipped.
            UsKeyboardLayout.keyStrokeFor(char)?.let(controller::typeKey)
        }
    }

    override fun onCleared() {
        controller.stop()
    }
}
