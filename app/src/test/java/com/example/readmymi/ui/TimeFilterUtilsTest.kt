package com.example.readmymi.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFilterUtilsTest {

    private val testEndTime = 1672531200000L // arbitrary timestamp: 2023-01-01T00:00:00Z
    private val oneDayMs = 24 * 60 * 60 * 1000L

    @Test
    fun getTimeFilterBounds_filter0_returnsLast24Hours() {
        val result = getTimeFilterBounds(filter = 0, endTime = testEndTime)
        val expectedStart = testEndTime - oneDayMs
        assertEquals(expectedStart to testEndTime, result)
    }

    @Test
    fun getTimeFilterBounds_filter1_returnsLast7Days() {
        val result = getTimeFilterBounds(filter = 1, endTime = testEndTime)
        val expectedStart = testEndTime - 7L * oneDayMs
        assertEquals(expectedStart to testEndTime, result)
    }

    @Test
    fun getTimeFilterBounds_filter2_returnsLast30Days() {
        val result = getTimeFilterBounds(filter = 2, endTime = testEndTime)
        val expectedStart = testEndTime - 30L * oneDayMs
        assertEquals(expectedStart to testEndTime, result)
    }

    @Test
    fun getTimeFilterBounds_filter3_returnsLast180Days() {
        val result = getTimeFilterBounds(filter = 3, endTime = testEndTime)
        val expectedStart = testEndTime - 180L * oneDayMs
        assertEquals(expectedStart to testEndTime, result)
    }

    @Test
    fun getTimeFilterBounds_invalidFilter_returnsLast24Hours() {
        val resultNegative = getTimeFilterBounds(filter = -1, endTime = testEndTime)
        val expectedStartNegative = testEndTime - oneDayMs
        assertEquals(expectedStartNegative to testEndTime, resultNegative)

        val resultHigh = getTimeFilterBounds(filter = 99, endTime = testEndTime)
        val expectedStartHigh = testEndTime - oneDayMs
        assertEquals(expectedStartHigh to testEndTime, resultHigh)
    }

    @Test
    fun getTimeBucketSize_filter0_returns10Minutes() {
        val result = getTimeBucketSize(filter = 0)
        assertEquals(10 * 60 * 1000L, result)
    }

    @Test
    fun getTimeBucketSize_filter1_returns1Hour() {
        val result = getTimeBucketSize(filter = 1)
        assertEquals(1 * 60 * 60 * 1000L, result)
    }

    @Test
    fun getTimeBucketSize_filter2_returns4Hours() {
        val result = getTimeBucketSize(filter = 2)
        assertEquals(4 * 60 * 60 * 1000L, result)
    }

    @Test
    fun getTimeBucketSize_filter3_returns24Hours() {
        val result = getTimeBucketSize(filter = 3)
        assertEquals(24 * 60 * 60 * 1000L, result)
    }

    @Test
    fun getTimeBucketSize_invalidFilter_returns10Minutes() {
        val resultNegative = getTimeBucketSize(filter = -1)
        assertEquals(10 * 60 * 1000L, resultNegative)

        val resultHigh = getTimeBucketSize(filter = 99)
        assertEquals(10 * 60 * 1000L, resultHigh)
    }
}
