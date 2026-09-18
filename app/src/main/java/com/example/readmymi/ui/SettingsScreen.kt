package com.example.readmymi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.readmymi.PrefsManager
import com.example.readmymi.SensorForegroundService
import com.example.readmymi.TemperatureConverter
import com.example.readmymi.ui.settings.AlertSettingsCard
import com.example.readmymi.ui.settings.DataSettingsCard
import com.example.readmymi.ui.settings.DeviceCard
import com.example.readmymi.ui.settings.DiagnosticsCard
import com.example.readmymi.ui.settings.DisplaySettingsCard
import com.example.readmymi.ui.settings.ScanSettingsCard
import com.example.readmymi.ui.settings.ServiceCard



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefs: PrefsManager,
    lastMac: String,
    isServiceRunning: Boolean,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onShowAbout: () -> Unit,
    onClearLastMac: () -> Unit,
    onBack: () -> Unit
) {
    val liveStatus by SensorForegroundService.serviceStatus.collectAsState()
    val tempUnit = prefs.tempUnit

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = TemperatureConverter.format(22.0, tempUnit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    liveStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item { AlertSettingsCard(prefs) }
            item { ScanSettingsCard(prefs) }
            item { DisplaySettingsCard(prefs) }
            item { DataSettingsCard(prefs) }
            item { DiagnosticsCard(prefs, lastMac) }
            item {
                DeviceCard(
                    prefs = prefs,
                    lastMac = lastMac,
                    onClearLastMac = onClearLastMac
                )
            }
            item {
                ServiceCard(
                    isServiceRunning = isServiceRunning,
                    onStartService = onStartService,
                    onStopService = onStopService,
                    onShowAbout = onShowAbout
                )
            }
        }
    }
}
