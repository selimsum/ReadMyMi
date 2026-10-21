package com.example.readmymi.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.example.readmymi.PrefsManager
import com.example.readmymi.ui.DropdownSelector
import com.example.readmymi.ui.SettingsCard

/** Scanning card: interval, offline-timeout override, history-sync mode. */
@Composable
fun ScanSettingsCard(prefs: PrefsManager) {
    var scanInterval by remember { mutableStateOf(prefs.scanIntervalSeconds.toString()) }
    var offlineTimeout by remember { mutableStateOf(prefs.offlineTimeoutMinutes.toString()) }
    var offlineTimeoutOverride by remember { mutableStateOf(prefs.offlineTimeoutOverride) }
    var autoHistorySyncMode by remember { mutableStateOf(prefs.autoHistorySyncMode) }

    SettingsCard {
        SettingsSectionHeader("Scanning & History Sync", Icons.Default.Sync)

        DropdownSelector(
            label = "Scan Interval",
            options = listOf(15, 60, 300, 900),
            selectedOption = scanInterval.toIntOrNull() ?: 300,
            optionToString = { s ->
                when (s) {
                    15 -> "15 seconds"; 60 -> "1 minute"; 300 -> "5 minutes"
                    900 -> "15 minutes"; else -> "$s seconds"
                }
            },
            onOptionSelected = { s -> scanInterval = s.toString(); prefs.scanIntervalSeconds = s }
        )
        SwitchRow(
            label = "Override Offline Detection",
            checked = offlineTimeoutOverride,
            onCheckedChange = { offlineTimeoutOverride = it; prefs.offlineTimeoutOverride = it }
        )
        if (offlineTimeoutOverride) {
            OutlinedTextField(
                value = offlineTimeout,
                onValueChange = {
                    offlineTimeout = it
                    it.toIntOrNull()?.let { m -> prefs.offlineTimeoutMinutes = m }
                },
                label = { Text("Offline Timeout (minutes)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        SettingsHint("Effective offline timeout: ${prefs.effectiveOfflineTimeoutMinutes} minutes")

        DropdownSelector(
            label = "Automatic History Download",
            options = listOf("off", "always", "start"),
            selectedOption = autoHistorySyncMode,
            optionToString = { m ->
                when (m) { "off" -> "Off"; "always" -> "Always"; "start" -> "On service start"; else -> m }
            },
            onOptionSelected = { m -> autoHistorySyncMode = m; prefs.autoHistorySyncMode = m }
        )
        SettingsHint(
            "• Off: Never automatically download history\n" +
            "• Always: Check on every reading\n" +
            "• On service start: Check once per service run"
        )
    }
}

