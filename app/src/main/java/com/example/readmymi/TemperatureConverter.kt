package com.example.readmymi

object TemperatureConverter {
    fun convert(celsius: Float, unit: String): Float {
        return if (unit == "F") {
            celsius * 1.8f + 32f
        } else {
            celsius
        }
    }

    fun convert(celsius: Double, unit: String): Double {
        return if (unit == "F") {
            celsius * 1.8 + 32
        } else {
            celsius
        }
    }

    fun format(celsius: Float, unit: String): String {
        val converted = convert(celsius, unit)
        return String.format(java.util.Locale.US, "%.1f", converted) + (if (unit == "F") "°F" else "°C")
    }

    fun format(celsius: Double, unit: String): String {
        val converted = convert(celsius, unit)
        return String.format(java.util.Locale.US, "%.1f", converted) + (if (unit == "F") "°F" else "°C")
    }
}
