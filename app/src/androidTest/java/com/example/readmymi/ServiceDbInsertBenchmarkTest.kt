package com.example.readmymi

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.readmymi.data.SensorDatabase
import com.example.readmymi.data.SensorDao
import com.example.readmymi.data.SensorEntity
import android.util.Log
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
class ServiceDbInsertBenchmarkTest {

    private lateinit var db: SensorDatabase
    private lateinit var dao: SensorDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SensorDatabase::class.java).build()
        dao = db.sensorDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun benchmarkInserts() = runBlocking {
        val records = (1..50).map {
            SensorEntity(
                macAddress = "00:00:00:00:00:00",
                temperature = 20.0f,
                humidity = 50,
                battery = 100,
                timestamp = System.currentTimeMillis() + it
            )
        }

        // Warm up
        dao.insert(records.first())

        val timeIndividual = measureTimeMillis {
            records.forEach { dao.insert(it) }
        }

        val timeBatch = measureTimeMillis {
            dao.insertAll(records)
        }

        Log.d("Benchmark", "Individual insert time: $timeIndividual ms")
        Log.d("Benchmark", "Batch insert time: $timeBatch ms")
        assert(timeBatch < timeIndividual)
    }
}
