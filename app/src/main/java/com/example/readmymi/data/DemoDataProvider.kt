package com.example.readmymi.data

import com.example.readmymi.SensorData
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Provides in-memory simulated environmental sensor data for first-time user exploration
 * (Demo Mode) without writing anything to the local database.
 */
object DemoDataProvider {
    const val DEMO_MAC = "DEMO:AA:BB:CC:DD"
    const val DEMO_DEVICE_NAME = "Living Room (Demo)"

    /**
     * Generates a 24-hour realistic historical curve of readings spaced every 10 minutes.
     * Uses a gentle diurnal sine wave to simulate natural indoor climate changes.
     */
    fun generate24HourHistory(nowMs: Long = System.currentTimeMillis()): List<SensorEntity> {
        val records = mutableListOf<SensorEntity>()
        val intervalMs = TimeUnit.MINUTES.toMillis(10)
        val count = 144 // 24 hours * 6 per hour

        for (i in count downTo 0) {
            val timestamp = nowMs - (i * intervalMs)
            // Normalized cycle across 24 hours (0 to 2*PI)
            val dayFraction = ((timestamp % TimeUnit.DAYS.toMillis(1)).toDouble() / TimeUnit.DAYS.toMillis(1)) * 2 * PI
            
            // Temperature peak in late afternoon (~23.5°C), trough in early morning (~20.5°C)
            val temp = 22.0 + (1.5 * sin(dayFraction - PI / 2)) + ((i % 5) * 0.05)
            // Humidity inversely correlated (~42% to ~56%)
            val humidity = 49.0 - (6.0 * sin(dayFraction - PI / 2)) + ((i % 3) * 0.2)

            records.add(
                SensorEntity(
                    id = i.toLong(),
                    macAddress = DEMO_MAC,
                    temperature = (Math.round(temp * 10) / 10.0).toFloat(),
                    humidity = (Math.round(humidity * 10) / 10.0).toFloat(),
                    battery = 94,
                    timestamp = timestamp
                )
            )
        }
        return records
    }

    /**
     * Emits continuous live readings that fluctuate realistically every [intervalMs].
     */
    fun liveDemoSensorFlow(intervalMs: Long = 4000L): Flow<SensorData> = flow {
        var tick = 0
        while (currentCoroutineContext().isActive) {
            val now = System.currentTimeMillis()
            val dayFraction = ((now % TimeUnit.DAYS.toMillis(1)).toDouble() / TimeUnit.DAYS.toMillis(1)) * 2 * PI
            val tempJitter = ((tick % 4) - 1.5) * 0.08
            val humJitter = ((tick % 3) - 1.0) * 0.2

            val currentTemp = 22.4 + (1.2 * sin(dayFraction - PI / 2)) + tempJitter
            val currentHumidity = 48.0 - (5.0 * sin(dayFraction - PI / 2)) + humJitter

            emit(
                SensorData(
                    macAddress = DEMO_MAC,
                    temperature = Math.round(currentTemp * 10) / 10.0,
                    humidity = Math.round(currentHumidity * 10) / 10.0,
                    battery = 94,
                    timestamp = now
                )
            )
            tick++
            delay(intervalMs)
        }
    }
}
