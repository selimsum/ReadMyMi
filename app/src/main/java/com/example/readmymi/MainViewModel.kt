package com.example.readmymi

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.readmymi.data.SensorDatabase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.example.readmymi.data.SensorEntity
import com.example.readmymi.data.DemoDataProvider

private fun SensorEntity.toSensorData() = SensorData(
    macAddress = macAddress,
    temperature = temperature.toDouble(),
    humidity = humidity.toDouble(),
    battery = battery,
    timestamp = timestamp
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val prefs = PrefsManager(context)
    private val database = SensorDatabase.getDatabase(context)

    private val _lastMac = MutableStateFlow(prefs.lastMac)
    val lastMac = _lastMac.asStateFlow()

    val offlineData = _lastMac.flatMapLatest { mac ->
        database.sensorDao().getLatest(mac)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isServiceRunning = SensorForegroundService.isServiceRunningFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2000), SensorForegroundService.isServiceRunning)

    val liveData = SensorForegroundService.liveSensorData

    private val _isDemoMode = MutableStateFlow(false)
    val isDemoMode = _isDemoMode.asStateFlow()
    val demoHistoryData: List<SensorEntity> by lazy { DemoDataProvider.generate24HourHistory() }

    val discoveredSensors: StateFlow<List<SensorData>> = SensorForegroundService.discoveredDevices
        .map { it.values.toList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val sensorData = _isDemoMode.flatMapLatest { isDemo ->
        if (isDemo) {
            DemoDataProvider.liveDemoSensorFlow()
        } else {
            combine(liveData, offlineData) { live, offline ->
                val selected = _lastMac.value
                when {
                    selected.isEmpty() -> live ?: offline?.toSensorData()
                    live != null && live.macAddress == selected -> live
                    else -> offline?.toSensorData()
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        // Adopt the first-seen device exactly once (empty -> first MAC) only
        // after onboarding is completed. Onboarding uses explicit selection.
        viewModelScope.launch {
            liveData.collect { live ->
                if (live != null && _lastMac.value.isEmpty() && prefs.hasCompletedOnboarding && !_isDemoMode.value) {
                    updateLastMac(live.macAddress)
                }
            }
        }
    }

    fun selectDeviceAndCompleteOnboarding(mac: String, friendlyName: String = "") {
        if (friendlyName.isNotBlank()) {
            prefs.setDeviceName(mac, friendlyName.trim())
        }
        updateLastMac(mac)
        prefs.hasCompletedOnboarding = true
        _isDemoMode.value = false
        startService()
    }

    fun enableDemoMode() {
        _isDemoMode.value = true
        prefs.hasCompletedOnboarding = true
    }

    fun exitDemoMode() {
        _isDemoMode.value = false
        prefs.hasCompletedOnboarding = false
        clearSelection()
        restartService()
    }

    fun resetOnboarding() {
        _isDemoMode.value = false
        prefs.hasCompletedOnboarding = false
        clearSelection()
        restartService()
    }

    fun updateLastMac(mac: String) {
        if (mac != _lastMac.value) {
            _lastMac.value = mac
            prefs.lastMac = mac
        }
    }

    fun clearSelection() {
        _lastMac.value = ""
        prefs.lastMac = ""
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
            kotlinx.coroutines.delay(3000)
            startService()
        }
    }

    /**
     * Manual history download. Runs in viewModelScope so it survives rotation
     * (previously launched in composition scope and cancelled on rotate).
     * Bounded by BluetoothSensorManager.DOWNLOAD_TIMEOUT_MS via withTimeout.
     */
    fun downloadHistory(records: Int, onDone: (ok: Boolean, message: String) -> Unit) {
        viewModelScope.launch {
            try {
                val mac = _lastMac.value.ifEmpty {
                    liveData.value?.macAddress ?: offlineData.value?.macAddress ?: ""
                }
                if (mac.isEmpty()) {
                    onDone(false, "No sensor selected yet.")
                    return@launch
                }
                val manager = BluetoothSensorManager(context)
                val latestTs = database.sensorDao().getLatestTimestamp(mac)
                val history = manager.downloadHistory(mac, records, lastDbTimestamp = latestTs)
                if (history.isNotEmpty()) {
                    database.sensorDao().insertAll(history.map {
                        SensorEntity(
                            id = 0,
                            macAddress = it.macAddress,
                            temperature = it.temperature.toFloat(),
                            humidity = it.humidity.toFloat(),
                            battery = it.battery,
                            timestamp = it.timestamp
                        )
                    })
                    onDone(true, "Successfully downloaded ${history.size} records.")
                } else {
                    onDone(true, "No new history records found.")
                }
            } catch (e: Exception) {
                onDone(false, "History sync failed: ${e.message}")
            }
        }
    }
}
