package com.example.xiaomimqtt.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanRecord
import android.os.ParcelUuid
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    targetMac: String? = null,
    onBackClick: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Raw Scanner", "Start Flashing (Config)")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug Menu (Flasher Features)") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }
            
            when (selectedTab) {
                0 -> RawScannerTab()
                1 -> ConfigTab(targetMac)
            }
        }
    }
}

@Composable
fun RawScannerTab() {
    val context = LocalContext.current
    val scanner = remember { com.example.xiaomimqtt.BluetoothSensorManager(context) }
    var isScanning by remember { mutableStateOf(false) }
    val rawResult by scanner.rawScanFlow.collectAsState()
    
    // Aggregate results
    val devices = remember { mutableStateMapOf<String, android.bluetooth.le.ScanResult>() }
    
    LaunchedEffect(rawResult) {
        rawResult?.let {
            devices[it.device.address] = it
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            scanner.stopRawScan()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(
            onClick = {
                if (isScanning) {
                    scanner.stopRawScan()
                } else {
                    scanner.startRawScan()
                    devices.clear() 
                }
                isScanning = !isScanning
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isScanning) "Stop Raw Scan" else "Start Raw Scan")
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
             Text("Count: ${devices.size}")
             if (isScanning) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            val deviceList = devices.values.toList().sortedByDescending { it.rssi }
            items(deviceList, key = { it.device.address }) { result ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(result.device.address, fontWeight = FontWeight.Bold)
                            Text("${result.rssi} dBm")
                        }
                        Text(result.device.name ?: "Unknown", style = MaterialTheme.typography.bodySmall)
                        
                        // Show Service Data if any
                        val scanRecord = result.scanRecord
                        val serviceData = scanRecord?.serviceData
                        if (!serviceData.isNullOrEmpty()) {
                            serviceData.forEach { (uuid, data) ->
                                val hex = data.joinToString("") { "%02x".format(it) }
                                Text("UUID: $uuid", style = MaterialTheme.typography.labelSmall)
                                Text("Data: $hex", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                            }
                        } else {
                             // Show Raw bytes if no service data parsed but record exists
                             val rawBytes = scanRecord?.bytes
                             if (rawBytes != null) {
                                  val hex = rawBytes.take(20).joinToString("") { "%02x".format(it) } + "..."
                                  Text("Raw: $hex", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = Color.Gray)
                             }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConfigTab(targetMac: String? = null) {
    val context = LocalContext.current
    val scanner = remember { com.example.xiaomimqtt.BluetoothSensorManager(context) }
    val scope = rememberCoroutineScope()
    
    var macInput by remember { mutableStateOf(targetMac ?: "") }
    var statusLog by remember { mutableStateOf("Ready to connect.") }
    var configData by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isConnecting by remember { mutableStateOf(false) }

    // Auto-connect if targetMac provided
    LaunchedEffect(targetMac) {
        if (!targetMac.isNullOrEmpty() && macInput == targetMac) {
             isConnecting = true
             statusLog = "Auto-Connecting to $targetMac..."
             configData = emptyMap()
             scope.launch {
                val result = scanner.connectAndReadConfig(targetMac) { log ->
                     statusLog = log
                }
                configData = result
                statusLog = if (result.isNotEmpty()) "Read Complete (${result.size} values)" else "Read Failed / No readable characteristics"
                isConnecting = false
             }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = macInput,
            onValueChange = { macInput = it },
            label = { Text("Device MAC Address (e.g. A4:C1:38:...)") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Button(
            onClick = {
                if (macInput.length >= 17) { // Simple validation
                    isConnecting = true
                    statusLog = "Connecting..."
                    configData = emptyMap()
                    
                    scope.launch {
                        val result = scanner.connectAndReadConfig(macInput) { log ->
                             statusLog = log
                        }
                        configData = result
                        statusLog = if (result.isNotEmpty()) "Read Complete (${result.size} values)" else "Read Failed / No readable characteristics"
                        isConnecting = false
                    }
                } else {
                    statusLog = "Invalid MAC Address"
                }
            },
            enabled = !isConnecting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isConnecting) "Connecting..." else "Connect & Read Config")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Status: $statusLog", style = MaterialTheme.typography.bodyMedium, color = if (statusLog.contains("Fail") || statusLog.contains("Error")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (configData.isNotEmpty()) {
            Text("Configuration Data:", style = MaterialTheme.typography.titleMedium)
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    configData.forEach { (key, value) ->
                        Text("$key:", fontWeight = FontWeight.Bold)
                        Text(value, fontFamily = FontFamily.Monospace)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                    
                    Text("Note: Parsing logic for specific fields (Advertising Interval, etc.) is pending.", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
        }
    }
}
