package com.example.readmymi.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.readmymi.AppLogger
import com.example.readmymi.PrefsManager
import android.widget.Toast
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.example.readmymi.SensorForegroundService
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.readmymi.TemperatureConverter
import com.example.readmymi.data.SensorDatabase
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.ColumnScope

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
    val scope = rememberCoroutineScope()
    val database = remember { SensorDatabase.getDatabase(context) }
    
    var tempUnit by remember { mutableStateOf(prefs.tempUnit) }
    var alertsEnabled by remember { mutableStateOf(prefs.alertsEnabled) }
    var alertTempHighEnabled by remember { mutableStateOf(prefs.alertTempHighEnabled) }
    var alertTempLowEnabled by remember { mutableStateOf(prefs.alertTempLowEnabled) }
    var alertHumidityHighEnabled by remember { mutableStateOf(prefs.alertHumidityHighEnabled) }
    var alertHumidityLowEnabled by remember { mutableStateOf(prefs.alertHumidityLowEnabled) }
    var alertTempHigh by remember { mutableStateOf("") }
    var alertTempLow by remember { mutableStateOf("") }
    var alertHumidityHigh by remember { mutableStateOf(prefs.alertHumidityHigh.toString()) }
    var alertHumidityLow by remember { mutableStateOf(prefs.alertHumidityLow.toString()) }
    
    var alertBatteryLowEnabled by remember { mutableStateOf(prefs.alertBatteryLowEnabled) }
    var alertBatteryLow by remember { mutableStateOf(prefs.alertBatteryLow.toString()) }
    var offlineTimeout by remember { mutableStateOf(prefs.offlineTimeoutMinutes.toString()) }
    var alertVibrationEnabled by remember { mutableStateOf(prefs.alertVibrationEnabled) }
    var autoPruningDays by remember { mutableStateOf(prefs.autoPruningDays) }
    var autoHistorySyncMode by remember { mutableStateOf(prefs.autoHistorySyncMode) }
    var ongoingNotificationEnabled by remember { mutableStateOf(prefs.ongoingNotificationEnabled) }
    var showWipeConfirmation by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }

    LaunchedEffect(tempUnit) {
        val highC = prefs.alertTempHigh
        val lowC = prefs.alertTempLow
        alertTempHigh = if (tempUnit == "F") {
            String.format(Locale.US, "%.1f", TemperatureConverter.convert(highC, "F"))
        } else {
            String.format(Locale.US, "%.1f", highC)
        }
        alertTempLow = if (tempUnit == "F") {
            String.format(Locale.US, "%.1f", TemperatureConverter.convert(lowC, "F"))
        } else {
            String.format(Locale.US, "%.1f", lowC)
        }
    }

    androidx.activity.compose.BackHandler { onBack() }
    
    if (showWipeConfirmation) {
        AlertDialog(
            onDismissRequest = { showWipeConfirmation = false },
            title = { Text("Wipe Database") },
            text = { Text("Are you sure you want to delete all stored sensor history? This action cannot be undone.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        showWipeConfirmation = false
                        scope.launch {
                            try {
                                database.sensorDao().deleteAll()
                                Toast.makeText(context, "Database cleared successfully", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) {
                    Text("Wipe All Data")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Settings",
                        style = TextStyle(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            ),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 28.sp
                        )
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    thickness = 1.dp
                )
            }
        }
        
        item {
            SettingsCard(spacing = 12) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bluetooth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Device Connection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                
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
                    TextButton(
                        onClick = onClearLastMac,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Forget Device")
                    }
                }
            }
        }

        item {
            SettingsCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Preferences", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                
                Column {
                    Text("Temperature Display Unit", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isCelsius = tempUnit == "C"
                        Button(
                            onClick = {
                                tempUnit = "C"
                                prefs.tempUnit = "C"
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isCelsius) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isCelsius) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text("Celsius (°C)")
                        }
                        Button(
                            onClick = {
                                tempUnit = "F"
                                prefs.tempUnit = "F"
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!isCelsius) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (!isCelsius) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text("Fahrenheit (°F)")
                        }
                    }
                }
                
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
                
                val syncModes = listOf("off", "start", "12h", "24h")
                val syncLabels = mapOf(
                    "off" to "Manual (Disabled)",
                    "start" to "On Connection",
                    "12h" to "Every 12 Hours",
                    "24h" to "Daily (Every 24 Hours)"
                )
                DropdownSelector(
                    label = "Auto-History Sync Interval",
                    options = syncModes,
                    selectedOption = autoHistorySyncMode,
                    optionToString = { syncLabels[it] ?: it },
                    onOptionSelected = {
                        autoHistorySyncMode = it
                        prefs.autoHistorySyncMode = it
                    }
                )
                
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Ongoing Status Notification", style = MaterialTheme.typography.bodyLarge)
                        Text("Show service status in notification drawer", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = ongoingNotificationEnabled,
                        onCheckedChange = {
                            ongoingNotificationEnabled = it
                            prefs.ongoingNotificationEnabled = it
                            if (isServiceRunning) {
                                val intent = Intent(context, SensorForegroundService::class.java).apply {
                                    action = "UPDATE_NOTIFICATION"
                                }
                                context.startService(intent)
                            }
                        }
                    )
                }
            }
        }

        item {
            SettingsCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Alerts & Notifications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Enable Alerts System", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = alertsEnabled, onCheckedChange = { alertsEnabled = it; prefs.alertsEnabled = it })
                }
                
                if (alertsEnabled) {
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Alert Vibration", style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = alertVibrationEnabled, onCheckedChange = { 
                            alertVibrationEnabled = it
                            prefs.alertVibrationEnabled = it
                        })
                    }
                    
                    AlertThresholdRow(
                        label = "High Temperature (°$tempUnit)",
                        enabled = alertTempHighEnabled,
                        value = alertTempHigh,
                        keyboardType = KeyboardType.Decimal,
                        onEnabledChange = {
                            alertTempHighEnabled = it
                            prefs.alertTempHighEnabled = it
                        },
                        onValueChange = {
                            alertTempHigh = it
                            it.toFloatOrNull()?.let { v ->
                                val cValue = if (tempUnit == "F") (v - 32f) / 1.8f else v
                                prefs.alertTempHigh = cValue
                            }
                        }
                    )
                    
                    AlertThresholdRow(
                        label = "Low Temperature (°$tempUnit)",
                        enabled = alertTempLowEnabled,
                        value = alertTempLow,
                        keyboardType = KeyboardType.Decimal,
                        onEnabledChange = {
                            alertTempLowEnabled = it
                            prefs.alertTempLowEnabled = it
                        },
                        onValueChange = {
                            alertTempLow = it
                            it.toFloatOrNull()?.let { v ->
                                val cValue = if (tempUnit == "F") (v - 32f) / 1.8f else v
                                prefs.alertTempLow = cValue
                            }
                        }
                    )
                    
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
                    
                    AlertThresholdRow(
                        label = "Low Battery Threshold (%)",
                        enabled = alertBatteryLowEnabled,
                        value = alertBatteryLow,
                        keyboardType = KeyboardType.Number,
                        onEnabledChange = {
                            alertBatteryLowEnabled = it
                            prefs.alertBatteryLowEnabled = it
                        },
                        onValueChange = {
                            alertBatteryLow = it
                            it.toIntOrNull()?.let { v -> prefs.alertBatteryLow = v }
                        }
                    )
                    
                    OutlinedTextField(
                        value = offlineTimeout,
                        onValueChange = {
                            offlineTimeout = it
                            it.toIntOrNull()?.let { v -> prefs.offlineTimeoutMinutes = v }
                        },
                        label = { Text("Offline Timeout (Minutes)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { showAdvanced = !showAdvanced },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (showAdvanced) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (showAdvanced) "Hide Advanced Options" else "Show Advanced Options")
            }
        }

        if (showAdvanced) {
            item {
                SettingsCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text("Database & Maintenance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    
                    val pruningOptions = listOf(0, 7, 30, 90)
                    val pruningLabels = mapOf(
                        0 to "Disabled",
                        7 to "7 Days",
                        30 to "30 Days",
                        90 to "90 Days"
                    )
                    DropdownSelector(
                        label = "Auto-Prune Data Older Than",
                        options = pruningOptions,
                        selectedOption = autoPruningDays,
                        optionToString = { pruningLabels[it] ?: "$it Days" },
                        onOptionSelected = {
                            autoPruningDays = it
                            prefs.autoPruningDays = it
                        }
                    )
                    
                    Button(
                        onClick = {
                            if (lastMac.isEmpty()) {
                                Toast.makeText(context, "No device selected to export", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            scope.launch {
                                try {
                                    val history = database.sensorDao().getAllHistoryDirect(lastMac)
                                    if (history.isEmpty()) {
                                        Toast.makeText(context, "No history data found for this device", Toast.LENGTH_SHORT).show()
                                        return@launch
                                    }
                                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                                    val csvBuilder = StringBuilder()
                                    csvBuilder.append("DateTime,Timestamp,MAC Address,Temperature(C),Temperature(F),Humidity(%),Battery(%)\n")
                                    for (item in history) {
                                        val dateTime = sdf.format(Date(item.timestamp))
                                        val tempF = item.temperature * 1.8f + 32f
                                        csvBuilder.append("$dateTime,${item.timestamp},${item.macAddress},${item.temperature},$tempF,${item.humidity},${item.battery}\n")
                                    }
                                    val csvString = csvBuilder.toString()
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/csv"
                                        putExtra(Intent.EXTRA_SUBJECT, "ReadMyMi Sensor Export - ${prefs.getDeviceName(lastMac)}")
                                        putExtra(Intent.EXTRA_TEXT, csvString)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Export Sensor Data"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export Device History (CSV)")
                    }
                    
                    OutlinedButton(
                        onClick = { showWipeConfirmation = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error))
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Wipe All Stored Data")
                    }
                }
            }

            item {
                val logs by AppLogger.logs.collectAsState()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("App Logs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    IconButton(
                        onClick = {
                            val logText = logs.joinToString(separator = "\n")
                            clipboardManager.setText(AnnotatedString(logText))
                            Toast.makeText(context, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
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

        item {
            val color = if (isServiceRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            Button(
                onClick = { if (isServiceRunning) onStopService() else onStartService() },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = color)
            ) {
                Text(if (isServiceRunning) "Stop Foreground Service" else "Start Foreground Service")
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
                label = { Text("Threshold Value") },
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun SettingsCard(spacing: Int = 16, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(spacing.dp), content = content)
    }
}

@Composable
fun <T> DropdownSelector(
    label: String,
    options: List<T>,
    selectedOption: T,
    optionToString: (T) -> String,
    onOptionSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = optionToString(selectedOption),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                        contentDescription = "Toggle dropdown"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { expanded = true }
        )
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        modifier = Modifier.fillMaxWidth()
    ) {
        options.forEach { option ->
            DropdownMenuItem(
                text = { Text(optionToString(option)) },
                onClick = {
                    onOptionSelected(option)
                    expanded = false
                }
            )
        }
    }
}
