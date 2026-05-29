package com.example.readmymi

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
import android.widget.Toast
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

import com.example.readmymi.data.SensorDatabase
import com.example.readmymi.data.SensorEntity

class SensorForegroundService : Service() {

    // Flag moved to companion
    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "ReadMyMiServiceChannel"
        private const val CHANNEL_SILENT_ID = "ReadMyMiServiceChannelSilent"
        private const val ALERTS_CHANNEL_ID = "ReadMyMiAlertChannel"
        private const val ALERT_NOTIFICATION_BASE_ID = 100
        private const val HISTORY_GAP_THRESHOLD_MS = 30 * 60 * 1000L
        private const val HISTORY_RECORD_INTERVAL_MS = 10 * 60 * 1000L
        private const val DEFAULT_HISTORY_RECORDS = 70
        private const val MAX_HISTORY_RECORDS = 512
        @Volatile
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
    private val lastAlertMap = mutableMapOf<String, Long>() // Throttle alert notifications (per alert key)

    private val latestReadings = mutableMapOf<String, SensorData>()
    private val historyCheckedMap = mutableMapOf<String, Boolean>()
    
    // Flag to control service loop (Accessed via Companion for UI)

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("SensorService", "onStartCommand: action=${intent?.action}")
        if (intent?.action == "STOP_SCAN") {
            isServiceRunning = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
            return START_NOT_STICKY
        } else if (intent?.action == "START_SCAN" || intent?.action == null) {
            if (!isServiceRunning) {
                isServiceRunning = true
                startForegroundService()
                serviceScope.launch {
                    runServiceLoop()
                }
                serviceScope.launch {
                    bluetoothSensorManager.sensorDataFlow.collectLatest { data ->
                        data?.let { handleSensorData(it) }
                    }
                }
            }
        } else if (intent?.action == "UPDATE_NOTIFICATION") {
            if (isServiceRunning) {
                createNotificationChannel()
                val lastData = liveSensorData.value
                if (lastData != null) {
                    updateLiveNotification(lastData)
                } else {
                    updateNotification("Read My Mi Scanner", "Running...")
                }
            }
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("SensorService", "Service Created")
        AppLogger.log("Service", "Service Created")

        // Acquire WakeLock for reliable background scanning
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ReadMyMi::ScanWakeLock")
        wakeLock?.acquire() // Indefinite acquisition until onDestroy

        database = SensorDatabase.getDatabase(this)
        prefs = PrefsManager(this)
        
        bluetoothSensorManager = BluetoothSensorManager(this)


    }
    
    private fun startForegroundService() {
        createNotificationChannel()
        val notification = buildOngoingNotification("Read My Mi Scanner", "Running...")
        startForegroundCompat(notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            // Service status channel (Standard)
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Read My Mi Scanner Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(serviceChannel)
            
            // Service status channel (Silent)
            val silentChannel = NotificationChannel(
                CHANNEL_SILENT_ID,
                "Read My Mi Scanner Service Channel (Silent)",
                NotificationManager.IMPORTANCE_MIN
            )
            manager.createNotificationChannel(silentChannel)
            
            // Delete alert channel first to dynamically apply vibration changes immediately
            manager.deleteNotificationChannel(ALERTS_CHANNEL_ID)
            
            // Alert channel has higher importance so it shows as heads-up.
            val alertChannel = NotificationChannel(
                ALERTS_CHANNEL_ID,
                "Sensor Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when temperature or humidity thresholds are exceeded"
                enableVibration(prefs.alertVibrationEnabled)
                if (!prefs.alertVibrationEnabled) {
                    vibrationPattern = longArrayOf(0)
                }
            }
            manager.createNotificationChannel(alertChannel)
        }
    }

    private suspend fun runServiceLoop() {
        while (isServiceRunning) {
            val scanStartTime = System.currentTimeMillis()
            performScan()
            
            checkOfflineStatus(scanStartTime)
            if (prefs.alertsEnabled) checkAlerts(prefs)
            checkTimeSync()
            performAutoPruning()
            
            runSleepLoop()
        }
    }

