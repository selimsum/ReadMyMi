package com.example.readmymi.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.readmymi.PrefsManager
import com.example.readmymi.ui.SettingsCard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Device card: connected-device name/time-sync display + rename dialog. */
@Composable
fun DeviceCard(
    prefs: PrefsManager,
    lastMac: String,
    onClearLastMac: () -> Unit
) {
    var showDeviceNameDialog by remember { mutableStateOf(false) }
    var newDeviceName by remember { mutableStateOf("") }
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.getDefault()).withZone(ZoneId.systemDefault())
    }

    SettingsCard {
        if (lastMac.isNotEmpty()) {
            Text("Connected Device: ${prefs.getDeviceName(lastMac)}", style = MaterialTheme.typography.bodyLarge)
            val lastSync = prefs.getLastTimeSync(lastMac)
            if (lastSync > 0) {
                Text(
                    "Last Time Sync: ${dateFormatter.format(Instant.ofEpochMilli(lastSync))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        newDeviceName = prefs.getDeviceName(lastMac)
                        showDeviceNameDialog = true
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Rename Device") }
                Button(
                    onClick = onClearLastMac,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Rescan")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Rescan")
                }
            }
        } else {
            Text("No device connected", style = MaterialTheme.typography.bodyLarge)
        }
    }

    if (showDeviceNameDialog) {
        AlertDialog(
            onDismissRequest = { showDeviceNameDialog = false },
            title = { Text("Rename Device") },
            text = {
                OutlinedTextField(
                    value = newDeviceName,
                    onValueChange = { newDeviceName = it },
                    label = { Text("Device Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newDeviceName.isNotBlank()) {
                            prefs.setDeviceName(lastMac, newDeviceName.trim())
                        }
                        showDeviceNameDialog = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showDeviceNameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/** Service card: start/stop foreground service + About entry point. */
@Composable
fun ServiceCard(
    isServiceRunning: Boolean,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onShowAbout: () -> Unit
) {
    SettingsCard {
        val color = if (isServiceRunning) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.primary
        Button(
            onClick = { if (isServiceRunning) onStopService() else onStartService() },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = color)
        ) {
            Text(if (isServiceRunning) "Stop Foreground Service" else "Start Foreground Service")
        }
        Button(
            onClick = onShowAbout,
            modifier = Modifier.fillMaxWidth().height(50.dp).padding(top = 0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text("About Read My Mi")
        }
    }
}
