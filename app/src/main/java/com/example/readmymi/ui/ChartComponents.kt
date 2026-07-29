package com.example.readmymi.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import java.util.Locale
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

    val formatStr = if (data.isNotEmpty() && data.last().timestamp - data.first().timestamp > 86400000L) "dd/MM" else "HH:mm"
    val dateFormatter = remember(formatStr) { DateTimeFormatter.ofPattern(formatStr, Locale.US).withZone(ZoneId.systemDefault()) }

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
            val validData = data.filter { it.temperature in -40f..80f && it.humidity in 0..100 && !(it.temperature == 0f && it.humidity == 0) }
            val sampledData = if (validData.size > 500) {
                val rate = validData.size / 250
                validData.filterIndexed { index, _ -> index % rate == 0 || index == validData.lastIndex }
            } else {
                validData
            }

            val minTime = if (sampledData.isNotEmpty()) sampledData.first().timestamp else 0L
            val maxTime = if (sampledData.isNotEmpty()) sampledData.last().timestamp else 0L
            val duration = maxTime - minTime

            val timeFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.getDefault()).withZone(ZoneId.systemDefault())
            val gapThresholdMs = 45 * 60 * 1000L

            val lines = mutableListOf<Line>()
            var currentSegment = mutableListOf<PointValue>()

            for (index in sampledData.indices) {
                val item = sampledData[index]
                if (index > 0 && (item.timestamp - sampledData[index - 1].timestamp) > gapThresholdMs) {
                    if (currentSegment.isNotEmpty()) {
                        val segmentLine = Line(ArrayList(currentSegment)).apply {
                            color = themeColor
                            isCubic = currentSegment.size > 2
                            setHasPoints(sampledData.size <= 100 || currentSegment.size == 1)
                            pointRadius = 4
                            strokeWidth = if (sampledData.size > 200) 1 else 2
                            setHasLabels(false)
                            setHasLabelsOnlyForSelected(true)
                        }
                        lines.add(segmentLine)
                        currentSegment = mutableListOf()
                    }
                }

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
                currentSegment.add(PointValue(xRaw, yRaw).setLabel(labelText.toCharArray()))
            }

            if (currentSegment.isNotEmpty()) {
                val segmentLine = Line(ArrayList(currentSegment)).apply {
                    color = themeColor
                    isCubic = currentSegment.size > 2
                    setHasPoints(sampledData.size <= 100 || currentSegment.size == 1)
                    pointRadius = 4
                    strokeWidth = if (sampledData.size > 200) 1 else 2
                    setHasLabels(false)
                    setHasLabelsOnlyForSelected(true)
                }
                lines.add(segmentLine)
            }

            val chartData = LineChartData(lines)

            // dateFormatter is now hoisted
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
                
                for (targetTime in targetTimes) {
                    val labelX = if (duration > 0) {
                        ((targetTime - minTime).toDouble() / duration * (sampledData.size - 1)).toFloat()
                    } else {
                        0f
                    }
                    axisXValues.add(AxisValue(labelX).setLabel(dateFormatter.format(Instant.ofEpochMilli(targetTime))))
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
