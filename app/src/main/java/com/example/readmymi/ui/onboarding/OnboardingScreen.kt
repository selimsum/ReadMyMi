package com.example.readmymi.ui.onboarding

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.readmymi.MainViewModel
import com.example.readmymi.PrefsManager
import com.example.readmymi.R
import com.example.readmymi.SensorData
import kotlinx.coroutines.delay

enum class OnboardingStage {
    WELCOME,
    PERMISSIONS,
    RADAR
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    viewModel: MainViewModel,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { PrefsManager(context) }
    var stage by remember { mutableStateOf(OnboardingStage.WELCOME) }

    val discoveredSensors by viewModel.discoveredSensors.collectAsState()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()

    var showTroubleshootingSheet by remember { mutableStateOf(false) }
    var selectedSensorForNaming by remember { mutableStateOf<SensorData?>(null) }
    var customDeviceName by remember { mutableStateOf("") }

    // 3-second auto-advance countdown for single-sensor discovery
    var autoAdvanceRemaining by remember { mutableStateOf<Int?>(null) }
    var isAutoAdvancePaused by remember { mutableStateOf(false) }

    // Radar scan duration counter for the 12-second helpful hint
    var scanSecondsElapsed by remember { mutableIntStateOf(0) }

    LaunchedEffect(stage) {
        if (stage == OnboardingStage.RADAR) {
            viewModel.startService()
            scanSecondsElapsed = 0
            while (true) {
                delay(1000)
                scanSecondsElapsed++
            }
        }
    }

