package com.example.readmymi

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
        _logs.update { current ->
            if (current.size < MAX_LOGS) {
                current + entry
            } else {
                current.subList(current.size - MAX_LOGS + 1, current.size) + entry
            }
        }
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
