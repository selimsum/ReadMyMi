package com.example.xiaomimqtt

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import java.util.Locale
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.rounded.SentimentSatisfied
import androidx.compose.material.icons.rounded.SentimentDissatisfied
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import kotlinx.coroutines.launch
import com.example.xiaomimqtt.ui.theme.XiaomiMqttAppTheme
import com.example.xiaomimqtt.ui.HistoryScreen
import com.example.xiaomimqtt.ui.SensorChart
import com.example.xiaomimqtt.data.SensorDatabase
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.bluetooth.BluetoothAdapter
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import com.example.xiaomimqtt.ui.DebugScreen
import com.example.xiaomimqtt.ui.AboutScreen
import com.example.xiaomimqtt.ui.SettingsScreen
import com.example.xiaomimqtt.ui.DashboardScreen
import com.example.xiaomimqtt.data.SensorEntity

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            XiaomiMqttAppTheme {
                MainScreen(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val prefs = remember { PrefsManager(context) }
    val database = remember { SensorDatabase.getDatabase(context) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    val lastMac by viewModel.lastMac.collectAsState()
    val isRunning by viewModel.isServiceRunning.collectAsState()
    val sensorData by viewModel.sensorData.collectAsState()
    
    var showSettings by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showDebug by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }

    val permissions = remember {
        mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val essentialPermissions = permissions.filter { it != Manifest.permission.POST_NOTIFICATIONS }
        val essentialGranted = essentialPermissions.all { result[it] == true }
        if (essentialGranted) {
            viewModel.startService()
        } else {
            Toast.makeText(context, "Essential Bluetooth and Location permissions are required to scan.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            Toast.makeText(context, "Bluetooth is not supported on this device.", Toast.LENGTH_LONG).show()
        } else if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(context, "Please enable Bluetooth to scan for sensors.", Toast.LENGTH_LONG).show()
        }

        val essentialPermissions = permissions.filter { it != Manifest.permission.POST_NOTIFICATIONS }
        val essentialGranted = essentialPermissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (essentialGranted) {
            viewModel.startService()
        } else {
            launcher.launch(permissions.toTypedArray())
        }
    }

    if (showDebug) {
        DebugScreen(targetMac = lastMac.takeIf { BluetoothAdapter.checkBluetoothAddress(it) }, onBackClick = { showDebug = false })
        return
    }

    if (showAbout) {
        AboutScreen(onBack = { showAbout = false })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(id = R.drawable.ic_app_logo_png),
                            contentDescription = "Logo",
                            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Read My Mi") 
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = !showSettings }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            if (showSettings) {
                SettingsScreen(
                    prefs = prefs,
                    lastMac = lastMac,
                    isServiceRunning = isRunning,
                    onStartService = { viewModel.startService(); showSettings = false },
                    onStopService = { viewModel.stopService() },
                    onShowAbout = { showAbout = true },
                    onShowDebug = { showDebug = true },
                    onClearLastMac = {
                        viewModel.updateLastMac("")
                        viewModel.restartService()
                    },
                    onBack = { showSettings = false }
                )
            } else {
                DashboardScreen(
                    sensorData = sensorData,
                    prefs = prefs,
                    database = database,
                    isServiceRunning = isRunning,
                    isDownloading = isDownloading,
                    onRefresh = { viewModel.restartService() },
                    onDownloadHistory = { records: Int ->
                        isDownloading = true
                        lifecycleOwner.lifecycleScope.launch {
                            val manager = BluetoothSensorManager(context)
                            val history = manager.downloadHistory(sensorData?.macAddress ?: "", records)
                            if (history.isNotEmpty()) {
                                database.sensorDao().insertAll(history.map { 
                                    SensorEntity(
                                        id = 0,
                                        macAddress = it.macAddress,
                                        temperature = it.temperature.toFloat(),
                                        humidity = it.humidity.toInt(),
                                        battery = it.battery,
                                        timestamp = it.timestamp
                                    )
                                })
                            }
                            isDownloading = false
                        }
                    }
                )
            }
        }
    }
}
