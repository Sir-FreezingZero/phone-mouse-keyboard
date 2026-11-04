package io.github.phonemousekeyboard

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import io.github.phonemousekeyboard.ui.MainScreen
import io.github.phonemousekeyboard.ui.theme.PhoneMouseKeyboardTheme

/** How long the phone stays visible to nearby computers when "Make discoverable" is tapped. */
private const val DISCOVERABLE_SECONDS = 120

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private var permissionMissing by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        permissionMissing = results.values.any { granted -> !granted }
        if (!permissionMissing) viewModel.startBluetooth()
    }

    private val discoverableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { /* Nothing to do; the system shows its own feedback. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PhoneMouseKeyboardTheme {
                MainScreen(
                    viewModel = viewModel,
                    permissionMissing = permissionMissing,
                    onRequestPermission = ::requestBluetoothPermissions,
                    onMakeDiscoverable = ::requestDiscoverable,
                )
            }
        }

        if (hasBluetoothPermissions()) {
            viewModel.startBluetooth()
        } else {
            requestBluetoothPermissions()
        }
    }

    private fun requestBluetoothPermissions() {
        permissionLauncher.launch(requiredPermissions())
    }

    private fun requestDiscoverable() {
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
            .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_SECONDS)
        discoverableLauncher.launch(intent)
    }

    private fun hasBluetoothPermissions(): Boolean =
        requiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }

    /** Runtime permissions only exist on Android 12+; older versions grant Bluetooth at install. */
    private fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
            )
        } else {
            emptyArray()
        }
}
