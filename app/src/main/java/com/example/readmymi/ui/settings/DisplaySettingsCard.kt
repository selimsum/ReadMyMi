package com.example.readmymi.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.readmymi.PrefsManager
import com.example.readmymi.ui.DropdownSelector
import com.example.readmymi.ui.SettingsCard

/** Display card: temperature unit + ongoing-notification toggle. */
@Composable
fun DisplaySettingsCard(prefs: PrefsManager) {
    var tempUnit by remember { mutableStateOf(prefs.tempUnit) }
    var ongoingNotificationEnabled by remember { mutableStateOf(prefs.ongoingNotificationEnabled) }

    SettingsCard {
        SettingsSectionHeader("Display & Notifications", Icons.Default.Notifications)

        DropdownSelector(
            label = "Temperature Unit",
            options = listOf("C", "F"),
            selectedOption = tempUnit,
            optionToString = { u -> if (u == "F") "Fahrenheit (°F)" else "Celsius (°C)" },
            onOptionSelected = { u -> tempUnit = u; prefs.tempUnit = u }
        )
        SwitchRow(
            label = "Ongoing Notification",
            checked = ongoingNotificationEnabled,
            onCheckedChange = { ongoingNotificationEnabled = it; prefs.ongoingNotificationEnabled = it }
        )
        SettingsHint("When enabled, notification shows sensor data. When disabled, a minimal silent notification is shown.")
    }
}

