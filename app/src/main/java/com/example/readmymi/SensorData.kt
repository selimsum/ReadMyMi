package com.example.readmymi

data class SensorData(
    val macAddress: String,
    val deviceName: String = "",
    val temperature: Double,
    val humidity: Double,
    val battery: Int,
    val timestamp: Long = System.currentTimeMillis()
)