    // Auto-advance logic: Starts when exactly 1 sensor is seen; cancels if 2+ appear or paused
    LaunchedEffect(discoveredSensors.size, isAutoAdvancePaused) {
        if (discoveredSensors.size == 1 && !isAutoAdvancePaused) {
            val singleSensor = discoveredSensors.first()
            autoAdvanceRemaining = 3
            while ((autoAdvanceRemaining ?: 0) > 0) {
                delay(1000)
                if (discoveredSensors.size > 1 || isAutoAdvancePaused) {
                    autoAdvanceRemaining = null
                    return@LaunchedEffect
                }
                autoAdvanceRemaining = (autoAdvanceRemaining ?: 1) - 1
            }
            // Auto-advance triggers
            if (!isAutoAdvancePaused && discoveredSensors.size == 1) {
                viewModel.selectDeviceAndCompleteOnboarding(singleSensor.macAddress, singleSensor.deviceName)
                onComplete()
            }
        } else {
            autoAdvanceRemaining = null
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = stage,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "OnboardingTransition"
            ) { currentStage ->
                when (currentStage) {
                    OnboardingStage.WELCOME -> {
                        WelcomeContent(
                            onGetStarted = { stage = OnboardingStage.PERMISSIONS },
                            onTryDemo = {
                                viewModel.enableDemoMode()
                                onComplete()
                            }
                        )
                    }

                    OnboardingStage.PERMISSIONS -> {
                        PermissionPrimerCard(
                            onAllGranted = {
                                stage = OnboardingStage.RADAR
                                viewModel.startService()
                            }
                        )
                    }

                    OnboardingStage.RADAR -> {
                        RadarDiscoveryContent(
                            discoveredSensors = discoveredSensors,
                            isScanning = isServiceRunning,
                            scanSeconds = scanSecondsElapsed,
                            autoAdvanceRemaining = autoAdvanceRemaining,
                            tempUnit = prefs.tempUnit,
                            onSelectSensor = { sensor ->
                                isAutoAdvancePaused = true
                                selectedSensorForNaming = sensor
                                customDeviceName = sensor.deviceName.ifBlank { "Living Room" }
                            },
                            onPauseOrRename = {
                                isAutoAdvancePaused = true
                                val sensor = discoveredSensors.firstOrNull()
                                if (sensor != null) {
                                    selectedSensorForNaming = sensor
                                    customDeviceName = sensor.deviceName.ifBlank { "Living Room" }
                                }
                            },
                            onOpenTroubleshooting = { showTroubleshootingSheet = true },
                            onTryDemo = {
                                viewModel.enableDemoMode()
                                onComplete()
                            },
                            onRescan = {
                                viewModel.restartService()
                                scanSecondsElapsed = 0
                            }
                        )
                    }
                }
            }
        }
    }

    // Modal Sheet for Hardware Troubleshooting & Web Flasher
    if (showTroubleshootingSheet) {
        TroubleshootingSheet(
            onDismiss = { showTroubleshootingSheet = false },
            onRescan = {
                viewModel.restartService()
                scanSecondsElapsed = 0
            },
            onTryDemo = {
                viewModel.enableDemoMode()
                onComplete()
            }
        )
    }

    // Dialog for Naming / Confirming Sensor
    selectedSensorForNaming?.let { sensor ->
        val presetRooms = listOf("Living Room", "Bedroom", "Baby Room", "Office", "Greenhouse", "Balcony")
        AlertDialog(
            onDismissRequest = { selectedSensorForNaming = null },
            title = { Text("Name Your Sensor") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Assign a room name to identify this sensor across ReadMyMi.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = customDeviceName,
                        onValueChange = { customDeviceName = it },
                        label = { Text("Room or Sensor Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Quick room chips
                    Text(
                        text = "Quick suggestions:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presetRooms.take(3).forEach { room ->
                            SuggestionChip(
                                onClick = { customDeviceName = room },
                                label = { Text(room, fontSize = 12.sp) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presetRooms.drop(3).forEach { room ->
                            SuggestionChip(
                                onClick = { customDeviceName = room },
                                label = { Text(room, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalName = customDeviceName.ifBlank { sensor.deviceName.ifBlank { "BLE Thermometer" } }
                        viewModel.selectDeviceAndCompleteOnboarding(sensor.macAddress, finalName)
                        selectedSensorForNaming = null
                        onComplete()
                    }
                ) {
                    Text("Connect & Track")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedSensorForNaming = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun WelcomeContent(
    onGetStarted: () -> Unit,
    onTryDemo: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Hero Header
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    )
                    .padding(3.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_app_logo_png),
                    contentDescription = "ReadMyMi Logo",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(18.dp))
                )
            }

            Text(
                text = "Read My Mi",
                style = TextStyle(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    ),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 32.sp
                )
            )

            Text(
                text = "Smart, Private Environmental Monitoring",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Track temperature and humidity passively from your Bluetooth thermometers with zero cloud connection and total privacy.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Value Cards
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ValuePropItem(
                icon = Icons.Default.Lock,
                title = "100% Offline & Private",
                description = "Your climate history never leaves your device. No cloud accounts, no analytics, no internet permissions."
            )
            ValuePropItem(
                icon = Icons.Default.BatterySaver,
                title = "Passive Low-Power Scanning",
                description = "Listens for broadcast advertisements without continuous connections to save both phone and sensor battery."
            )
            ValuePropItem(
                icon = Icons.Default.Widgets,
                title = "Multi-Protocol Support",
                description = "Supports standard BTHome v1/v2, ATC custom firmware (LYWSD03MMC), and Xiaomi Mijia formats."
            )
        }

        // Actions
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onGetStarted,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Get Started", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
            }

            OutlinedButton(
                onClick = onTryDemo,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Preview, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Explore with In-Memory Demo Sensor", fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun RadarDiscoveryContent(
    discoveredSensors: List<SensorData>,
    isScanning: Boolean,
    scanSeconds: Int,
    autoAdvanceRemaining: Int?,
    tempUnit: String,
    onSelectSensor: (SensorData) -> Unit,
    onPauseOrRename: () -> Unit,
    onOpenTroubleshooting: () -> Unit,
    onTryDemo: () -> Unit,
    onRescan: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Radar Animation
        SensorRadarView(isScanning = isScanning)

        Text(
            text = if (discoveredSensors.isEmpty()) "Scanning for BLE sensors..." else "Discovered Sensors (${discoveredSensors.size})",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = if (discoveredSensors.isEmpty()) "Make sure your sensor is broadcasting nearby"
                   else "Tap your sensor to connect, or pause to rename",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Discovered Devices List or Searching State
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (discoveredSensors.isNotEmpty()) {
                items(discoveredSensors, key = { it.macAddress }) { sensor ->
                    val protocol = inferProtocol(sensor.deviceName)
                    DiscoveredSensorCard(
                        sensor = sensor,
                        protocol = protocol,
                        autoAdvanceSecondsRemaining = if (discoveredSensors.size == 1) autoAdvanceRemaining else null,
                        onSelect = { onSelectSensor(sensor) },
                        onPauseOrRename = onPauseOrRename,
                        tempUnit = tempUnit
                    )
                }
            } else {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Listening for advertising packets...\n(Scan window: ${15 - (scanSeconds % 15)}s)",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // If 12+ seconds have passed with 0 sensors, show the helpful guidance card
            if (discoveredSensors.isEmpty() && scanSeconds >= 12) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Taking longer than expected?",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = "Your sensor might need the battery tab pulled, or may be running factory encrypted firmware that needs a quick flash.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onOpenTroubleshooting,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Help & Flasher", fontSize = 12.sp)
                                }
                                Button(
                                    onClick = onTryDemo,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Try Demo Mode", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom helper buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onOpenTroubleshooting) {
                Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Sensor Help")
            }

            TextButton(onClick = onRescan) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Rescan")
            }
        }
    }
}

@Composable
private fun ValuePropItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun inferProtocol(deviceName: String): String {
    val upper = deviceName.uppercase()
    return when {
        upper.contains("ATC") || upper.contains("LYWSD") -> "ATC"
        upper.contains("BTHOME") -> "BTHome"
        upper.contains("MI") || upper.contains("MIJIA") -> "Xiaomi"
        else -> "BLE"
    }
}
