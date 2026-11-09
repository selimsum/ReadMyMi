package com.example.readmymi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FixTimestampsTest {

    private fun createData(ts: Long, temp: Double = 22.0, hum: Double = 50.0): SensorData {
        return SensorData(
            macAddress = "AA:BB:CC:DD:EE:FF",
            deviceName = "ATC_Test",
            temperature = temp,
            humidity = hum,
            battery = 80,
            timestamp = ts
        )
    }

    @Test
    fun testEmptyHistoryReturnsEmpty() {
        val result = BluetoothSensorManager.fixTimestamps(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun testValidEpochRecordsPreserved() {
        val now = 1794220000000L // 2026-11-09 approx
        val history = listOf(
            createData(now - 300_000L),
            createData(now - 200_000L),
            createData(now - 100_000L)
        )
        val result = BluetoothSensorManager.fixTimestamps(history, now = now)
        assertEquals(3, result.size)
        assertEquals(now - 300_000L, result[0].timestamp)
        assertEquals(now - 200_000L, result[1].timestamp)
        assertEquals(now - 100_000L, result[2].timestamp)
    }

    @Test
    fun testRelativeRecordsShiftedToNow() {
        val now = 1794220000000L
        val history = listOf(
            createData(0L),
            createData(60_000L),
            createData(120_000L)
        )
        val result = BluetoothSensorManager.fixTimestamps(history, now = now)
        assertEquals(3, result.size)
        assertEquals(now - 60_000L, result[2].timestamp)
        assertEquals(now - 120_000L, result[1].timestamp)
        assertEquals(now - 180_000L, result[0].timestamp)
    }

    @Test
    fun testMixedRelativeAndModernEpochRecordsSeamless() {
        val now = 1794220000000L
        // 5 relative records representing readings before time sync
        val relRecords = listOf(
            createData(100_000L),
            createData(160_000L),
            createData(220_000L),
            createData(280_000L),
            createData(340_000L)
        )
        // 2 modern epoch records recorded after time sync
        val syncTime = now - 120_000L
        val epochRecords = listOf(
            createData(syncTime),
            createData(syncTime + 60_000L)
        )
        val combinedInput = relRecords + epochRecords

        val result = BluetoothSensorManager.fixTimestamps(combinedInput, now = now)

        // All 7 records must be preserved
        assertEquals(7, result.size)
        // Relative records should end right before syncTime
        assertEquals(syncTime - 60_000L, result[4].timestamp)
        // Epoch records must remain at their actual timestamps
        assertEquals(syncTime, result[5].timestamp)
        assertEquals(syncTime + 60_000L, result[6].timestamp)
        // Monotonically increasing timestamps
        for (i in 1 until result.size) {
            assertTrue(result[i].timestamp > result[i - 1].timestamp)
        }
    }

    @Test
    fun testMultiSessionRelativeRecordsChainedBackwards() {
        val now = 1794220000000L
        // Session 1: device ran from 0 to 120s, then rebooted
        val session1 = listOf(
            createData(0L),
            createData(60_000L),
            createData(120_000L)
        )
        // Session 2: device ran from 0 to 60s
        val session2 = listOf(
            createData(0L),
            createData(60_000L)
        )
        val result = BluetoothSensorManager.fixTimestamps(session1 + session2, now = now)

        assertEquals(5, result.size)
        assertEquals(now - 60_000L, result[4].timestamp)
        assertEquals(now - 120_000L, result[3].timestamp)
        // Session 1 chained right before Session 2
        assertEquals(now - 180_000L, result[2].timestamp)
        assertEquals(now - 240_000L, result[1].timestamp)
        assertEquals(now - 300_000L, result[0].timestamp)
    }
}
