package com.example.readmymi.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt
import com.example.readmymi.ui.getTimeFilterBounds
import com.example.readmymi.ui.getTimeBucketSize

@OptIn(ExperimentalMaterial3Api::class)
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
                        var sumTemp = 0.0
                        var sumHum = 0.0
                        var sumTime = 0.0
                        for (item in group) {
                            sumTemp += item.temperature
                            sumHum += item.humidity
                            sumTime += item.timestamp
                        }
                        val size = group.size
                        group.first().copy(
                            temperature = Math.round((sumTemp / size) * 100) / 100f,
                            humidity = (sumHum / size).roundToInt(),
                            timestamp = (sumTime / size).toLong()
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
        
        // Time filter – MD3 SegmentedButton
        val filterLabels = listOf("Day", "Week", "Month", "6 Mos")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            filterLabels.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = timeFilter == index,
                    onClick = { timeFilter = index },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = filterLabels.size),
                ) { Text(label) }
            }
        }
    }
}
