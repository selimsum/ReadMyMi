package com.example.readmymi.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.readmymi.PrefsManager
import com.example.readmymi.ui.DropdownSelector
import com.example.readmymi.ui.SettingsCard

/** Display card: temperature unit + ongoing-notification toggle. */
@Composable
fun DisplaySettingsCard(
    prefs: PrefsManager,
    tempUnit: String = prefs.tempUnit,
    onTempUnitChange: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var selectedTempUnit by remember(tempUnit) { mutableStateOf(tempUnit) }
    var ongoingNotificationEnabled by remember { mutableStateOf(prefs.ongoingNotificationEnabled) }

    SettingsCard {
        SettingsSectionHeader("Display & Notifications", Icons.Default.Notifications)

        DropdownSelector(
            label = "Temperature Unit",
            options = listOf("C", "F"),
            selectedOption = selectedTempUnit,
            optionToString = { u -> if (u == "F") "Fahrenheit (°F)" else "Celsius (°C)" },
            onOptionSelected = { u ->
                selectedTempUnit = u
                prefs.tempUnit = u
                onTempUnitChange(u)
                updateServiceNotificationIfRunning(context)
            }
        )
        SwitchRow(
            label = "Ongoing Notification",
            checked = ongoingNotificationEnabled,
            onCheckedChange = {
                ongoingNotificationEnabled = it
                prefs.ongoingNotificationEnabled = it
                updateServiceNotificationIfRunning(context)
            }
        )
        SettingsHint("When enabled, notification shows sensor data. When disabled, a minimal silent notification is shown.")
    }
}

