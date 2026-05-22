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
}
