package com.example.xiaomimqtt

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppLogger {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs = _logs.asStateFlow()

    fun log(tag: String, message: String) {
        val entry = "[${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())}] $tag: $message"
        // Keep last 500 logs
        _logs.value = (listOf(entry) + _logs.value).take(500)
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
