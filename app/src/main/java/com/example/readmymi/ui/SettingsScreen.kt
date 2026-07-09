package com.example.readmymi.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import android.database.sqlite.SQLiteException
import androidx.compose.foundation.layout.ColumnScope
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

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
    var offlineTimeoutOverride by remember { mutableStateOf(prefs.offlineTimeoutOverride) }
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
            String.format(Locale.US, "%.1f", TemperatureConverter.convert(highC.toDouble(), "F"))
        } else {
            String.format(Locale.US, "%.1f", highC)
        }
        alertTempLow = if (tempUnit == "F") {
            String.format(Locale.US, "%.1f", TemperatureConverter.convert(lowC.toDouble(), "F"))
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
                            } catch (e: SQLiteException) {
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
            .padding(16.dp),
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
                            .background(MaterialTheme.colorScheme.surfaceContainer)
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
                    "Friendly name shown on dashboard instead of MAC address",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, top = 2.dp)
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
                    val isCelsius = tempUnit == "C"
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = isCelsius,
                            onClick = { tempUnit = "C"; prefs.tempUnit = "C" },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        ) { Text("Celsius (°C)") }
                        SegmentedButton(
                            selected = !isCelsius,
                            onClick = { tempUnit = "F"; prefs.tempUnit = "F" },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        ) { Text("Fahrenheit (°F)") }
                    }
                    Text(
                        "Choose how temperature readings are displayed throughout the app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                    )
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
                Text(
                    "How often the app checks for sensor updates (minimum 30s). Shorter = more responsive, longer = better battery",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, top = 2.dp)
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
                Text(
                    "When to download recorded history from the sensor to fill in gaps",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                )
                
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                
                ListItem(
                    headlineContent = { Text("Ongoing Status Notification") },
                    supportingContent = { Text("Show service status in notification drawer") },
                    trailingContent = {
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
                    },
                    modifier = Modifier.fillMaxWidth()
                )
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
                
                ListItem(
                    headlineContent = { Text("Enable Alerts System") },
                    supportingContent = { Text("Get notified when sensor readings cross your configured thresholds") },
                    trailingContent = {
                        Switch(checked = alertsEnabled, onCheckedChange = { alertsEnabled = it; prefs.alertsEnabled = it })
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (alertsEnabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    
                    ListItem(
                        headlineContent = { Text("Alert Vibration") },
                        supportingContent = { Text("Vibrate the device when an alert is triggered") },
                        trailingContent = {
                            Switch(checked = alertVibrationEnabled, onCheckedChange = {
                                alertVibrationEnabled = it
                                prefs.alertVibrationEnabled = it
                            })
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
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
                    Text(
                        "Notify when temperature rises above this value",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp)
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
                    Text(
                        "Notify when temperature drops below this value",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp)
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
                    Text(
                        "Notify when humidity rises above this percentage",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp)
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
                    Text(
                        "Notify when humidity drops below this percentage",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp)
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
                    Text(
                        "Notify when the sensor battery falls below this level",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp)
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
                            imageVector = Icons.Filled.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text("Offline Detection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    ListItem(
                        headlineContent = { Text("Override Auto Timeout") },
                        supportingContent = {
                            val autoMins = (prefs.scanIntervalSeconds * 2 / 60).coerceAtLeast(1)
                            Text(
                                if (offlineTimeoutOverride)
                                    "Manual: ${prefs.effectiveOfflineTimeoutMinutes} min"
                                else
                                    "Auto: scan interval × 2 = ${autoMins} min"
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = offlineTimeoutOverride,
                                onCheckedChange = {
                                    offlineTimeoutOverride = it
                                    prefs.offlineTimeoutOverride = it
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Manually set how long before a sensor is marked offline instead of using auto-calculated timeout",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                    )
                    if (offlineTimeoutOverride) {
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
                        Text(
                            "Sensor is marked offline if no data received for this long",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, top = 2.dp)
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
                    Text(
                        "Automatically delete history older than this many days to save storage",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp)
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
                                    val dateObj = Date()
                                    for (item in history) {
                                        dateObj.time = item.timestamp
                                        val dateTime = sdf.format(dateObj)
                                        val tempF = item.temperature * 1.8f + 32f
                                        csvBuilder.append(dateTime).append(",").append(item.timestamp).append(",").append(item.macAddress).append(",").append(item.temperature).append(",").append(tempF).append(",").append(item.humidity).append(",").append(item.battery).append("\n")
                                    }
                                    val csvString = csvBuilder.toString()
                                    val csvFile = File(context.cacheDir, "sensor_export_${lastMac.replace(":", "_")}.csv")
                                    FileOutputStream(csvFile).use { it.write(csvString.toByteArray()) }
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", csvFile)
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/csv"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(Intent.EXTRA_SUBJECT, "ReadMyMi Sensor Export - ${prefs.getDeviceName(lastMac)}")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
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
                Box(modifier = Modifier.fillMaxWidth().height(200.dp).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(8.dp)) {
                    LazyColumn {
                        items(logs.asReversed()) { log ->
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
        ListItem(
            headlineContent = { Text(label) },
            trailingContent = {
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            },
            modifier = Modifier.fillMaxWidth()
        )
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(spacing.dp), content = content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownSelector(
    label: String,
    options: List<T>,
    selectedOption: T,
    optionToString: (T) -> String,
    onOptionSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = optionToString(selectedOption),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
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
}
