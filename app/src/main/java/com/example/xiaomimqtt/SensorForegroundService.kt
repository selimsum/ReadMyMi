package com.example.xiaomimqtt

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

import com.example.xiaomimqtt.data.SensorDatabase
import com.example.xiaomimqtt.data.SensorEntity

class SensorForegroundService : Service() {

    // Flag moved to companion
    companion object {
        var isServiceRunning = false
        val liveSensorData = kotlinx.coroutines.flow.MutableStateFlow<SensorData?>(null)
        val serviceStatus = kotlinx.coroutines.flow.MutableStateFlow("Initializing...")
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var bluetoothSensorManager: BluetoothSensorManager


    private val lastPublishMap = mutableMapOf<String, Long>()
    private var wakeLock: PowerManager.WakeLock? = null
    
    private lateinit var database: SensorDatabase
    private lateinit var prefs: PrefsManager
    private val lastDbSaveMap = mutableMapOf<String, Long>() // Throttle DB saves

    
    // Flag to control service loop (Accessed via Companion for UI)

    override fun onCreate() {
        super.onCreate()
        Log.d("SensorService", "Service Created")
        AppLogger.log("Service", "Service Created")
        isServiceRunning = true

        // Acquire WakeLock for reliable background scanning
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "XiaomiMqtt::ScanWakeLock")
        wakeLock?.acquire() // Indefinite acquisition until onDestroy

        startForegroundService()
        

        
        
        database = SensorDatabase.getDatabase(this)
        prefs = PrefsManager(this)
        
        bluetoothSensorManager = BluetoothSensorManager(this)


        serviceScope.launch {
            runServiceLoop()
        }
        
        serviceScope.launch {
            bluetoothSensorManager.sensorDataFlow.collectLatest { data ->
                data?.let { handleSensorData(it) }
            }
        }
        

    }
    
    private suspend fun runServiceLoop() {
        while (isServiceRunning) {
            val scanStartTime = System.currentTimeMillis()
            performScan()
            
            checkOfflineStatus(scanStartTime)
            if (prefs.alertsEnabled) checkAlerts(prefs)
            checkTimeSync()
            
            runSleepLoop()
        }
    }

    private suspend fun performScan() {
        AppLogger.log("Service", "Starting Periodic Scan (15s)")
        serviceStatus.value = "Scanning (15s)..."
        try {
            bluetoothSensorManager.startScanning(prefs.lastMac)
            kotlinx.coroutines.delay(15000)
            bluetoothSensorManager.stopScanning()
        } catch (e: Exception) {
            AppLogger.log("Service", "Scan error: ${e.message}")
        }
        serviceStatus.value = "Processing & Sleeping..."
    }

    private fun checkOfflineStatus(scanStartTime: Long) {
        val targetMac = prefs.lastMac
        if (targetMac.isNotEmpty()) {
            val lastSeen = lastPublishMap[targetMac] ?: 0L
            if (lastSeen < scanStartTime && !prefs.getWasOffline(targetMac)) {
                prefs.setWasOffline(targetMac, true)
                AppLogger.log("Service", "Device $targetMac went offline.")
            }
        }
    }

    private suspend fun checkTimeSync() {
        val now = System.currentTimeMillis()
        val lastCheck = lastDbSaveMap["TIME_SYNC_CHECK"] ?: 0L
        if (now - lastCheck > 3600000L) {
            lastDbSaveMap["TIME_SYNC_CHECK"] = now
            val targetMac = prefs.lastMac
            if (targetMac.isNotEmpty()) {
                val lastSync = prefs.getLastTimeSync(targetMac)
                if (now - lastSync > 259200000L) {
                    performTimeSync(targetMac, now)
                }
            }
        }
    }