    private suspend fun performAutoPruning() {
        val now = System.currentTimeMillis()
        val lastCheck = lastDbSaveMap["AUTO_PRUNE_CHECK"] ?: 0L
        if (now - lastCheck > 3600000L) { // Once per hour
            lastDbSaveMap["AUTO_PRUNE_CHECK"] = now
            val pruningDays = prefs.autoPruningDays
            if (pruningDays > 0) {
                val cutoffTime = now - pruningDays * 24 * 60 * 60 * 1000L
                try {
                    database.sensorDao().deleteOldData(cutoffTime)
                    AppLogger.log("Service", "Auto-pruning complete: deleted history older than $pruningDays days.")
                } catch (e: Exception) {
                    AppLogger.log("Service", "Auto-pruning error: ${e.message}")
                }
            }
        }
    }

    private fun checkAlerts(prefs: PrefsManager) {
        val now = System.currentTimeMillis()

        latestReadings.forEach { (mac, data) ->
            val devName = prefs.getDeviceName(mac)

            fun maybeAlert(key: String, title: String, message: String) {
                val alertedKey = "$mac:$key"
                if (!lastAlertMap.containsKey(alertedKey)) {
                    lastAlertMap[alertedKey] = now
                    sendAlertNotification(key.hashCode() + ALERT_NOTIFICATION_BASE_ID, title, "$devName: $message")
                    AppLogger.log("Alert", "[$devName] $message")
                }
            }

            fun clearAlert(key: String) {
                lastAlertMap.remove("$mac:$key")
            }

            // Temperature alerts
            if (prefs.alertTempHighEnabled && data.temperature > prefs.alertTempHigh) {
                maybeAlert("temp_high", "High Temperature", "${String.format("%.1f", data.temperature)}C > ${prefs.alertTempHigh}C")
            } else { clearAlert("temp_high") }

            if (prefs.alertTempLowEnabled && data.temperature < prefs.alertTempLow) {
                maybeAlert("temp_low", "Low Temperature", "${String.format("%.1f", data.temperature)}C < ${prefs.alertTempLow}C")
            } else { clearAlert("temp_low") }

            // Humidity alerts
            if (prefs.alertHumidityHighEnabled && data.humidity > prefs.alertHumidityHigh) {
                maybeAlert("hum_high", "High Humidity", "${PercentFormatter.format(data.humidity)} > ${PercentFormatter.format(prefs.alertHumidityHigh)}")
            } else { clearAlert("hum_high") }

            if (prefs.alertHumidityLowEnabled && data.humidity < prefs.alertHumidityLow) {
                maybeAlert("hum_low", "Low Humidity", "${PercentFormatter.format(data.humidity)} < ${PercentFormatter.format(prefs.alertHumidityLow)}")
            } else { clearAlert("hum_low") }

            // Battery alerts
            if (prefs.alertBatteryLowEnabled && data.battery < prefs.alertBatteryLow) {
                maybeAlert("battery_low", "Low Battery", "Battery level is ${data.battery}% < ${prefs.alertBatteryLow}%")
            } else { clearAlert("battery_low") }
        }
    }

