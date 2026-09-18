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
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

import com.example.readmymi.data.SensorDatabase
import com.example.readmymi.data.SensorEntity

class SensorForegroundService : Service() {

    // Flag moved to companion
    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "ReadMyMiServiceChannel"
        private const val CHANNEL_SILENT_ID = "ReadMyMiServiceChannelSilent"
        private const val ALERTS_CHANNEL_ID = "ReadMyMiAlertChannel"
        const val HISTORY_GAP_THRESHOLD_MS = 30 * 60 * 1000L
        const val HISTORY_RECORD_INTERVAL_MS = 10 * 60 * 1000L
        const val DEFAULT_HISTORY_RECORDS = 70
        const val MAX_HISTORY_RECORDS = 10000
        @Volatile
        var isServiceRunning = false
        val isServiceRunningFlow = kotlinx.coroutines.flow.MutableStateFlow(false)
        val liveSensorData = kotlinx.coroutines.flow.MutableStateFlow<SensorData?>(null)
        val serviceStatus = kotlinx.coroutines.flow.MutableStateFlow("Initializing...")

        fun findGapInTimestamps(recentTimestamps: List<Long>, incomingTimestamp: Long): Pair<Long, Long>? {
            if (recentTimestamps.isEmpty()) {
                return Pair(0L, 24 * 3600 * 1000L) // Initial sync: 24h
            }

            // 1. Check if there is a gap between the latest DB record and incoming reading
            val latestDb = recentTimestamps.first()
            val latestGap = incomingTimestamp - latestDb
            if (latestGap > HISTORY_GAP_THRESHOLD_MS) {
                return Pair(latestDb, latestGap)
            }

            // 2. Check for gaps between adjacent historical records in recent history
            // (e.g., live records were recently inserted after an offline period)
            for (i in 0 until recentTimestamps.size - 1) {
                val newer = recentTimestamps[i]
                val older = recentTimestamps[i + 1]
                val gap = newer - older
                if (gap > HISTORY_GAP_THRESHOLD_MS) {
                    return Pair(older, incomingTimestamp - older)
                }
            }

            return null
        }

