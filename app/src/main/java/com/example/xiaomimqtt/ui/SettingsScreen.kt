package com.example.xiaomimqtt.ui

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xiaomimqtt.AppLogger
import com.example.xiaomimqtt.PrefsManager
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
    onShowDebug: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
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
            var alertsEnabled by remember { mutableStateOf(prefs.alertsEnabled) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = alertsEnabled, onCheckedChange = { alertsEnabled = it; prefs.alertsEnabled = it })
                Spacer(modifier = Modifier.width(8.dp))
                Text("Enable Alerts System")
            }
        }
        
        if (prefs.alertsEnabled) {
            item { AlertSetting("High Temperature (> °C)", prefs.alertTempHigh.toString()) { prefs.alertTempHigh = it.toFloat(); prefs.alertTempHighEnabled = true } }
            item { AlertSetting("Low Temperature (< °C)", prefs.alertTempLow.toString()) { prefs.alertTempLow = it.toFloat(); prefs.alertTempLowEnabled = true } }
            item { AlertSetting("High Humidity (> %)", prefs.alertHumidityHigh.toString()) { prefs.alertHumidityHigh = it.toInt(); prefs.alertHumidityHighEnabled = true } }
            item { AlertSetting("Low Humidity (< %)", prefs.alertHumidityLow.toString()) { prefs.alertHumidityLow = it.toInt(); prefs.alertHumidityLowEnabled = true } }
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
            Text("Logs", style = MaterialTheme.typography.titleMedium)
            val logs by AppLogger.logs.collectAsState()
            Box(modifier = Modifier.fillMaxWidth().height(200.dp).background(Color.Black.copy(0.05f)).padding(8.dp)) {
                LazyColumn {
                    items(logs) { log ->
                        Text(log, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    }
                }
            }
        }
        
        item {
            Button(onClick = onShowDebug, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)) {
                Text("Advanced / Debug Menu")
            }
        }
    }
}

@Composable
fun AlertSetting(label: String, value: String, onValueChange: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onValueChange(it) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}
