package com.example.readmymi.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.readmymi.data.SensorDatabase
import com.example.readmymi.PrefsManager
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt
import com.example.readmymi.ui.getTimeFilterBounds
import com.example.readmymi.ui.getTimeBucketSize

// Simple ViewModel usage for query, or just use direct flow in composable for simplicity given no DI setup
// We will access DB from context directly here for simplicity as per existing pattern
@Composable
fun HistoryScreen(
    macAddress: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val database = remember { SensorDatabase.getDatabase(context) }
    val prefs = remember { PrefsManager(context) }
    val tempUnit = prefs.tempUnit
    
    // Time Range State: 0=Day, 1=Week, 2=Month, 3=6Months
    var timeFilter by remember { mutableStateOf(0) }
    
    val endTime = System.currentTimeMillis()
    val (startTime, _) = getTimeFilterBounds(timeFilter, endTime)
    val bucketSize = getTimeBucketSize(timeFilter)

    // Collect Data and aggregate into time buckets to eliminate zigzags and overlapping duplicates
    val historyFlow = remember(macAddress, startTime, endTime, bucketSize) {
        database.sensorDao().getHistory(macAddress, startTime, endTime)
            .map { list ->
                list.groupBy { it.timestamp / bucketSize }
                    .map { (_, group) ->
                        group.first().copy(
                            temperature = Math.round(group.map { it.temperature }.average() * 100) / 100f,
                            humidity = group.map { it.humidity }.average().roundToInt(),
                            timestamp = group.map { it.timestamp }.average().toLong()
                        )
                    }
                    .sortedBy { it.timestamp }
            }
    }
    val historyData by historyFlow.collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Button(onClick = onBack) {
            Text("Back")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text("History for $macAddress", style = MaterialTheme.typography.titleMedium)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Temperature Chart
        val tempLabel = if (tempUnit == "F") "Temperature (°F)" else "Temperature (°C)"
        Text(tempLabel, style = MaterialTheme.typography.bodyMedium)
        SensorChart(data = historyData, isTemperature = true, modifier = Modifier.weight(1f), tempUnit = tempUnit)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Humidity Chart
        Text("Humidity (%)", style = MaterialTheme.typography.bodyMedium)
        SensorChart(data = historyData, isTemperature = false, modifier = Modifier.weight(1f), tempUnit = tempUnit)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Filter Buttons
        Row(modifier = Modifier.fillMaxWidth()) {
            FilterButton(text = "Day", selected = timeFilter == 0, onClick = { timeFilter = 0 }, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(4.dp))
            FilterButton(text = "Week", selected = timeFilter == 1, onClick = { timeFilter = 1 }, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(4.dp))
            FilterButton(text = "Month", selected = timeFilter == 2, onClick = { timeFilter = 2 }, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(4.dp))
            FilterButton(text = "6 Mos", selected = timeFilter == 3, onClick = { timeFilter = 3 }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun FilterButton(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Text(text)
    }
}