        fun estimateHistoryRecordCount(missingDuration: Long, isInitialSync: Boolean): Int {
            if (isInitialSync) return DEFAULT_HISTORY_RECORDS
            // Estimate records with 5-minute sampling resolution to ensure no records are missed
            val estimatedRecords = (missingDuration / (5 * 60 * 1000L)).toInt() + 20
            return estimatedRecords.coerceIn(DEFAULT_HISTORY_RECORDS, MAX_HISTORY_RECORDS)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var bluetoothSensorManager: BluetoothSensorManager


    private val lastPublishMap = ConcurrentHashMap<String, Long>()
    private var wakeLock: PowerManager.WakeLock? = null
    
    private lateinit var database: SensorDatabase
    private lateinit var prefs: PrefsManager
    private val lastDbSaveMap = ConcurrentHashMap<String, Long>() // Throttle DB saves
    private val dbSaveBuffer = java.util.concurrent.ConcurrentLinkedQueue<SensorEntity>()
    private val lastDbBatchSaveTime = AtomicLong(0L)
    private var lastVibrationEnabled = true
    private var alertIdCounter = 200
    private val alertKeyIds = ConcurrentHashMap<String, Int>()

    private val latestReadings = ConcurrentHashMap<String, SensorData>()
    private val historyCheckedMap = ConcurrentHashMap.newKeySet<String>()
    private val historySyncInFlight = ConcurrentHashMap.newKeySet<String>()
    private val lastHistoryAttemptMap = ConcurrentHashMap<String, Long>()
    
    // Flag to control service loop (Accessed via Companion for UI)

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.log("SensorService", "onStartCommand: action=${intent?.action}")
        if (intent?.action == "STOP_SCAN") {
            isServiceRunning = false
            isServiceRunningFlow.value = false
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
                isServiceRunningFlow.value = true
                startForegroundService()
                serviceScope.launch {
                    runServiceLoop()
                }
                serviceScope.launch {
                    // collect (not collectLatest): every buffered packet is handled so
                    // history-sync GATT work is never cancelled by the next advertisement.
                    bluetoothSensorManager.sensorDataFlow.collect { data ->
                        handleSensorData(data)
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
            } else {
                // Settings sends this intent even when the scanner is stopped.
                // Letting the service start for it would leave a process with
                // no scan loop and no foreground notification, so back out again.
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        AppLogger.log("Service", "Service Created")

        // Created here, held only while the radio is in use — see acquireWakeLock.
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ReadMyMi::ScanWakeLock")

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
            
            // Only delete and recreate alert channel when vibration setting changes
            if (prefs.alertVibrationEnabled != lastVibrationEnabled) {
                lastVibrationEnabled = prefs.alertVibrationEnabled
                manager.deleteNotificationChannel(ALERTS_CHANNEL_ID)
            }
            
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
            
            // Wait for any in-flight history sync to complete before proceeding
            while (historySyncInFlight.isNotEmpty() && isServiceRunning) {
                kotlinx.coroutines.delay(1000)
            }

            checkOfflineStatus(scanStartTime)
            if (prefs.alertsEnabled) checkAlerts(prefs)
            checkTimeSync()
            performAutoPruning()
            
            runSleepLoop()
        }
    }

    // The BLE controller wakes the AP for scan results, so holding the lock while
    // idle between scans would only keep the CPU from suspending. Acquire it just
    // for the radio-active phases (scan window, history download); every acquire
    // is paired with a release in a finally block. The lock is reference-counted
    // (the PowerManager default), so an overlapping scan and download stay held
    // until both finish.
    private fun acquireWakeLock() {
        try {
            wakeLock?.acquire()
        } catch (e: Exception) {
            AppLogger.log("Service", "WakeLock acquire error: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.takeIf { it.isHeld }?.release()
        } catch (e: Exception) {
            AppLogger.log("Service", "WakeLock release error: ${e.message}")
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
        latestReadings.forEach { (mac, data) ->
            val devName = prefs.getDeviceName(mac)

            // Edge-triggered alert: fires once on the rising edge (below -> above the
            // threshold) and only re-arms after the value returns below the threshold.
            // The active state is persisted in prefs, so service restarts do not re-fire.
            fun maybeAlert(key: String, title: String, message: String, active: Boolean) {
                val alertedKey = "$mac:$key"
                val wasActive = prefs.isAlertActive(mac, key)
                if (active && !wasActive) {
                    prefs.setAlertActive(mac, key, true)
                    val notifId = alertKeyIds.getOrPut(alertedKey) { alertIdCounter++ }
                    sendAlertNotification(notifId, title, "$devName: $message")
                    AppLogger.log("Alert", "[$devName] $message")
                } else if (!active && wasActive) {
                    prefs.setAlertActive(mac, key, false)
                }
            }

            // Temperature alerts
            maybeAlert(
                "temp_high", "High Temperature",
                "${String.format("%.1f", data.temperature)}°C > ${prefs.alertTempHigh}°C",
                prefs.alertTempHighEnabled && data.temperature > prefs.alertTempHigh
            )

            maybeAlert(
                "temp_low", "Low Temperature",
                "${String.format("%.1f", data.temperature)}°C < ${prefs.alertTempLow}°C",
                prefs.alertTempLowEnabled && data.temperature < prefs.alertTempLow
            )

            // Humidity alerts
            maybeAlert(
                "hum_high", "High Humidity",
                "${PercentFormatter.format(data.humidity)} > ${PercentFormatter.format(prefs.alertHumidityHigh)}",
                prefs.alertHumidityHighEnabled && data.humidity > prefs.alertHumidityHigh
            )

            maybeAlert(
                "hum_low", "Low Humidity",
                "${PercentFormatter.format(data.humidity)} < ${PercentFormatter.format(prefs.alertHumidityLow)}",
                prefs.alertHumidityLowEnabled && data.humidity < prefs.alertHumidityLow
            )

            // Battery alerts
            maybeAlert(
                "battery_low", "Low Battery",
                "Battery level is ${data.battery}% < ${prefs.alertBatteryLow}%",
                prefs.alertBatteryLowEnabled && data.battery < prefs.alertBatteryLow
            )
        }
    }

    private fun sendAlertNotification(id: Int, title: String, text: String) {
        val pendingIntent = createPendingIntent()
        val notification = NotificationCompat.Builder(this, ALERTS_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_thermometer)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        getSystemService(NotificationManager::class.java).notify(id, notification)
    }

    private suspend fun performScan() {
        if (historySyncInFlight.isNotEmpty() || BluetoothSensorManager.isGattBusy) {
            AppLogger.log("Service", "Skipping scan: history sync or GATT in flight")
            return
        }
        AppLogger.log("Service", "Starting Periodic Scan (15s)")
        serviceStatus.value = "Scanning (15s)..."
        try {
            acquireWakeLock()
            bluetoothSensorManager.startScanning(prefs.lastMac)
            kotlinx.coroutines.delay(15000)
            bluetoothSensorManager.stopScanning()
        } catch (e: Exception) {
            AppLogger.log("Service", "Scan error: ${e.message}")
        } finally {
            releaseWakeLock()
        }
        serviceStatus.value = "Processing & Sleeping..."
    }

    private fun checkOfflineStatus(scanStartTime: Long) {
        val targetMac = prefs.lastMac
        if (targetMac.isNotEmpty()) {
            val lastSeen = lastPublishMap[targetMac] ?: 0L
            val timeoutMs = prefs.effectiveOfflineTimeoutMinutes * 60 * 1000L
            val isCurrentlyOffline = if (lastSeen > 0L) {
                (scanStartTime - lastSeen) > timeoutMs
            } else {
                true
            }
            val wasOffline = prefs.getWasOffline(targetMac)
            when {
                isCurrentlyOffline && !wasOffline -> {
                    prefs.setWasOffline(targetMac, true)
                    AppLogger.log("Service", "Device $targetMac went offline (no data for ${prefs.effectiveOfflineTimeoutMinutes} mins).")
                }
                !isCurrentlyOffline && wasOffline -> {
                    AppLogger.log("Service", "Device $targetMac came back online.")
                    historyCheckedMap.remove(targetMac)
                }
            }
        }
    }

    private suspend fun checkTimeSync() {
        if (historySyncInFlight.isNotEmpty() || BluetoothSensorManager.isGattBusy) {
            AppLogger.log("Service", "Skipping time sync check: GATT busy or history sync in flight")
            return
        }
        val now = System.currentTimeMillis()
        val lastCheck = lastDbSaveMap["TIME_SYNC_CHECK"] ?: 0L
        if (now - lastCheck > 3600000L) {
            val targetMac = prefs.lastMac
            if (targetMac.isNotEmpty()) {
                val lastSync = prefs.getLastTimeSync(targetMac)
                if (now - lastSync > 86400000L) { // Daily sync (24 hours)
                    lastDbSaveMap["TIME_SYNC_CHECK"] = now
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
        
        checkAndDownloadHistory(data.macAddress, data.timestamp)
        
        updateLiveNotification(data)
        throttleSaveToDb(data)
    }

    private fun updateLastPublish(mac: String) {
        val now = System.currentTimeMillis()
        if (lastPublishMap.put(mac, now) == null) {
            AppLogger.log("Service", "Found new device: $mac")
        }
    }

    private fun checkAndDownloadHistory(mac: String, incomingTimestamp: Long) {
        val mode = prefs.autoHistorySyncMode
        if (mode == "off") {
            return
        }

        val now = System.currentTimeMillis()
        val wasOffline = prefs.getWasOffline(mac)
        val forceSync = wasOffline

        if (forceSync) {
            AppLogger.log("Service", "Device $mac was offline and is now back online. Forcing history sync...")
        } else if (mode == "start") {
            // Persisted marker (not just in-memory historyCheckedMap) so process
            // restarts don't re-pull the initial 24h/70-record sync every time.
            if (historyCheckedMap.contains(mac) || prefs.getHistoryChecked(mac)) {
                historyCheckedMap.add(mac)
                return
            }
        } else {
            val intervalMs = if (mode == "12h") 12 * 3600 * 1000L else 24 * 3600 * 1000L
            val lastSync = prefs.getLastHistorySyncTimestamp(mac)
            if (now - lastSync < intervalMs) {
                return
            }
        }

        val lastAttempt = lastHistoryAttemptMap[mac] ?: 0L
        if (now - lastAttempt < 60000L) {
            return
        }

        // wasOffline stays true for every packet of a scan window until
        // checkOfflineStatus() runs, so without this guard a device coming back
        // online would launch one GATT download per received packet.
        if (!historySyncInFlight.add(mac)) {
            AppLogger.log("Service", "History sync already in progress for $mac; skipping.")
            return
        }

        serviceScope.launch(Dispatchers.IO) {
            try {
                acquireWakeLock()
                lastHistoryAttemptMap[mac] = now
                bluetoothSensorManager.stopScanning()

                val recentTimestamps = database.sensorDao().getRecentTimestamps(mac, limit = 100)
                val gapInfo = findGapInTimestamps(recentTimestamps, incomingTimestamp)

                if (gapInfo != null) {
                    val (missingSince, gapDuration) = gapInfo
                    val recordsToDownload = estimateHistoryRecordCount(gapDuration, recentTimestamps.isEmpty())
                    AppLogger.log("Service", "Syncing history ($mode). Downloading up to $recordsToDownload records, missingSince=$missingSince (gap ${gapDuration / 60000}m)...")
                    val history = bluetoothSensorManager.downloadHistory(mac, records = recordsToDownload, lastDbTimestamp = missingSince) { AppLogger.log("BLE", it) }

                    val missingHistory = history
                        .asSequence()
                        .filter { it.timestamp <= incomingTimestamp }
                        .distinctBy { it.timestamp }
                        .toList()
                        .sortedBy { it.timestamp }

                    if (missingHistory.isNotEmpty()) {
                        saveHistoryToDb(missingHistory)
                        val latestSavedTimestamp = missingHistory.maxOf { it.timestamp }
                        prefs.setLastHistorySyncTimestamp(mac, latestSavedTimestamp)
                        prefs.setLastTimeSync(mac, now)
                        prefs.setWasOffline(mac, false)
                        prefs.setHistoryChecked(mac, true)
                        historyCheckedMap.add(mac)
                        AppLogger.log("Service", "History sync complete: saved ${missingHistory.size} records.")
                    } else {
                        AppLogger.log("Service", "No missing history records found on device.")
                        prefs.setLastHistorySyncTimestamp(mac, incomingTimestamp)
                        prefs.setWasOffline(mac, false)
                        prefs.setHistoryChecked(mac, true)
                        historyCheckedMap.add(mac)
                    }
                } else {
                    prefs.setWasOffline(mac, false)
                    prefs.setHistoryChecked(mac, true)
                    historyCheckedMap.add(mac)
                }
            } catch (e: Exception) {
                AppLogger.log("Service", "History sync failed: ${e.message}")
                historyCheckedMap.remove(mac)
            } finally {
                historySyncInFlight.remove(mac)
                releaseWakeLock()
            }
        }
    }

    private suspend fun saveHistoryToDb(history: List<SensorData>) {
        if (history.isEmpty()) return
        AppLogger.log("Service", "Saving ${history.size} history records...")
        try {
            val entities = history.map { record ->
                SensorEntity(
                    macAddress = record.macAddress,
                    temperature = record.temperature.toFloat(),
                    humidity = record.humidity.toFloat(),
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
        val tempStr = String.format(java.util.Locale.getDefault(), "%.1f", it.temperature)
        val humStr = PercentFormatter.format(it.humidity)
        val devName = prefs.getDeviceName(it.macAddress)
        val isHappy = it.temperature in 21.0..26.0 && it.humidity in 30.0..60.0
        val comfortIcon = if (isHappy) "😊" else "🙁"
        updateNotification(devName, "🌡 ${tempStr}°C   💧 $humStr   $comfortIcon")
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
            .setSmallIcon(R.drawable.ic_stat_thermometer)
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
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun throttleSaveToDb(it: SensorData) {
        val now = System.currentTimeMillis()
        val lastSave = lastDbSaveMap[it.macAddress] ?: 0L
        if (now - lastSave > 60000) {
            dbSaveBuffer.add(SensorEntity(
                macAddress = it.macAddress,
                temperature = it.temperature.toFloat(),
                humidity = it.humidity.toFloat(),
                battery = it.battery,
                timestamp = it.timestamp
            ))
            lastDbSaveMap[it.macAddress] = now

            if (dbSaveBuffer.size >= 10 || now - lastDbBatchSaveTime.get() > 60000) {
                flushDbBuffer()
            }
        }
    }

    private fun flushDbBuffer() {
        if (dbSaveBuffer.isEmpty()) return
        val entitiesToSave = mutableListOf<SensorEntity>()
        while (dbSaveBuffer.isNotEmpty()) {
            dbSaveBuffer.poll()?.let { entitiesToSave.add(it) }
        }
        if (entitiesToSave.isNotEmpty()) {
            lastDbBatchSaveTime.set(System.currentTimeMillis())
            serviceScope.launch(Dispatchers.IO) {
                try {
                    database.sensorDao().insertAll(entitiesToSave)
                } catch (e: Exception) {
                    AppLogger.log("SensorService", "DB Save Error: ${e.message}")
                }
            }
        }
    }

    /** Synchronous final flush: must run before serviceScope.cancel() in onDestroy. */
    private fun flushDbBufferSync() {
        if (dbSaveBuffer.isEmpty()) return
        val entitiesToSave = mutableListOf<SensorEntity>()
        while (dbSaveBuffer.isNotEmpty()) {
            dbSaveBuffer.poll()?.let { entitiesToSave.add(it) }
        }
        if (entitiesToSave.isNotEmpty()) {
            lastDbBatchSaveTime.set(System.currentTimeMillis())
            runBlocking(Dispatchers.IO) {
                try {
                    database.sensorDao().insertAll(entitiesToSave)
                } catch (e: Exception) {
                    AppLogger.log("SensorService", "DB Save Error: ${e.message}")
                }
            }
        }
    }

    override fun onDestroy() {
        flushDbBufferSync()
        super.onDestroy()
        isServiceRunning = false
        isServiceRunningFlow.value = false
        val manager = getSystemService(NotificationManager::class.java)
        manager.cancel(NOTIFICATION_ID)
        wakeLock?.takeIf { it.isHeld }?.release()
        serviceScope.cancel()
    }
}
