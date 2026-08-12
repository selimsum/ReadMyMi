package com.example.readmymi.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [SensorEntity::class], version = 3, exportSchema = false)
abstract class SensorDatabase : RoomDatabase() {
    abstract fun sensorDao(): SensorDao

    companion object {
        @Volatile
        private var INSTANCE: SensorDatabase? = null

        val MIGRATION_1_2 = Migration(1, 2) { db ->
            // Deduplicate existing rows before adding unique index
            db.execSQL("""
                DELETE FROM sensor_data WHERE id NOT IN (
                    SELECT MIN(id) FROM sensor_data GROUP BY macAddress, timestamp
                )
            """)
            // Add unique index on (macAddress, timestamp) to prevent duplicates
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sensor_data_macAddress_timestamp ON sensor_data(macAddress, timestamp)")
        }

        val MIGRATION_2_3 = Migration(2, 3) { db ->
            // Recreate the table so humidity is stored as REAL (keeps fractional percent values)
            db.execSQL("CREATE TABLE IF NOT EXISTS sensor_data_new (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "macAddress TEXT NOT NULL, " +
                "temperature REAL NOT NULL, " +
                "humidity REAL NOT NULL, " +
                "battery INTEGER NOT NULL, " +
                "timestamp INTEGER NOT NULL)")
            db.execSQL("INSERT INTO sensor_data_new (id, macAddress, temperature, humidity, battery, timestamp) " +
                "SELECT id, macAddress, temperature, CAST(humidity AS REAL), battery, timestamp FROM sensor_data")
            db.execSQL("DROP TABLE sensor_data")
            db.execSQL("ALTER TABLE sensor_data_new RENAME TO sensor_data")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sensor_data_macAddress_timestamp ON sensor_data(macAddress, timestamp)")
        }

        fun getDatabase(context: Context): SensorDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SensorDatabase::class.java,
                    "sensor_database"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
