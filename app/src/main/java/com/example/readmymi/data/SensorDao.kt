package com.example.readmymi.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SensorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sensorData: SensorEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sensorDataList: List<SensorEntity>)

    // Get history for a specific device within a time range
    @Query("SELECT * FROM sensor_data WHERE macAddress = :macAddress AND timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp ASC")
    fun getHistory(macAddress: String, startTime: Long, endTime: Long): Flow<List<SensorEntity>>

    // Delete old data (optional maintenance)
    @Query("DELETE FROM sensor_data WHERE timestamp < :threshold")
    suspend fun deleteOldData(threshold: Long)

    @Query("SELECT * FROM sensor_data WHERE macAddress = :macAddress ORDER BY timestamp DESC LIMIT 1")
    fun getLatest(macAddress: String): Flow<SensorEntity?>

    @Query("SELECT timestamp FROM sensor_data WHERE macAddress = :macAddress ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestTimestamp(macAddress: String): Long?

    @Query("SELECT DISTINCT macAddress FROM sensor_data")
    fun getKnownDevices(): Flow<List<String>>
}
