package com.example.readmymi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.SentimentDissatisfied
import androidx.compose.material.icons.rounded.SentimentSatisfied
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.readmymi.TemperatureConverter
import com.example.readmymi.PercentFormatter
import com.example.readmymi.PrefsManager
import com.example.readmymi.SensorData
import com.example.readmymi.data.SensorDatabase
import com.example.readmymi.data.SensorEntity
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    sensorData: SensorData?,
    prefs: PrefsManager,
    database: SensorDatabase,
    isServiceRunning: Boolean,
    isDownloading: Boolean,
    onRefresh: () -> Unit,
    onDownloadHistory: (Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()
    val serviceStatus by com.example.readmymi.SensorForegroundService.serviceStatus.collectAsState()
    
    if (sensorData == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Scanning for sensors...", style = MaterialTheme.typography.headlineSmall)
                if (serviceStatus.isNotBlank() && serviceStatus != "Initializing...") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(serviceStatus, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                }
            }
        }
        return
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            scope.launch {
                onRefresh()
                kotlinx.coroutines.delay(1000)
                isRefreshing = false
            }
        },
        state = pullRefreshState,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { SensorMainCard(sensorData, prefs, isServiceRunning, serviceStatus) }
            item { HistoryChartCard(sensorData.macAddress, database) }
            item { HistoryActions(isDownloading, onDownloadHistory) }
        }
    }
}

@Composable
fun SensorMainCard(
    data: SensorData,
    prefs: PrefsManager,
    isServiceRunning: Boolean,
    serviceStatus: String
) {
    // Compute mood: happy if temp & humidity are within comfort parameters:
    // Temperature: 21.0°C to 26.0°C
    // Humidity: 30.0% to 60.0%
    val tempOk = data.temperature in 21.0..26.0
    val humidityOk = data.humidity in 30.0..60.0
    val isHappy = tempOk && humidityOk
    val isOnline = isServiceRunning && !prefs.getWasOffline(data.macAddress)
    val lastUpdateTime = remember(data.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(data.timestamp))
    }
    val nextUpdateTime = remember(serviceStatus, data.timestamp, prefs.scanIntervalSeconds) {
        getNextUpdateTime(serviceStatus, data.timestamp, prefs.scanIntervalSeconds)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = prefs.getDeviceName(data.macAddress), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (isOnline) Icons.Filled.CheckCircle else Icons.Filled.CloudOff,
                    contentDescription = if (isOnline) "Online" else "Offline",
                    tint = if (isOnline) Color(0xFF4CAF50) else Color(0xFFF44336),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${if (isOnline) "Online" else "Offline"} · Last $lastUpdateTime · Next $nextUpdateTime",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 2
                )
            }
            
            val tempUnit = prefs.tempUnit
            val displayTemp = TemperatureConverter.convert(data.temperature, tempUnit)
            val tempLabel = if (tempUnit == "F") "°F" else "°C"

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DeviceThermostat, contentDescription = null, modifier = Modifier.height(48.dp))
                Text(text = String.format("%.2f", displayTemp), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold, fontSize = 64.sp)
                Text(text = tempLabel, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 4.dp))
            }

            Icon(
                imageVector = if (isHappy) Icons.Rounded.SentimentSatisfied else Icons.Rounded.SentimentDissatisfied,
                contentDescription = if (isHappy) "Conditions OK" else "Conditions out of range",
                tint = if (isHappy) Color(0xFF4CAF50) else Color(0xFFF44336),
                modifier = Modifier.size(36.dp)
            )
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatusItem(Icons.Filled.WaterDrop, PercentFormatter.format(data.humidity), "Humidity")
                StatusItem(Icons.Filled.BatteryStd, PercentFormatter.format(data.battery), "Battery", color = if (data.battery < 20) Color.Red else MaterialTheme.colorScheme.primary)
            }
            
        }
    }
}

private fun getNextUpdateTime(serviceStatus: String, lastReadingTimestamp: Long, scanIntervalSeconds: Int): String {
    val remainingSeconds = Regex("""Sleeping\.\.\. \((\d+)s\)""")
        .find(serviceStatus)
        ?.groupValues
        ?.getOrNull(1)
        ?.toLongOrNull()

    val nextUpdateMillis = if (remainingSeconds != null) {
        System.currentTimeMillis() + remainingSeconds * 1000L
    } else {
        lastReadingTimestamp + scanIntervalSeconds.coerceAtLeast(30) * 1000L
    }

    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(nextUpdateMillis))
}

@Composable
fun StatusItem(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String, color: Color = MaterialTheme.colorScheme.primary) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = label, tint = color)
        Spacer(modifier = Modifier.width(4.dp))
        Column {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun HistoryChartCard(macAddress: String, database: SensorDatabase) {
    val context = LocalContext.current
    val prefs = remember { PrefsManager(context) }
    val tempUnit = prefs.tempUnit

    var timeFilter by remember { mutableStateOf(0) }
    val endTime = System.currentTimeMillis()
    val startTime = when(timeFilter) {
        0 -> endTime - 24 * 60 * 60 * 1000L
        1 -> endTime - 7L * 24 * 60 * 60 * 1000L
        2 -> endTime - 30L * 24 * 60 * 60 * 1000L
        else -> endTime - 180L * 24 * 60 * 60 * 1000L
    }
    
    val historyData by database.sensorDao().getHistory(macAddress, startTime, endTime).collectAsState(initial = emptyList())
    
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("Day", "Week", "Month", "6 Months").forEachIndexed { index, label ->
                    FilterChip(selected = timeFilter == index, onClick = { timeFilter = index }, label = { Text(label) })
                }
            }
            
            if (historyData.isNotEmpty()) {
                SensorChart(historyData, isTemperature = true, tempUnit = tempUnit)
                Spacer(modifier = Modifier.height(8.dp))
                SensorChart(historyData, isTemperature = false, tempUnit = tempUnit)
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    Text("No history data", color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun HistoryActions(isDownloading: Boolean, onDownload: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onDownload(200) }, enabled = !isDownloading, modifier = Modifier.weight(1f)) {
                Text("Update History")
            }
            Button(onClick = { onDownload(30000) }, enabled = !isDownloading, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)) {
                Text("Full History")
            }
        }
        if (isDownloading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("Downloading history...", style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}