    private suspend fun performTimeSync(mac: String, now: Long) {
        AppLogger.log("Service", "Time Sync Needed")
        serviceStatus.value = "Synchronizing Time..."
        try {
            if (bluetoothSensorManager.syncTime(mac, onLog = { AppLogger.log("BLE", it) })) {
                AppLogger.log("Service", "Time Sync SUCCESS")
                prefs.setLastTimeSync(mac, now)
            } else {
                AppLogger.log("Service", "Time Sync FAILED")
            }
        } catch (e: Exception) {
            AppLogger.log("Service", "Time Sync Error: ${e.message}")
        }
    }

    private suspend fun runSleepLoop() {
        val sleepStart = System.currentTimeMillis()
        while (isServiceRunning) {
            val minInterval = prefs.scanIntervalSeconds.coerceAtLeast(30)
            val elapsed = System.currentTimeMillis() - sleepStart
            if (elapsed >= minInterval * 1000L) break
            
            val remaining = minInterval - (elapsed / 1000)
            serviceStatus.value = "Sleeping... (${remaining}s)"
            kotlinx.coroutines.delay(1000)
        }
    }

    private fun handleSensorData(data: SensorData) {
        liveSensorData.value = data
        latestReadings[data.macAddress] = data
        updateLastPublish(data.macAddress)
        
        if (!historyCheckedMap.containsKey(data.macAddress)) {
            checkAndDownloadHistory(data.macAddress)
        }
        
        updateLiveNotification(data)
        throttleSaveToDb(data)
    }

    private fun updateLastPublish(mac: String) {
        val now = System.currentTimeMillis()
        if (lastPublishMap.put(mac, now) == null) {
            AppLogger.log("Service", "Found new device: $mac")
        }
    }

    private fun checkAndDownloadHistory(mac: String) {
        historyCheckedMap[mac] = true
        serviceScope.launch(Dispatchers.IO) {
            try {
                val latestDbTimestamp = database.sensorDao().getLatestTimestamp(mac)
                val now = System.currentTimeMillis()
                val gapThreshold = 1800000L // 30 mins
                
                if (latestDbTimestamp == null || (now - latestDbTimestamp) > gapThreshold) {
                    AppLogger.log("Service", "Data gap detected. Downloading history...")
                    val history = bluetoothSensorManager.downloadHistory(mac, records = 0) { AppLogger.log("BLE", it) }
                    saveHistoryToDb(history)
                }
            } catch (e: Exception) {
                AppLogger.log("Service", "History sync failed: ${e.message}")
            }
        }
    }

    private fun saveHistoryToDb(history: List<SensorData>) {
        if (history.isEmpty()) return
        AppLogger.log("Service", "Saving ${history.size} history records...")
        history.forEach { record ->
            try {
                database.sensorDao().insert(SensorEntity(
                    macAddress = record.macAddress,
                    temperature = record.temperature.toFloat(),
                    humidity = record.humidity.toInt(),
                    battery = record.battery,
                    timestamp = record.timestamp
                ))
            } catch (e: Exception) { /* ignore */ }
        }
    }

    private fun updateLiveNotification(it: SensorData) {
        val tempStr = String.format(java.util.Locale.GERMANY, "%.1f", it.temperature)
        val humStr = String.format(java.util.Locale.GERMANY, "%.1f", it.humidity)
        val devName = prefs.getDeviceName(it.macAddress)
        updateNotification(devName, "🌡️ $tempStr°C   💧 $humStr%")
    }

    private fun throttleSaveToDb(it: SensorData) {
        val now = System.currentTimeMillis()
        val lastSave = lastDbSaveMap[it.macAddress] ?: 0L
        if (now - lastSave > 60000) {
            serviceScope.launch(Dispatchers.IO) {
                try {
                    database.sensorDao().insert(SensorEntity(
                        macAddress = it.macAddress,
                        temperature = it.temperature.toFloat(),
                        humidity = it.humidity.toInt(),
                        battery = it.battery,
                        timestamp = it.timestamp
                    ))
                    lastDbSaveMap[it.macAddress] = now
                } catch (e: Exception) {
                    Log.e("SensorService", "DB Save Error", e)
                }
            }
        }
    }
}
