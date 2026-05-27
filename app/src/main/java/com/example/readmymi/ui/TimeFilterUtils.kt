package com.example.readmymi.ui

fun getTimeFilterBounds(filter: Int, endTime: Long = System.currentTimeMillis()): Pair<Long, Long> {
    val startTime = when (filter) {
        0 -> endTime - 24 * 60 * 60 * 1000L
        1 -> endTime - 7L * 24 * 60 * 60 * 1000L
        2 -> endTime - 30L * 24 * 60 * 60 * 1000L
        3 -> endTime - 180L * 24 * 60 * 60 * 1000L
        else -> endTime - 24 * 60 * 60 * 1000L
    }
    return startTime to endTime
}

fun getTimeBucketSize(filter: Int): Long {
    return when (filter) {
        0 -> 10 * 60 * 1000L
        1 -> 1 * 60 * 60 * 1000L
        2 -> 4 * 60 * 60 * 1000L
        3 -> 24 * 60 * 60 * 1000L
        else -> 10 * 60 * 1000L
    }
}
