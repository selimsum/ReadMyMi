package com.example.readmymi.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sensor_data")
data class SensorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val macAddress: String,
    val temperature: Float,
    val humidity: Int,
    val battery: Int,
    val timestamp: Long
) {
    companion object {
        // 10-minute bucket in ms for deduplication
        const val BUCKET_MS = 600000L
    }
}
