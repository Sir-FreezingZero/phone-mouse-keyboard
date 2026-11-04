package io.github.phonemousekeyboard.ui.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.phonemousekeyboard.bluetooth.ConnectionState
import io.github.phonemousekeyboard.ui.theme.ConnectedGreen

/**
 * A single line at the top of the app showing who we're connected to, with one action button
 * whose meaning depends on the state (Connect / Disconnect / Retry).
 */
@Composable
fun ConnectionBar(
    state: ConnectionState,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusDot(isConnected = state is ConnectionState.Connected)

        Text(
            text = state.description(),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        when (state) {
            is ConnectionState.Connected,
            is ConnectionState.Connecting -> OutlinedButton(onClick = onDisconnectClick) {
                Text("Disconnect")
            }

            ConnectionState.Disconnected -> OutlinedButton(onClick = onConnectClick) {
                Text("Connect")
            }

            is ConnectionState.Unavailable -> OutlinedButton(onClick = onRetryClick) {
                Text("Retry")
            }

            ConnectionState.Starting -> Unit
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun StatusDot(isConnected: Boolean) {
    val color = if (isConnected) ConnectedGreen else MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(color, CircleShape),
    )
}

private fun ConnectionState.description(): String = when (this) {
    is ConnectionState.Unavailable -> reason
    ConnectionState.Starting -> "Starting Bluetooth…"
    ConnectionState.Disconnected -> "Not connected"
    is ConnectionState.Connecting -> "Connecting to $deviceName…"
    is ConnectionState.Connected -> "Connected to $deviceName"
}
