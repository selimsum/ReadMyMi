package com.example.xiaomimqtt.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xiaomimqtt.PrefsManager
import com.example.xiaomimqtt.SensorData
import com.example.xiaomimqtt.data.SensorDatabase
import com.example.xiaomimqtt.data.SensorEntity
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
    val pullRefreshState = rememberPullToRefreshState()
    
    if (sensorData == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Scanning for sensors...", style = MaterialTheme.typography.headlineSmall)
        }
        return
    }

    PullToRefreshBox(
        isRefreshing = false, // Managed externally if needed
        onRefresh = onRefresh,
        state = pullRefreshState,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { SensorMainCard(sensorData, prefs) }
            item { HistoryChartCard(sensorData.macAddress, database) }
            item { HistoryActions(isDownloading, onDownloadHistory) }
        }
    }
}

@Composable
fun SensorMainCard(data: SensorData, prefs: PrefsManager) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = prefs.getDeviceName(data.macAddress), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DeviceThermostat, contentDescription = null, modifier = Modifier.height(48.dp))
                Text(text = String.format("%.2f", data.temperature), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold, fontSize = 64.sp)
                Text(text = "°C", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 4.dp))
            }
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatusItem(Icons.Filled.WaterDrop, String.format("%.1f%%", data.humidity), "Humidity")
                StatusItem(Icons.Filled.BatteryStd, "${data.battery}%", "Battery", color = if (data.battery < 20) Color.Red else MaterialTheme.colorScheme.primary)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(data.timestamp))
            Text("Last updated: $time", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
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
    var timeFilter by remember { mutableStateOf(0) }
    val endTime = System.currentTimeMillis()
    val startTime = when(timeFilter) {
        0 -> endTime - 24 * 60 * 60 * 1000L
        1 -> endTime - 30L * 24 * 60 * 60 * 1000L
        else -> endTime - 180L * 24 * 60 * 60 * 1000L
    }
    
    val historyData by database.sensorDao().getHistory(macAddress, startTime, endTime).collectAsState(initial = emptyList())
    
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("Day", "Month", "6 Months").forEachIndexed { index, label ->
                    FilterChip(selected = timeFilter == index, onClick = { timeFilter = index }, label = { Text(label) })
                }
            }
            
            if (historyData.isNotEmpty()) {
                SensorChart(historyData, isTemperature = true)
                Spacer(modifier = Modifier.height(8.dp))
                SensorChart(historyData, isTemperature = false)
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
