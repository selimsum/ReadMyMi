package com.example.readmymi

import org.junit.Assert.assertEquals
import org.junit.Test

class TemperatureConverterTest {

    @Test
    fun testConvert_CelsiusToFahrenheit() {
        val result1 = TemperatureConverter.convert(0.0, "F")
        assertEquals(32.0, result1, 0.001)

        val result2 = TemperatureConverter.convert(100.0, "F")
        assertEquals(212.0, result2, 0.001)

        val result3 = TemperatureConverter.convert(-40.0, "F")
        assertEquals(-40.0, result3, 0.001)
    }

    @Test
    fun testConvert_CelsiusToCelsius() {
        val result1 = TemperatureConverter.convert(0.0, "C")
        assertEquals(0.0, result1, 0.001)

        val result2 = TemperatureConverter.convert(100.0, "C")
        assertEquals(100.0, result2, 0.001)

        val result3 = TemperatureConverter.convert(-40.0, "C")
        assertEquals(-40.0, result3, 0.001)

        // Also test an unknown unit, should default to Celsius
        val result4 = TemperatureConverter.convert(25.0, "UNKNOWN")
        assertEquals(25.0, result4, 0.001)
    }

    @Test
    fun testFormat_Fahrenheit() {
        val result1 = TemperatureConverter.format(0.0, "F")
        assertEquals("32.0°F", result1)

        val result2 = TemperatureConverter.format(25.0, "F")
        assertEquals("77.0°F", result2)

        val result3 = TemperatureConverter.format(-40.0, "F")
        assertEquals("-40.0°F", result3)
    }

    @Test
    fun testFormat_Celsius() {
        val result1 = TemperatureConverter.format(0.0, "C")
        assertEquals("0.0°C", result1)

        val result2 = TemperatureConverter.format(23.54, "C")
        assertEquals("23.5°C", result2)

        val result3 = TemperatureConverter.format(23.56, "C")
        assertEquals("23.6°C", result3)

        val result4 = TemperatureConverter.format(-10.12, "C")
        assertEquals("-10.1°C", result4)
    }
}
