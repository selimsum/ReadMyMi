package com.example.readmymi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SensorParserTest {

    @Test
    fun testParseBTHome_ValidPayload() {
        // Mock payload for BTHome
        // Info: 0x40 (unencrypted)
        // Batt: type 0x01, value 95
        // Temp: type 0x02, value 2350 (23.5C) -> 2350 is 0x092E -> LE: 2E 09
        // Hum: type 0x03, value 4500 (45.0%) -> 4500 is 0x1194 -> LE: 94 11
        val payload = byteArrayOf(
            0x40.toByte(),
            0x01.toByte(), 95.toByte(),
            0x02.toByte(), 0x2E.toByte(), 0x09.toByte(),
            0x03.toByte(), 0x94.toByte(), 0x11.toByte()
        )
        val serviceData = mapOf("fcd2" to payload)

        val result = SensorParser.parse("TestDevice", "00:11:22:33:44:55", serviceData)

        assertEquals(23.5, result?.temperature)
        assertEquals(45.0, result?.humidity)
        assertEquals(95, result?.battery)
    }

    @Test
    fun testParseBTHome_Encrypted() {
        // Info byte with encryption bit set (0x01)
        val payload = byteArrayOf(0x01.toByte(), 0x00.toByte())
        val serviceData = mapOf("fcd2" to payload)

        val result = SensorParser.parse("TestDevice", "00:11:22:33:44:55", serviceData)

        assertNull(result)
    }

    @Test
    fun testParseBTHome_VoltageFallback() {
        // Info: 0x40
        // Temp: type 0x02, value 2000 -> LE: D0 07
        // Voltage: type 0x0C, value 3000 (3.000V) -> 3000 is 0x0BB8 -> LE: B8 0B
        // Battery should be (3000 - 2100) / 10 = 90
        val payload = byteArrayOf(
            0x40.toByte(),
            0x02.toByte(), 0xD0.toByte(), 0x07.toByte(),
            0x0C.toByte(), 0xB8.toByte(), 0x0B.toByte()
        )
        val serviceData = mapOf("fcd2" to payload)

        val result = SensorParser.parse("TestDevice", "00:11:22:33:44:55", serviceData)

        assertEquals(20.0, result?.temperature)
        assertEquals(90, result?.battery)
    }

    @Test
    fun testParseATC_ValidPayload() {
        // Payload must be >= 12 bytes
        // Temp at offset 6: value 2150 (21.5C) -> 2150 is 0x0866 -> LE: 66 08
        // Hum at offset 8: value 55 (55%) -> 0x37
        // Batt at offset 9: value 80 (80%) -> 0x50
        val payload = ByteArray(12)
        payload[6] = 0x66.toByte()
        payload[7] = 0x08.toByte()
        payload[8] = 0x37.toByte()
        payload[9] = 0x50.toByte()
        val serviceData = mapOf("181a" to payload)

        val result = SensorParser.parse("TestDevice", "00:11:22:33:44:55", serviceData)

        assertEquals(21.5, result?.temperature)
        assertEquals(55.0, result?.humidity)
        assertEquals(80, result?.battery)
    }

    @Test
    fun testParseATC_UndersizedPayload() {
        val payload = ByteArray(11) // Less than 12
        val serviceData = mapOf("181a" to payload)

        val result = SensorParser.parse("TestDevice", "00:11:22:33:44:55", serviceData)

        assertNull(result)
    }

    @Test
    fun testParseXiaomi_Size15() {
        // Payload >= 15
        // Temp at offset 11: value 225 (22.5C) -> 225 is 0x00E1 -> LE: E1 00
        // Hum at offset 13: value 400 (40.0%) -> 400 is 0x0190 -> LE: 90 01
        val payload = ByteArray(15)
        payload[11] = 0xE1.toByte()
        payload[12] = 0x00.toByte()
        payload[13] = 0x90.toByte()
        payload[14] = 0x01.toByte()
        val serviceData = mapOf("fe95" to payload)

        val result = SensorParser.parse("TestDevice", "00:11:22:33:44:55", serviceData)

        assertEquals(22.5, result?.temperature)
        assertEquals(40.0, result?.humidity)
        assertEquals(0, result?.battery) // No battery offset since size < 17
    }

    @Test
    fun testParseXiaomi_Size17() {
        // Payload >= 17
        // Temp at 11: 250 (25.0) -> FA 00
        // Hum at 13: 500 (50.0) -> F4 01
        // Voltage at 15: value 2800 (2.8V) -> 0x0AF0 -> LE: F0 0A
        // Batt = ((2800 - 2100) / 10) = 70
        val payload = ByteArray(17)
        payload[11] = 0xFA.toByte()
        payload[12] = 0x00.toByte()
        payload[13] = 0xF4.toByte()
        payload[14] = 0x01.toByte()
        payload[15] = 0xF0.toByte()
        payload[16] = 0x0A.toByte()
        val serviceData = mapOf("fe95" to payload)

        // The deviceName "ATC_Test" triggers parseATC logic in SensorParser.parse()
        // which has a completely different payload format expectation than parseXiaomi.
        // We use "Xiaomi_Test" to fall through to parseXiaomi because it contains "fe95" service data.
        val result = SensorParser.parse("Xiaomi_Test", "00:11:22:33:44:55", serviceData)

        assertEquals(25.0, result?.temperature)
        assertEquals(50.0, result?.humidity)
        assertEquals(70, result?.battery)
    }

    @Test
    fun testParseXiaomi_Size13() {
        // Payload >= 13, but < 15 and name doesn't contain ATC
        // Temp at 6 (low) & 7 (high): value 2000 (20.0C) -> 2000 is 0x07D0 -> LE: D0 07
        // Hum at 8: 60 (60%) -> 0x3C
        // Batt at 12: 85 (85%) -> 0x55
        val payload = ByteArray(13)
        payload[6] = 0xD0.toByte()
        payload[7] = 0x07.toByte()
        payload[8] = 0x3C.toByte()
        payload[12] = 0x55.toByte()
        val serviceData = mapOf("fe95" to payload)

        val result = SensorParser.parse("OtherDevice", "00:11:22:33:44:55", serviceData)

        assertEquals(20.0, result?.temperature)
        assertEquals(60.0, result?.humidity)
        assertEquals(85, result?.battery)
    }

    @Test
    fun testParse_UnknownUUID() {
        val payload = byteArrayOf(0x00.toByte(), 0x01.toByte())
        val serviceData = mapOf("abcd" to payload) // Not matching any handled UUID

        val result = SensorParser.parse("TestDevice", "00:11:22:33:44:55", serviceData)

        assertNull(result)
    }

    @Test
    fun testParse_ExceptionHandling() {
        // For BTHome, say it says temp is 2 bytes but payload ends early
        val payload = byteArrayOf(
            0x40.toByte(),
            0x02.toByte(), 0x00.toByte() // Missing the second byte for int16
        )
        val serviceData = mapOf("fcd2" to payload)

        val result = SensorParser.parse("TestDevice", "00:11:22:33:44:55", serviceData)

        // Internal exception catching should handle IndexOutOfBounds and return null
        assertNull(result)
    }
}
