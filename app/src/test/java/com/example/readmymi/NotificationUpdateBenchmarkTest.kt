package com.example.readmymi

import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.measureTimeMillis

class NotificationUpdateBenchmarkTest {

    @Test
    fun benchmarkSimpleDateFormat() {
        val iterations = 100000
        val timestamp = System.currentTimeMillis()

        // 1. Baseline: Creating SimpleDateFormat inside the loop
        val timeWithCreation = measureTimeMillis {
            for (i in 0 until iterations) {
                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                sdf.format(Date(timestamp))
            }
        }

        // 2. Optimized: Reusing SimpleDateFormat instance
        val reusedSdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timeWithReuse = measureTimeMillis {
            for (i in 0 until iterations) {
                reusedSdf.format(Date(timestamp))
            }
        }

        System.err.println("--- PERFORMANCE BENCHMARK ---")
        System.err.println("Iterations: $iterations")
        System.err.println("Time (Creating new instance): $timeWithCreation ms")
        System.err.println("Time (Reusing instance): $timeWithReuse ms")
        val improvement = ((timeWithCreation - timeWithReuse).toDouble() / timeWithCreation) * 100
        System.err.println("Improvement: %.2f%%".format(improvement))
        System.err.println("-----------------------------")
    }
}
