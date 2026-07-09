package com.example.readmymi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppLoggerTest {

    @Before
    fun setup() {
        // Ensure AppLogger is clear before each test
        AppLogger.clear()
    }

    @Test
    fun testLogAddsEntry() {
        val tag = "TEST_TAG"
        val message = "Test message"

        AppLogger.log(tag, message)

        val logs = AppLogger.logs.value
        assertEquals(1, logs.size)
        assertTrue(logs[0].contains(tag))
        assertTrue(logs[0].contains(message))
    }

    @Test
    fun testLogLimitEdgeCase() {
        // AppLogger should keep only the last 500 logs
        val limit = 500
        val overLimit = 501

        for (i in 1..overLimit) {
            AppLogger.log("TAG", "Message $i")
        }

        val logs = AppLogger.logs.value
        assertEquals("Log size should be limited to $limit", limit, logs.size)

        // The logs are added to the end of the list, so the last element in the list is the most recent log
        // Let's verify that the oldest log (Message 1) was dropped, and the newest log (Message 501) is present
        assertTrue("Most recent log should be present at the end", logs.last().contains("Message $overLimit"))
        assertTrue("Oldest log should be dropped, second oldest should be present at the beginning", logs.first().contains("Message 2"))
    }

    @Test
    fun testClear() {
        AppLogger.log("TAG", "Message to clear")
        assertEquals(1, AppLogger.logs.value.size)

        AppLogger.clear()

        assertEquals(0, AppLogger.logs.value.size)
    }
}
