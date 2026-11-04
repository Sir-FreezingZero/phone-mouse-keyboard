package io.github.phonemousekeyboard.bluetooth

/** Everything the UI needs to know about the Bluetooth link to the host computer. */
sealed interface ConnectionState {

    /** Bluetooth is off, missing, or permissions have not been granted yet. */
    data class Unavailable(val reason: String) : ConnectionState

    /** Waiting for Android to accept our keyboard/mouse registration. */
    data object Starting : ConnectionState

    /** Registered and ready; no host computer is connected. */
    data object Disconnected : ConnectionState

    /** A connection to [deviceName] is being established. */
    data class Connecting(val deviceName: String) : ConnectionState

    /** Connected — mouse and keyboard input is being sent to [deviceName]. */
    data class Connected(val deviceName: String) : ConnectionState
}
