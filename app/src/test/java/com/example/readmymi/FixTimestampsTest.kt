package com.example.readmymi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

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

    private fun buildHistoryPacket(
        index: Short,
        epochSeconds: Int,
        tempHundredths: Short,
        humHundredths: Short,
        vbatMv: Short
    ): ByteArray {
        val buf = ByteBuffer.allocate(13).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(0x35.toByte())
        buf.putShort(index)
        buf.putInt(epochSeconds)
        buf.putShort(tempHundredths)
        buf.putShort(humHundredths)
        buf.putShort(vbatMv)
        return buf.array()
    }

    @Test
    fun testEndOfHistoryMarkerMatchesOnlyShortTerminator() {
        assertTrue(BluetoothSensorManager.isEndOfHistoryMarker(byteArrayOf(0x35, 0x00, 0x00)))
        assertFalse(BluetoothSensorManager.isEndOfHistoryMarker(byteArrayOf(0x34, 0x00, 0x00)))

        val indexZeroRecord = buildHistoryPacket(
            index = 0,
            epochSeconds = 1794220000,
            tempHundredths = 2250,
            humHundredths = 5125,
            vbatMv = 3000
        )
        assertFalse(BluetoothSensorManager.isEndOfHistoryMarker(indexZeroRecord))
    }

    @Test
    fun testParseHistoryRecordIndexZeroParsesValidly() {
        val packet = buildHistoryPacket(
            index = 0,
            epochSeconds = 1794220000,
            tempHundredths = 2250,
            humHundredths = 5125,
            vbatMv = 3100
        )
        val parsed = BluetoothSensorManager.parseHistoryRecord(packet, "AA:BB:CC:DD:EE:FF", "ATC_Test")
        assertNotNull(parsed)
        assertEquals(22.5, parsed!!.temperature, 0.001)
        assertEquals(51.25, parsed.humidity, 0.001)
        assertEquals(1794220000_000L, parsed.timestamp)
        assertEquals(SensorParser.calculateBatteryPercentage(3100), parsed.battery)
    }

    @Test
    fun testParseHistoryRecordDummyVsRealTolerance() {
        val dummyPacket = buildHistoryPacket(
            index = 1,
            epochSeconds = 1794220000,
            tempHundredths = 2112,
            humHundredths = 4300,
            vbatMv = 3000
        )
        assertNull(BluetoothSensorManager.parseHistoryRecord(dummyPacket, "AA:BB:CC:DD:EE:FF", "ATC_Test"))

        val realClosePacket = buildHistoryPacket(
            index = 2,
            epochSeconds = 1794220060,
            tempHundredths = 2114,
            humHundredths = 4300,
            vbatMv = 3000
        )
        val parsedReal = BluetoothSensorManager.parseHistoryRecord(realClosePacket, "AA:BB:CC:DD:EE:FF", "ATC_Test")
        assertNotNull(parsedReal)
        assertEquals(21.14, parsedReal!!.temperature, 0.001)
        assertEquals(43.0, parsedReal.humidity, 0.001)
    }

    @Test
    fun testParseHistoryRecordUnsignedVbat() {
        val highVbatPacket = buildHistoryPacket(
            index = 3,
            epochSeconds = 1794220120,
            tempHundredths = 2200,
            humHundredths = 5000,
            vbatMv = 40000.toShort()
        )
        val parsed = BluetoothSensorManager.parseHistoryRecord(highVbatPacket, "AA:BB:CC:DD:EE:FF", "ATC_Test")
        assertNotNull(parsed)
        assertEquals(SensorParser.calculateBatteryPercentage(40000), parsed!!.battery)
    }
}
