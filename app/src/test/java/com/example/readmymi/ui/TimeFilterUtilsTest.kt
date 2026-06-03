package com.example.readmymi.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFilterUtilsTest {

    @Test
    fun getTimeBucketSize_validFilters_returnsCorrectSize() {
        // filter 0 -> 10 * 60 * 1000L
        assertEquals(600_000L, getTimeBucketSize(0))
        // filter 1 -> 1 * 60 * 60 * 1000L
        assertEquals(3_600_000L, getTimeBucketSize(1))
        // filter 2 -> 4 * 60 * 60 * 1000L
        assertEquals(14_400_000L, getTimeBucketSize(2))
        // filter 3 -> 24 * 60 * 60 * 1000L
        assertEquals(86_400_000L, getTimeBucketSize(3))
    }

    @Test
    fun getTimeBucketSize_negativeFilters_returnsFallbackSize() {
        // Fallback is 10 * 60 * 1000L
        val fallbackSize = 600_000L

        assertEquals(fallbackSize, getTimeBucketSize(-1))
        assertEquals(fallbackSize, getTimeBucketSize(-10))
        assertEquals(fallbackSize, getTimeBucketSize(Int.MIN_VALUE))
    }

    @Test
    fun getTimeBucketSize_largePositiveFilters_returnsFallbackSize() {
        // Fallback is 10 * 60 * 1000L
        val fallbackSize = 600_000L

        assertEquals(fallbackSize, getTimeBucketSize(4))
        assertEquals(fallbackSize, getTimeBucketSize(100))
        assertEquals(fallbackSize, getTimeBucketSize(Int.MAX_VALUE))
    }
}
