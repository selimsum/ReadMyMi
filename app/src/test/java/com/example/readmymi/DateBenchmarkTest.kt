package com.example.readmymi

import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DateBenchmarkTest {
    @Test
    fun testDateAllocation() {
        val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.US)
        val timestamps = LongArray(100000) { System.currentTimeMillis() + it * 1000 }

        // Warmup
        for (i in 0..10000) {
            dateFormat.format(Date(timestamps[i]))
        }

        val timeBaseline = kotlin.system.measureTimeMillis {
            for (ts in timestamps) {
                dateFormat.format(Date(ts))
            }
        }

        val sharedDate = Date()
        val timeOptimized = kotlin.system.measureTimeMillis {
            for (ts in timestamps) {
                sharedDate.time = ts
                dateFormat.format(sharedDate)
            }
        }

        assertTrue(timeBaseline >= 0)
        assertTrue(timeOptimized >= 0)
    }
}
