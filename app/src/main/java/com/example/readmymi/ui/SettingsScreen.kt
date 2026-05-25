package com.example.readmymi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.readmymi.AppLogger
import com.example.readmymi.PrefsManager
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefs: PrefsManager,
    lastMac: String,
    isServiceRunning: Boolean,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onShowAbout: () -> Unit,
    onClearLastMac: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var alertsEnabled by remember { mutableStateOf(prefs.alertsEnabled) }
    var alertTempHighEnabled by remember { mutableStateOf(prefs.alertTempHighEnabled) }
    var alertTempLowEnabled by remember { mutableStateOf(prefs.alertTempLowEnabled) }
    var alertHumidityHighEnabled by remember { mutableStateOf(prefs.alertHumidityHighEnabled) }
    var alertHumidityLowEnabled by remember { mutableStateOf(prefs.alertHumidityLowEnabled) }
    var alertTempHigh by remember { mutableStateOf(prefs.alertTempHigh.toString()) }
    var alertTempLow by remember { mutableStateOf(prefs.alertTempLow.toString()) }
    var alertHumidityHigh by remember { mutableStateOf(prefs.alertHumidityHigh.toString()) }
    var alertHumidityLow by remember { mutableStateOf(prefs.alertHumidityLow.toString()) }
    androidx.activity.compose.BackHandler { onBack() }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount > 50) onBack()
                }
            },
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Settings", style = MaterialTheme.typography.titleLarge)
            }
        }
        
        item {
            Text("Device", style = MaterialTheme.typography.titleMedium)
            var devName by remember { mutableStateOf(prefs.getDeviceName(lastMac)) }
            OutlinedTextField(
                value = devName,
                onValueChange = { 
                    devName = it
                    if (lastMac.isNotEmpty()) prefs.setDeviceName(lastMac, it)
                },
                label = { Text("Device Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                if (lastMac.isNotEmpty()) "MAC: $lastMac" else "No device selected yet.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp)
            )
            if (lastMac.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = onClearLastMac,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text("Forget Device")
                }
            }
        }

        item {
            Text("Periodic Scanning & Alerts", style = MaterialTheme.typography.titleMedium)
            var scanInterval by remember { mutableStateOf(prefs.scanIntervalSeconds.toString()) }
            OutlinedTextField(
                value = scanInterval,
                onValueChange = { 
                    scanInterval = it
                    it.toIntOrNull()?.let { v -> prefs.scanIntervalSeconds = v }
                },
                label = { Text("Scan Interval (Seconds)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = alertsEnabled, onCheckedChange = { alertsEnabled = it; prefs.alertsEnabled = it })
                Spacer(modifier = Modifier.width(8.dp))
                Text("Enable Alerts System")
            }
        }
        
        if (alertsEnabled) {
            item {
                AlertThresholdRow(
                    label = "High Temperature (°C)",
                    enabled = alertTempHighEnabled,
                    value = alertTempHigh,
                    keyboardType = KeyboardType.Decimal,
                    onEnabledChange = {
                        alertTempHighEnabled = it
                        prefs.alertTempHighEnabled = it
                    },
                    onValueChange = {
                        alertTempHigh = it
                        it.toFloatOrNull()?.let { v -> prefs.alertTempHigh = v }
                    }
                )
            }
            item {
                AlertThresholdRow(
                    label = "Low Temperature (°C)",
                    enabled = alertTempLowEnabled,
                    value = alertTempLow,
                    keyboardType = KeyboardType.Decimal,
                    onEnabledChange = {
                        alertTempLowEnabled = it
                        prefs.alertTempLowEnabled = it
                    },
                    onValueChange = {
                        alertTempLow = it
                        it.toFloatOrNull()?.let { v -> prefs.alertTempLow = v }
                    }
                )
            }
            item {
                AlertThresholdRow(
                    label = "High Humidity (%)",
                    enabled = alertHumidityHighEnabled,
                    value = alertHumidityHigh,
                    keyboardType = KeyboardType.Number,
                    onEnabledChange = {
                        alertHumidityHighEnabled = it
                        prefs.alertHumidityHighEnabled = it
                    },
                    onValueChange = {
                        alertHumidityHigh = it
                        it.toIntOrNull()?.let { v -> prefs.alertHumidityHigh = v }
                    }
                )
            }
            item {
                AlertThresholdRow(
                    label = "Low Humidity (%)",
                    enabled = alertHumidityLowEnabled,
                    value = alertHumidityLow,
                    keyboardType = KeyboardType.Number,
                    onEnabledChange = {
                        alertHumidityLowEnabled = it
                        prefs.alertHumidityLowEnabled = it
                    },
                    onValueChange = {
                        alertHumidityLow = it
                        it.toIntOrNull()?.let { v -> prefs.alertHumidityLow = v }
                    }
                )
            }
        }

        item {
            val color = if (isServiceRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            Button(
                onClick = { if (isServiceRunning) onStopService() else onStartService() },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = color)
            ) {
                Text(if (isServiceRunning) "Stop Service" else "Start Service")
            }
        }

        item {
            Button(
                onClick = onShowAbout,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("About Read My Mi")
            }
        }

        item {
            val logs by AppLogger.logs.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Logs", style = MaterialTheme.typography.titleMedium)
                IconButton(
                    onClick = {
                        val logText = logs.joinToString(separator = "\n")
                        clipboardManager.setText(AnnotatedString(logText))
                        Toast.makeText(context, "Logs copied", Toast.LENGTH_SHORT).show()
                    },
                    enabled = logs.isNotEmpty()
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy logs")
                }
            }
            Box(modifier = Modifier.fillMaxWidth().height(200.dp).background(Color.Black.copy(0.05f)).padding(8.dp)) {
                LazyColumn {
                    items(logs) { log ->
                        Text(log, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun AlertThresholdRow(
    label: String,
    enabled: Boolean,
    value: String,
    keyboardType: KeyboardType,
    onEnabledChange: (Boolean) -> Unit,
    onValueChange: (String) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        if (enabled) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("Threshold value") },
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
