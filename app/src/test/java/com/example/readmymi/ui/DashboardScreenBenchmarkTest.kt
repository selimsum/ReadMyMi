package com.example.readmymi.ui

import org.junit.Test
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.system.measureTimeMillis

class DashboardScreenBenchmarkTest {

    @Test
    fun benchmarkSimpleDateFormat() {
        val nextUpdateMillis = System.currentTimeMillis()

        // Baseline (Current Implementation)
        val baselineTime = measureTimeMillis {
            for (i in 1..100000) {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(nextUpdateMillis))
            }
        }

        // Optimized Implementation
        val formatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
        val optimizedTime = measureTimeMillis {
            val instant = Instant.ofEpochMilli(nextUpdateMillis)
            for (i in 1..100000) {
                formatter.format(instant)
            }
        }

        println("Baseline time (100k iterations): ${baselineTime}ms")
        println("Optimized time (100k iterations): ${optimizedTime}ms")

        assert(optimizedTime < baselineTime)
    }
}
