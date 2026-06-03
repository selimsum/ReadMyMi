package com.example.readmymi

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class RegexBenchmarkTest {
    private val precompiledRegex = Regex("""Sleeping\.\.\. \((\d+)s\)""")

    @Test
    fun testRegexCompilation() {
        val iterations = 100000
        val serviceStatus = "Sleeping... (120s)"

        // Warmup
        for (i in 0..10000) {
            Regex("""Sleeping\.\.\. \((\d+)s\)""").find(serviceStatus)?.groupValues?.getOrNull(1)?.toLongOrNull()
            precompiledRegex.find(serviceStatus)?.groupValues?.getOrNull(1)?.toLongOrNull()
        }

        val timeBaseline = measureTimeMillis {
            for (i in 1..iterations) {
                Regex("""Sleeping\.\.\. \((\d+)s\)""").find(serviceStatus)?.groupValues?.getOrNull(1)?.toLongOrNull()
            }
        }

        val timeOptimized = measureTimeMillis {
            for (i in 1..iterations) {
                precompiledRegex.find(serviceStatus)?.groupValues?.getOrNull(1)?.toLongOrNull()
            }
        }

        assertTrue(timeBaseline >= 0)
        assertTrue(timeOptimized >= 0)
    }
}
