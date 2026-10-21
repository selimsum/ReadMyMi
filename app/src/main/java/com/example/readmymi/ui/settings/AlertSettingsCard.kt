package com.example.readmymi.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import com.example.readmymi.PrefsManager
import com.example.readmymi.ui.AlertThresholdRow
import com.example.readmymi.ui.SettingsCard

/** Alerts card: master switch + temp/humidity/battery thresholds + vibration. */
@Composable
fun AlertSettingsCard(prefs: PrefsManager) {
    var alertsEnabled by remember { mutableStateOf(prefs.alertsEnabled) }
    var alertTempHighEnabled by remember { mutableStateOf(prefs.alertTempHighEnabled) }
    var alertTempLowEnabled by remember { mutableStateOf(prefs.alertTempLowEnabled) }
    var alertHumidityHighEnabled by remember { mutableStateOf(prefs.alertHumidityHighEnabled) }
    var alertHumidityLowEnabled by remember { mutableStateOf(prefs.alertHumidityLowEnabled) }
    var alertTempHigh by remember {
        mutableStateOf(
            if (prefs.tempUnit == "C") prefs.alertTempHigh.toString()
            else String.format(java.util.Locale.US, "%.1f", prefs.alertTempHigh * 1.8f + 32)
        )
    }
    var alertTempLow by remember {
        mutableStateOf(
            if (prefs.tempUnit == "C") prefs.alertTempLow.toString()
            else String.format(java.util.Locale.US, "%.1f", prefs.alertTempLow * 1.8f + 32)
        )
    }
    var alertHumidityHigh by remember { mutableStateOf(prefs.alertHumidityHigh.toString()) }
    var alertHumidityLow by remember { mutableStateOf(prefs.alertHumidityLow.toString()) }
    var alertBatteryLowEnabled by remember { mutableStateOf(prefs.alertBatteryLowEnabled) }
    var alertBatteryLow by remember { mutableStateOf(prefs.alertBatteryLow.toString()) }
    var alertVibrationEnabled by remember { mutableStateOf(prefs.alertVibrationEnabled) }

    fun parseTempInputToCelsius(input: String): Float? {
        val parsed = input.toFloatOrNull() ?: return null
        return if (prefs.tempUnit == "F") (parsed - 32) / 1.8f else parsed
    }

    SettingsCard {
        SettingsSectionHeader("Alerts & Thresholds", androidx.compose.material.icons.Icons.Default.Notifications)

        ThresholdSwitch(
            "Enable Alerts",
            alertsEnabled,
            onCheckedChange = { checked ->
                alertsEnabled = checked
                prefs.alertsEnabled = checked
                prefs.setWasOffline(prefs.lastMac, false)
            }
        )
        if (alertsEnabled) {
            AlertThresholdRow(
                label = if (prefs.tempUnit == "F") "High Temperature (°F)" else "High Temperature (°C)",
                enabled = alertTempHighEnabled,
                value = alertTempHigh,
                keyboardType = KeyboardType.Number,
                onEnabledChange = { alertTempHighEnabled = it; prefs.alertTempHighEnabled = it },
                onValueChange = {
                    alertTempHigh = it
                    parseTempInputToCelsius(it)?.let { c -> prefs.alertTempHigh = c }
                }
            )
            AlertThresholdRow(
                label = if (prefs.tempUnit == "F") "Low Temperature (°F)" else "Low Temperature (°C)",
                enabled = alertTempLowEnabled,
                value = alertTempLow,
                keyboardType = KeyboardType.Number,
                onEnabledChange = { alertTempLowEnabled = it; prefs.alertTempLowEnabled = it },
                onValueChange = {
                    alertTempLow = it
                    parseTempInputToCelsius(it)?.let { c -> prefs.alertTempLow = c }
                }
            )
            AlertThresholdRow(
                label = "High Humidity (%)",
                enabled = alertHumidityHighEnabled,
                value = alertHumidityHigh,
                keyboardType = KeyboardType.Number,
                onEnabledChange = { alertHumidityHighEnabled = it; prefs.alertHumidityHighEnabled = it },
                onValueChange = {
                    alertHumidityHigh = it
                    it.toIntOrNull()?.let { v -> prefs.alertHumidityHigh = v }
                }
            )
            AlertThresholdRow(
                label = "Low Humidity (%)",
                enabled = alertHumidityLowEnabled,
                value = alertHumidityLow,
                keyboardType = KeyboardType.Number,
                onEnabledChange = { alertHumidityLowEnabled = it; prefs.alertHumidityLowEnabled = it },
                onValueChange = {
                    alertHumidityLow = it
                    it.toIntOrNull()?.let { v -> prefs.alertHumidityLow = v }
                }
            )
            AlertThresholdRow(
                label = "Battery Low (%)",
                enabled = alertBatteryLowEnabled,
                value = alertBatteryLow,
                keyboardType = KeyboardType.Number,
                onEnabledChange = { alertBatteryLowEnabled = it; prefs.alertBatteryLowEnabled = it },
                onValueChange = {
                    alertBatteryLow = it
                    it.toIntOrNull()?.let { v -> prefs.alertBatteryLow = v }
                }
            )
            SwitchRow(
                label = "Enable Vibration",
                checked = alertVibrationEnabled,
                onCheckedChange = { alertVibrationEnabled = it; prefs.alertVibrationEnabled = it }
            )
        }
    }
}
