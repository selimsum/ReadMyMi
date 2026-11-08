package com.example.readmymi

import com.example.readmymi.data.DemoDataProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DemoDataProviderTest {

    @Test
    fun testGenerate24HourHistory() {
        val now = 1700000000000L
        val history = DemoDataProvider.generate24HourHistory(now)

        assertFalse(history.isEmpty())
        assertEquals(145, history.size) // 24h * 6 records/hour + 1

        history.forEach { record ->
            assertEquals(DemoDataProvider.DEMO_MAC, record.macAddress)
            assertEquals(94, record.battery)
            assertTrue("Temperature ${record.temperature} should be realistic", record.temperature in 15f..35f)
            assertTrue("Humidity ${record.humidity} should be realistic", record.humidity in 20f..80f)
            assertTrue("Timestamp should be in past 24 hours", record.timestamp <= now)
        }
    }

    @Test
    fun testLiveDemoSensorFlow() = runBlocking {
        val reading = DemoDataProvider.liveDemoSensorFlow().first()

        assertEquals(DemoDataProvider.DEMO_MAC, reading.macAddress)
        assertEquals(94, reading.battery)
        assertTrue("Live temp realistic", reading.temperature in 18.0..30.0)
        assertTrue("Live humidity realistic", reading.humidity in 30.0..70.0)
    }
}
