package com.example.readmymi.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
        SettingsSectionHeader("Connected Device", Icons.Default.Bluetooth)

        if (lastMac.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = prefs.getDeviceName(lastMac),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "MAC: $lastMac",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val lastSync = prefs.getLastTimeSync(lastMac)
                if (lastSync > 0) {
                    Text(
                        text = "Last time sync: ${dateFormatter.format(Instant.ofEpochMilli(lastSync))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        newDeviceName = prefs.getDeviceName(lastMac)
                        showDeviceNameDialog = true
                    },
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Rename", maxLines = 1)
                }
                Button(
                    onClick = onClearLastMac,
                    modifier = Modifier.weight(1f).height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Rescan", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Rescan", maxLines = 1)
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
        SettingsSectionHeader("Background Service", Icons.Default.PowerSettingsNew)

        val serviceColor = if (isServiceRunning) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.primary

        Button(
            onClick = { if (isServiceRunning) onStopService() else onStartService() },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = serviceColor)
        ) {
            Icon(
                imageVector = if (isServiceRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isServiceRunning) "Stop Foreground Service" else "Start Foreground Service")
        }

        OutlinedButton(
            onClick = onShowAbout,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("About Read My Mi")
        }
    }
}
