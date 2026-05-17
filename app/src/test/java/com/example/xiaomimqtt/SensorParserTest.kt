package com.example.xiaomimqtt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SensorParserTest {

    private fun createATCPayload(temp: Int, hum: Int, batt: Int = 100): ByteArray {
        val payload = ByteArray(12)
        // temp is int16LE at index 6
        payload[6] = (temp and 0xFF).toByte()
        payload[7] = ((temp shr 8) and 0xFF).toByte()
        // hum is unsigned byte at index 8
        payload[8] = hum.toByte()
        // batt is unsigned byte at index 9
        payload[9] = batt.toByte()
        return payload
    }

    @Test
    fun testValidData() {
        // temp: 20.00 -> 2000, hum: 50
        val payload = createATCPayload(2000, 50)
        val data = SensorParser.parse("ATC_123", "00:11:22:33:44:55", mapOf("181a" to payload))
        assertNotNull(data)
        assertEquals(20.0, data!!.temperature, 0.01)
        assertEquals(50.0, data.humidity, 0.01)
    }

    @Test
    fun testTempTooLow() {
        // temp: -41.00 -> -4100
        val payload = createATCPayload(-4100, 50)
        val data = SensorParser.parse("ATC_123", "00:11:22:33:44:55", mapOf("181a" to payload))
        assertNull(data)
    }

    @Test
    fun testTempTooHigh() {
        // temp: 81.00 -> 8100
        val payload = createATCPayload(8100, 50)
        val data = SensorParser.parse("ATC_123", "00:11:22:33:44:55", mapOf("181a" to payload))
        assertNull(data)
    }

    @Test
    fun testHumTooHigh() {
        // temp: 20.00 -> 2000, hum: 101
        val payload = createATCPayload(2000, 101)
        val data = SensorParser.parse("ATC_123", "00:11:22:33:44:55", mapOf("181a" to payload))
        assertNull(data)
    }

    // Since hum is extracted via `data[8].toUByte().toInt()`, it cannot be < 0 from a single byte.
    // We can't directly inject a negative humidity here using ATC parser due to UByte casting.
    // But testing > 100 covers the upper bound logic.

    @Test
    fun testTempAndHumZero() {
        // temp: 0.00 -> 0, hum: 0
        val payload = createATCPayload(0, 0)
        val data = SensorParser.parse("ATC_123", "00:11:22:33:44:55", mapOf("181a" to payload))
        assertNull(data)
    }

    @Test
    fun testTempAndHumZeroEdgeCase() {
        // Check temp 0, hum > 0
        val payload1 = createATCPayload(0, 50)
        val data1 = SensorParser.parse("ATC_123", "00:11:22:33:44:55", mapOf("181a" to payload1))
        assertNotNull(data1)
        assertEquals(0.0, data1!!.temperature, 0.01)

        // Check temp > 0, hum 0
        val payload2 = createATCPayload(2000, 0)
        val data2 = SensorParser.parse("ATC_123", "00:11:22:33:44:55", mapOf("181a" to payload2))
        assertNotNull(data2)
        assertEquals(0.0, data2!!.humidity, 0.01)
    }
}
