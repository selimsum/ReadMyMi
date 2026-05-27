package com.example.readmymi

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppLogger {
    private const val MAX_LOGS = 500
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs = _logs.asStateFlow()

    private val dateFormat = object : ThreadLocal<java.text.SimpleDateFormat>() {
        override fun initialValue(): java.text.SimpleDateFormat {
            return java.text.SimpleDateFormat("HH:mm:ss")
        }
    }

    fun log(tag: String, message: String) {
        val entry = "[${dateFormat.get()?.format(java.util.Date())}] $tag: $message"
        val current = _logs.value
        _logs.value = if (current.size < MAX_LOGS) {
            listOf(entry) + current
        } else {
            listOf(entry) + current.subList(0, MAX_LOGS - 1)
        }
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
