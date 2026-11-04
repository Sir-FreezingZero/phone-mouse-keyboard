package io.github.phonemousekeyboard

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.phonemousekeyboard.ui.MainScreen
import io.github.phonemousekeyboard.ui.SystemActions
import io.github.phonemousekeyboard.ui.theme.PhoneMouseKeyboardTheme

/** How long the phone stays visible to nearby computers when "Make discoverable" is tapped. */
private const val DISCOVERABLE_SECONDS = 120

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private var permissionStatus by mutableStateOf(PermissionStatus())

    /**
     * True once the user has denied a permission with "Don't ask again" (or twice). Android
     * then ignores further requests, so the Grant button opens the app's settings instead.
     */
    private var isPermissionBlocked = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        refreshPermissions()
        isPermissionBlocked = results.any { (permission, granted) ->
            !granted && !shouldShowRequestPermissionRationale(permission)
        }
    }

    // The system shows its own feedback for these, and the app reacts to the resulting
    // Bluetooth broadcasts, so the results themselves aren't needed.
    private val turnOnBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {}
    private val discoverableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {}

    private val systemActions = SystemActions(
        requestPermissions = {
            if (isPermissionBlocked) {
                val appSettings = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null),
                )
                startActivity(appSettings)
            } else {
                permissionLauncher.launch(BluetoothPermissions.all)
            }
        },
        turnOnBluetooth = {
            turnOnBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        },
        makeDiscoverable = {
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_SECONDS)
            discoverableLauncher.launch(intent)
        },
        openBluetoothSettings = { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PhoneMouseKeyboardTheme {
                MainScreen(
                    viewModel = viewModel,
                    permissions = permissionStatus,
                    systemActions = systemActions,
                )
            }
        }

        refreshPermissions()
        // Ask once on first launch; after that the Bluetooth page offers a "Grant" button.
        val isFirstCreate = savedInstanceState == null
        if (isFirstCreate && !permissionStatus.canUseBluetooth) {
            systemActions.requestPermissions()
        }
    }

    override fun onResume() {
        super.onResume()
        // The user may have changed permissions in system settings while we were away.
        refreshPermissions()
    }

    private fun refreshPermissions() {
        permissionStatus = BluetoothPermissions.status(this)
        if (permissionStatus.canUseBluetooth) {
            viewModel.onBluetoothPermissionGranted()
        }
    }
}
