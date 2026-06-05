package com.example.readmymi

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ReadMyMiPrefs", Context.MODE_PRIVATE)



    var lastMac: String
        get() = prefs.getString("last_mac", "") ?: ""
        set(value) = prefs.edit().putString("last_mac", value).apply()

    var scanIntervalSeconds: Int
        get() = prefs.getInt("scan_interval", 300)
        set(value) = prefs.edit().putInt("scan_interval", value).apply()

    var alertsEnabled: Boolean
        get() = prefs.getBoolean("alerts_enabled", false)
        set(value) = prefs.edit().putBoolean("alerts_enabled", value).apply()

    var alertTempHigh: Float
        get() = prefs.getFloat("alert_temp_high", 30.0f)
        set(value) = prefs.edit().putFloat("alert_temp_high", value).apply()

    var alertTempHighEnabled: Boolean
        get() = prefs.getBoolean("alert_temp_high_enabled", false)
        set(value) = prefs.edit().putBoolean("alert_temp_high_enabled", value).apply()

    var alertTempLow: Float
        get() = prefs.getFloat("alert_temp_low", 15.0f)
        set(value) = prefs.edit().putFloat("alert_temp_low", value).apply()

    var alertTempLowEnabled: Boolean
        get() = prefs.getBoolean("alert_temp_low_enabled", false)
        set(value) = prefs.edit().putBoolean("alert_temp_low_enabled", value).apply()

    var alertHumidityHigh: Int
        get() = prefs.getInt("alert_hum_high", 70)
        set(value) = prefs.edit().putInt("alert_hum_high", value).apply()

    var alertHumidityHighEnabled: Boolean
        get() = prefs.getBoolean("alert_hum_high_enabled", false)
        set(value) = prefs.edit().putBoolean("alert_hum_high_enabled", value).apply()

    var alertHumidityLow: Int
        get() = prefs.getInt("alert_hum_low", 30)
        set(value) = prefs.edit().putInt("alert_hum_low", value).apply()

    var alertHumidityLowEnabled: Boolean
        get() = prefs.getBoolean("alert_hum_low_enabled", false)
        set(value) = prefs.edit().putBoolean("alert_hum_low_enabled", value).apply()
        
    fun getWasOffline(mac: String): Boolean {
        return prefs.getBoolean("was_offline_$mac", false)
    }

    fun setWasOffline(mac: String, offline: Boolean) {
        prefs.edit().putBoolean("was_offline_$mac", offline).apply()
    }

    fun getDeviceName(mac: String): String {
        return prefs.getString("name_$mac", mac) ?: mac
    }
    
    fun setDeviceName(mac: String, name: String) {
        prefs.edit().putString("name_$mac", name).apply()
    }

    fun getOriginalDeviceName(mac: String): String {
        return prefs.getString("orig_name_$mac", "") ?: ""
    }

    fun setOriginalDeviceName(mac: String, name: String) {
        if (name.isNotEmpty()) {
            prefs.edit().putString("orig_name_$mac", name).apply()
        }
    }

    fun getLastTimeSync(mac: String): Long {
        return prefs.getLong("last_time_sync_$mac", 0L)
    }

    fun setLastTimeSync(mac: String, timestamp: Long) {
        prefs.edit().putLong("last_time_sync_$mac", timestamp).apply()
    }

    var tempUnit: String
        get() = prefs.getString("temp_unit", "C") ?: "C"
        set(value) = prefs.edit().putString("temp_unit", value).apply()

    var alertBatteryLowEnabled: Boolean
        get() = prefs.getBoolean("alert_battery_low_enabled", false)
        set(value) = prefs.edit().putBoolean("alert_battery_low_enabled", value).apply()

    var alertBatteryLow: Int
        get() = prefs.getInt("alert_battery_low", 20)
        set(value) = prefs.edit().putInt("alert_battery_low", value).apply()

    var offlineTimeoutMinutes: Int
        get() = prefs.getInt("offline_timeout_minutes", 15)
        set(value) = prefs.edit().putInt("offline_timeout_minutes", value).apply()

    var offlineTimeoutOverride: Boolean
        get() = prefs.getBoolean("offline_timeout_override", false)
        set(value) = prefs.edit().putBoolean("offline_timeout_override", value).apply()

    /** Returns the effective offline timeout in minutes.
     *  If override is disabled, defaults to scanInterval × 2 (converted from seconds to minutes).
     *  If override is enabled, uses the user-set offlineTimeoutMinutes value. */
    val effectiveOfflineTimeoutMinutes: Int
        get() = if (offlineTimeoutOverride) offlineTimeoutMinutes
                else (scanIntervalSeconds * 2 / 60).coerceAtLeast(1)

    var autoPruningDays: Int
        get() = prefs.getInt("auto_pruning_days", 0)
        set(value) = prefs.edit().putInt("auto_pruning_days", value).apply()

    var alertVibrationEnabled: Boolean
        get() = prefs.getBoolean("alert_vibration_enabled", true)
        set(value) = prefs.edit().putBoolean("alert_vibration_enabled", value).apply()

    var autoHistorySyncMode: String
        get() = prefs.getString("auto_history_sync_mode", "start") ?: "start"
        set(value) = prefs.edit().putString("auto_history_sync_mode", value).apply()

    var ongoingNotificationEnabled: Boolean
        get() = prefs.getBoolean("ongoing_notification_enabled", true)
        set(value) = prefs.edit().putBoolean("ongoing_notification_enabled", value).apply()

    var chartZoomMode: String
        get() = prefs.getString("chart_zoom_mode", "hv") ?: "hv"
        set(value) = prefs.edit().putString("chart_zoom_mode", value).apply()

    fun getLastHistorySyncTimestamp(mac: String): Long {
        return prefs.getLong("last_history_sync_$mac", 0L)
    }

    fun setLastHistorySyncTimestamp(mac: String, timestamp: Long) {
        prefs.edit().putLong("last_history_sync_$mac", timestamp).apply()
    }
}