    private fun sendAlertNotification(id: Int, title: String, text: String) {
        val pendingIntent = createPendingIntent()
        val notification = NotificationCompat.Builder(this, ALERTS_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_app_logo_png)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        getSystemService(NotificationManager::class.java).notify(id, notification)
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
            val timeoutMs = prefs.offlineTimeoutMinutes * 60 * 1000L
            val isCurrentlyOffline = if (lastSeen > 0L) {
                (scanStartTime - lastSeen) > timeoutMs
            } else {
                true
            }
            val wasOffline = prefs.getWasOffline(targetMac)
            when {
                isCurrentlyOffline && !wasOffline -> {
                    prefs.setWasOffline(targetMac, true)
                    AppLogger.log("Service", "Device $targetMac went offline (no data for ${prefs.offlineTimeoutMinutes} mins).")
                }
                !isCurrentlyOffline && wasOffline -> {
                    prefs.setWasOffline(targetMac, false)
                    AppLogger.log("Service", "Device $targetMac came back online.")
                }
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
                if (now - lastSync > 86400000L) { // Daily sync (24 hours)
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
        
        checkAndDownloadHistory(data.macAddress)
        
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
        val mode = prefs.autoHistorySyncMode
        if (mode == "off") {
            return
        }
        
        val now = System.currentTimeMillis()
        if (mode == "start") {
            if (historyCheckedMap.containsKey(mac)) {
                return
            }
            historyCheckedMap[mac] = true
        } else {
            val intervalMs = if (mode == "12h") 12 * 3600 * 1000L else 24 * 3600 * 1000L
            val lastSync = prefs.getLastHistorySyncTimestamp(mac)
            if (now - lastSync < intervalMs) {
                return
            }
        }

        serviceScope.launch(Dispatchers.IO) {
            try {
                val latestDbTimestamp = database.sensorDao().getLatestTimestamp(mac)
                val missingSince = latestDbTimestamp ?: 0L
                val missingDuration = now - missingSince

                if (latestDbTimestamp == null || missingDuration > HISTORY_GAP_THRESHOLD_MS) {
                    val recordsToDownload = estimateHistoryRecordCount(missingDuration, latestDbTimestamp == null)
                    AppLogger.log("Service", "Syncing history ($mode). Downloading up to $recordsToDownload records...")
                    val history = bluetoothSensorManager.downloadHistory(mac, records = recordsToDownload) { AppLogger.log("BLE", it) }
                    val missingHistory = history
                        .filter { it.timestamp > missingSince && it.timestamp <= now }
                        .distinctBy { it.timestamp }
                        .sortedBy { it.timestamp }

                    if (missingHistory.isNotEmpty()) {
                        saveHistoryToDb(missingHistory)
                        prefs.setLastHistorySyncTimestamp(mac, now)
                    } else {
                        AppLogger.log("Service", "No missing history records found on device.")
                        prefs.setLastHistorySyncTimestamp(mac, now)
                    }
                }
            } catch (e: Exception) {
                AppLogger.log("Service", "History sync failed: ${e.message}")
            }
        }
    }

    private fun estimateHistoryRecordCount(missingDuration: Long, isInitialSync: Boolean): Int {
        if (isInitialSync) return DEFAULT_HISTORY_RECORDS
        val estimatedRecords = (missingDuration / HISTORY_RECORD_INTERVAL_MS).toInt() + 2
        return estimatedRecords.coerceIn(1, MAX_HISTORY_RECORDS)
    }

    private suspend fun saveHistoryToDb(history: List<SensorData>) {
        if (history.isEmpty()) return
        AppLogger.log("Service", "Saving ${history.size} history records...")
        try {
            val entities = history.map { record ->
                SensorEntity(
                    macAddress = record.macAddress,
                    temperature = record.temperature.toFloat(),
                    humidity = record.humidity.toInt(),
                    battery = record.battery,
                    timestamp = record.timestamp
                )
            }
            database.sensorDao().insertAll(entities)
        } catch (e: Exception) {
            AppLogger.log("Service", "Failed to save history: ${e.message}")
        }
    }

    private fun updateLiveNotification(it: SensorData) {
        val tempStr = String.format(java.util.Locale.GERMANY, "%.1f", it.temperature)
        val humStr = PercentFormatter.format(it.humidity)
        val devName = prefs.getDeviceName(it.macAddress)
        updateNotification(devName, "🌡 ${tempStr}°C   💧 $humStr")
    }

    private fun updateNotification(title: String, text: String) {
        val notification = buildOngoingNotification(title, text)
        startForegroundCompat(notification)
    }

    private fun buildOngoingNotification(title: String, text: String): Notification {
        val pendingIntent = createPendingIntent()
        val channelId = if (prefs.ongoingNotificationEnabled) CHANNEL_ID else CHANNEL_SILENT_ID
        val priority = if (prefs.ongoingNotificationEnabled) {
            NotificationCompat.PRIORITY_LOW
        } else {
            NotificationCompat.PRIORITY_MIN
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_app_logo_png)
            .setContentIntent(pendingIntent)
            .setSilent(true)
            .setPriority(priority)
            .build()
    }

    private fun createPendingIntent(): PendingIntent {
        val notificationIntent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
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

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        val manager = getSystemService(NotificationManager::class.java)
        manager.cancel(NOTIFICATION_ID)
        wakeLock?.release()
        serviceScope.cancel()
    }
}
