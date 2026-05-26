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
import lecho.lib.hellocharts.model.*
import lecho.lib.hellocharts.view.LineChartView
import lecho.lib.hellocharts.view.Chart
import lecho.lib.hellocharts.gesture.ZoomType
import lecho.lib.hellocharts.gesture.ContainerScrollType
import com.example.readmymi.data.SensorEntity
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat
import kotlin.math.roundToInt

@Composable
fun SensorChart(
    data: List<SensorEntity>,
    isTemperature: Boolean, // true for Temp, false for Humidity
    modifier: Modifier = Modifier,
    tempUnit: String = "C"
) {
    if (data.isEmpty()) return

    val themeColor = if (isTemperature) android.graphics.Color.parseColor("#FF5722") else android.graphics.Color.parseColor("#03A9F4")
    val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f).toArgb()

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(top = 16.dp, bottom = 12.dp),
        factory = { context ->
            LineChartView(context).apply {
                isInteractive = true
                zoomType = ZoomType.HORIZONTAL
                setContainerScrollEnabled(true, ContainerScrollType.HORIZONTAL)
                isValueSelectionEnabled = true
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

            val pointsData = sampledData.mapIndexed { index, item ->
                val yRaw = if (isTemperature) {
                    com.example.readmymi.TemperatureConverter.convert(item.temperature, tempUnit)
                } else {
                    item.humidity.toFloat()
                }
                val xRaw = if (duration > 0) {
                    ((item.timestamp - minTime).toDouble() / duration * (sampledData.size - 1)).toFloat()
                } else {
                    index.toFloat()
                }
                val unit = if (isTemperature) (if (tempUnit == "F") "°F" else "°C") else "%"
                val timeStr = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(item.timestamp))
                val labelText = "${String.format(Locale.US, "%.1f", yRaw)}$unit ($timeStr)"
                PointValue(xRaw, yRaw).setLabel(labelText.toCharArray())
            }

            // Custom Touch Listener for "Scrubbing" (Slide to Select) and Column/Vertical Hit detection
            // allowing the user to tap above/below points or slide finger to read data.
            chartView.setOnTouchListener { v, event ->
                val parent = v.parent
                parent?.requestDisallowInterceptTouchEvent(true) // Prevent parent scroll while scrubbing

                if (event.action == android.view.MotionEvent.ACTION_DOWN || 
                    event.action == android.view.MotionEvent.ACTION_MOVE) {
                    
                    val chart = v as Chart
                    val computator = chart.chartComputator
                    val contentRect = computator.contentRectMinusAllMargins
                    val viewport = computator.visibleViewport
                    
                    if (contentRect.width() > 0 && viewport.width() > 0) {
                        // Map X pixel -> X Value (Index)
                        val x = event.x.coerceIn(contentRect.left.toFloat(), contentRect.right.toFloat())
                        val relativeX = (x - contentRect.left) / contentRect.width()
                        val valueX = viewport.left + (relativeX * viewport.width())
                        
                        // Find point closest to valueX
                        var nearestIndex = 0
                        var minDiff = Float.MAX_VALUE
                        for (i in pointsData.indices) {
                            val diff = Math.abs(pointsData[i].x - valueX)
                            if (diff < minDiff) {
                                minDiff = diff
                                nearestIndex = i
                            }
                        }
                        
                        // Select the point programmatically
                        chart.selectValue(SelectedValue(0, nearestIndex, SelectedValue.SelectedValueType.LINE))
                    }
                    true // Consume event to prevent conflict with built-in scroll/zoom and force Scrub behavior
                } else if (event.action == android.view.MotionEvent.ACTION_UP || 
                           event.action == android.view.MotionEvent.ACTION_CANCEL) {
                    true
                } else {
                    false
                }
            }

            // Remove Toast listener (User requested no popup)
            chartView.onValueTouchListener = object : lecho.lib.hellocharts.listener.LineChartOnValueSelectListener {
                override fun onValueSelected(lineIndex: Int, pointIndex: Int, value: PointValue) {}
                override fun onValueDeselected() {}
            }

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
