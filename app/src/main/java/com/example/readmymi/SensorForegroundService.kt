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
        private const val ALERTS_CHANNEL_ID = "ReadMyMiAlertChannel"
        private const val ALERT_NOTIFICATION_BASE_ID = 100
        private const val HISTORY_GAP_THRESHOLD_MS = 30 * 60 * 1000L
        private const val HISTORY_RECORD_INTERVAL_MS = 10 * 60 * 1000L
        private const val DEFAULT_HISTORY_RECORDS = 70
        private const val MAX_HISTORY_RECORDS = 512
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
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Read My Mi Scanner")
            .setContentText("Running...")
            .setSmallIcon(R.drawable.ic_app_logo_png)
            .setContentIntent(pendingIntent)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            // Service status channel
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Read My Mi Scanner Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(serviceChannel)
            // Alert channel has higher importance so it shows as heads-up.
            val alertChannel = NotificationChannel(
                ALERTS_CHANNEL_ID,
                "Sensor Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when temperature or humidity thresholds are exceeded"
                enableVibration(true)
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
            
            runSleepLoop()
        }
    }

    private fun checkAlerts(prefs: PrefsManager) {
        val cooldownMs = 3600_000L // 1 hour between repeated alerts for the same condition
        val now = System.currentTimeMillis()

        latestReadings.forEach { (mac, data) ->
            val devName = prefs.getDeviceName(mac)

            fun maybeAlert(key: String, title: String, message: String) {
                val lastAlert = lastAlertMap["$mac:$key"] ?: 0L
                if (now - lastAlert >= cooldownMs) {
                    lastAlertMap["$mac:$key"] = now
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
        }
    }

    private fun sendAlertNotification(id: Int, title: String, text: String) {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, ALERTS_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
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
            val isCurrentlyOffline = lastSeen < scanStartTime
            val wasOffline = prefs.getWasOffline(targetMac)
            when {
                isCurrentlyOffline && !wasOffline -> {
                    prefs.setWasOffline(targetMac, true)
                    AppLogger.log("Service", "Device $targetMac went offline.")
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
                val missingSince = latestDbTimestamp ?: 0L
                val missingDuration = now - missingSince

                if (latestDbTimestamp == null || missingDuration > HISTORY_GAP_THRESHOLD_MS) {
                    val recordsToDownload = estimateHistoryRecordCount(missingDuration, latestDbTimestamp == null)
                    AppLogger.log("Service", "Data gap detected. Downloading up to $recordsToDownload history records...")
                    val history = bluetoothSensorManager.downloadHistory(mac, records = recordsToDownload) { AppLogger.log("BLE", it) }
                    val missingHistory = history
                        .filter { it.timestamp > missingSince && it.timestamp <= now }
                        .distinctBy { it.timestamp }
                        .sortedBy { it.timestamp }

                    if (missingHistory.isNotEmpty()) {
                        saveHistoryToDb(missingHistory)
                    } else {
                        AppLogger.log("Service", "No missing history records found on device.")
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

    private fun saveHistoryToDb(history: List<SensorData>) {
        if (history.isEmpty()) return
        AppLogger.log("Service", "Saving ${history.size} history records...")
        serviceScope.launch(Dispatchers.IO) {
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
    }

    private fun updateLiveNotification(it: SensorData) {
        val tempStr = String.format(java.util.Locale.GERMANY, "%.1f", it.temperature)
        val humStr = PercentFormatter.format(it.humidity)
        val lastUpdate = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(it.timestamp))
        val devName = prefs.getDeviceName(it.macAddress)
        updateNotification(devName, "🌡 ${tempStr}°C   💧 $humStr   🕒 $lastUpdate")
    }

    private fun updateNotification(title: String, text: String) {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_app_logo_png)
            .setContentIntent(pendingIntent)
            .setSilent(true) // Don't beep on every update
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
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
