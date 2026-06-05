package com.example.readmymi.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.MaterialTheme
import com.example.readmymi.ui.theme.md_chartTemperature
import com.example.readmymi.ui.theme.md_chartHumidity
import lecho.lib.hellocharts.model.*
import lecho.lib.hellocharts.view.LineChartView
import com.example.readmymi.data.SensorEntity
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun SensorChart(
    data: List<SensorEntity>,
    isTemperature: Boolean, // true for Temp, false for Humidity
    modifier: Modifier = Modifier,
    tempUnit: String = "C"
) {
    if (data.isEmpty()) return

    val themeColor = if (isTemperature) md_chartTemperature.toArgb() else md_chartHumidity.toArgb()
    val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f).toArgb()

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(top = 16.dp, bottom = 12.dp),
        factory = { context ->
            LineChartView(context).apply {
                isInteractive = false
                isValueSelectionEnabled = false
            }
        },
        update = { chartView ->
            val sampledData = if (data.size > 500) {
                val rate = data.size / 250
                data.filterIndexed { index, _ -> index % rate == 0 || index == data.lastIndex }
            } else {
                data
            }

            val minTime = if (sampledData.isNotEmpty()) sampledData.first().timestamp else 0L
            val maxTime = if (sampledData.isNotEmpty()) sampledData.last().timestamp else 0L
            val duration = maxTime - minTime

            val timeFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.getDefault()).withZone(ZoneId.systemDefault())

            val pointsData = sampledData.mapIndexed { index, item ->
                val yRaw = if (isTemperature) {
                    com.example.readmymi.TemperatureConverter.convert(item.temperature.toDouble(), tempUnit).toFloat()
                } else {
                    item.humidity.toFloat()
                }
                val xRaw = if (duration > 0) {
                    ((item.timestamp - minTime).toDouble() / duration * (sampledData.size - 1)).toFloat()
                } else {
                    index.toFloat()
                }
                val unit = if (isTemperature) (if (tempUnit == "F") "°F" else "°C") else "%"
                val timeStr = timeFormatter.format(Instant.ofEpochMilli(item.timestamp))
                val labelText = "${String.format(Locale.getDefault(), "%.1f", yRaw)}$unit ($timeStr)"
                PointValue(xRaw, yRaw).setLabel(labelText.toCharArray())
            }

            // Chart is display-only – no touch interaction

            // Ensure points are technically present for rendering, but minimal if dense
            val line = Line(pointsData).apply {
                color = themeColor
                isCubic = true
                setHasPoints(sampledData.size <= 100) 
                pointRadius = 4
                strokeWidth = if (sampledData.size > 200) 1 else 2
                setHasLabels(false)
                setHasLabelsOnlyForSelected(true) // Bubble appears on selection
            }

            val lines = listOf(line)
            val chartData = LineChartData(lines)

            val formatStr = if (data.isNotEmpty() && data.last().timestamp - data.first().timestamp > 86400000L) "dd/MM" else "HH:mm"
            val dateFormat = SimpleDateFormat(formatStr, Locale.US)
            val axisXValues = mutableListOf<AxisValue>()
            
            if (sampledData.isNotEmpty()) {
                val numLabels = if (sampledData.size < 6) sampledData.size else 6
                val targetTimes = (0 until numLabels).map { i ->
                    if (numLabels > 1) {
                        minTime + (duration * i) / (numLabels - 1)
                    } else {
                        minTime
                    }
                }
                
                val sharedDate = Date()
                for (targetTime in targetTimes) {
                    val labelX = if (duration > 0) {
                        ((targetTime - minTime).toDouble() / duration * (sampledData.size - 1)).toFloat()
                    } else {
                        0f
                    }
                    sharedDate.time = targetTime
                    axisXValues.add(AxisValue(labelX).setLabel(dateFormat.format(sharedDate)))
                }
            }

            val axisX = Axis(axisXValues).apply {
                textColor = labelColor
                textSize = 10
                setHasLines(true)
            }
            
            val axisY = Axis().apply {
                textColor = labelColor
                textSize = 10
                setHasLines(true)
                val unit = if (isTemperature) {
                    if (tempUnit == "F") "°F" else "°C"
                } else {
                    "%"
                }
                name = unit
            }

            chartData.axisXBottom = axisX
            chartData.axisYLeft = axisY

            chartView.lineChartData = chartData
        }
    )
}
