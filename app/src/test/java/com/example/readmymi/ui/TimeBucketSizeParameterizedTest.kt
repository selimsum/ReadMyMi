package com.example.readmymi.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class TimeBucketSizeParameterizedTest(private val filter: Int, private val expected: Long) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "filter={0}, expected={1}")
        fun data(): Collection<Array<Any>> = listOf(
            arrayOf(0, 10 * 60 * 1000L),
            arrayOf(1, 1 * 60 * 60 * 1000L),
            arrayOf(2, 4 * 60 * 60 * 1000L),
            arrayOf(3, 24 * 60 * 60 * 1000L),
            arrayOf(-1, 10 * 60 * 1000L),
            arrayOf(4, 10 * 60 * 1000L),
            arrayOf(99, 10 * 60 * 1000L),
            arrayOf(Int.MIN_VALUE, 10 * 60 * 1000L),
            arrayOf(Int.MAX_VALUE, 10 * 60 * 1000L)
        )
    }

    @Test
    fun testGetTimeBucketSize() {
        assertEquals(expected, getTimeBucketSize(filter))
    }
}
