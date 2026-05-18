package com.example.xiaomimqtt

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.xiaomimqtt.data.SensorDatabase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val prefs = PrefsManager(context)
    private val database = SensorDatabase.getDatabase(context)

    private val _lastMac = MutableStateFlow(prefs.lastMac)
    val lastMac = _lastMac.asStateFlow()

    val offlineData = _lastMac.flatMapLatest { mac ->
        database.sensorDao().getLatest(mac)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isServiceRunning = flow {
        while (true) {
            emit(SensorForegroundService.isServiceRunning)
            kotlinx.coroutines.delay(1000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(2000), SensorForegroundService.isServiceRunning)

    val liveData = SensorForegroundService.liveSensorData

    val sensorData = combine(liveData, offlineData) { live, offline ->
        live ?: offline?.let {
            SensorData(
                macAddress = it.macAddress,
                temperature = it.temperature.toDouble(),
                humidity = it.humidity.toDouble(),
                battery = it.battery,
                timestamp = it.timestamp
            )
        }
    }.onEach { data ->
        data?.let { if (it.macAddress != _lastMac.value) updateLastMac(it.macAddress) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun updateLastMac(mac: String) {
        if (mac != _lastMac.value) {
            _lastMac.value = mac
            prefs.lastMac = mac
        }
    }

    fun startService() {
        val intent = Intent(context, SensorForegroundService::class.java).apply {
            action = "START_SCAN"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopService() {
        val intent = Intent(context, SensorForegroundService::class.java).apply {
            action = "STOP_SCAN"
        }
        context.startService(intent)
    }

    fun restartService() {
        viewModelScope.launch {
            stopService()
            kotlinx.coroutines.delay(1000)
            startService()
        }
    }
}
