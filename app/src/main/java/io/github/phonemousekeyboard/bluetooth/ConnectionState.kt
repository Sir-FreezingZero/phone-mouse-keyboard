package io.github.phonemousekeyboard.bluetooth

/** Everything the UI needs to know about the Bluetooth link to the host computer. */
sealed interface ConnectionState {

    /** Bluetooth is off, missing, or permissions have not been granted yet. */
    data class Unavailable(val reason: String) : ConnectionState

    /** Setting up the keyboard/mouse service; [step] describes what we're waiting for. */
    data class Starting(val step: String) : ConnectionState

    /**
     * Registered and ready; no host computer is connected.
     *
     * @param problem why the last connection attempt failed or dropped, if it did.
     */
    data class Disconnected(val problem: String? = null) : ConnectionState

    /** A connection to the host is being established. */
    data class Connecting(val deviceName: String, val deviceAddress: String) : ConnectionState

    /** Connected — mouse and keyboard input is being sent to the host. */
    data class Connected(val deviceName: String, val deviceAddress: String) : ConnectionState
}
