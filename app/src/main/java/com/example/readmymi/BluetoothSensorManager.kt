package com.example.readmymi

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import android.bluetooth.le.ScanFilter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import android.Manifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException

class BluetoothSensorManager(private val context: Context) {

    companion object {
        private const val SERVICE_UUID = "00001f10-0000-1000-8000-00805f9b34fb"
        private const val CHAR_UUID = "00001f1f-0000-1000-8000-00805f9b34fb"
        private const val DESC_UUID = "00002902-0000-1000-8000-00805f9b34fb"
        private const val XIAOMI_SERVICE_PREFIX = "0000fe95-0000-1000-8000-00805f9b34fb"
        private const val BTHOME_SERVICE_PREFIX = "0000fcd2-0000-1000-8000-00805f9b34fb"
        private const val ENV_SENSING_PREFIX = "0000181a-0000-1000-8000-00805f9b34fb"
        private val uuidFilters: List<ScanFilter> by lazy {
            listOf("fe95", "fcd2", "181a").flatMap { uuid ->
                val parcelUuid = ParcelUuid.fromString("0000$uuid-0000-1000-8000-00805f9b34fb")
                listOf(
                    ScanFilter.Builder().setServiceUuid(parcelUuid).build(),
                    ScanFilter.Builder().setServiceData(parcelUuid, byteArrayOf()).build()
                )
            }
        }
    }

    private val bluetoothManager: BluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val scanner: BluetoothLeScanner?
        get() = bluetoothAdapter?.bluetoothLeScanner

    private val _sensorDataFlow = MutableStateFlow<SensorData?>(null)
    val sensorDataFlow: StateFlow<SensorData?> = _sensorDataFlow.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()


    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val scanRecord = result.scanRecord ?: return
            
            val serviceData = scanRecord.serviceData.mapKeys { it.key.uuid.toString() }
            val data = SensorParser.parse(device.name ?: "", device.address, serviceData)
            
            if (data != null) {
                _sensorDataFlow.value = data
                AppLogger.log("BluetoothSensorManager", "Parsed: $data")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            AppLogger.log("BLE", "Scan Failed: $errorCode")
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun startScanning(targetMac: String? = null) {
        val scanner = this.scanner ?: run {
            AppLogger.log("BLE", "Scanner not available")
            return
        }
        
        if (_isScanning.value) return

        val filters = mutableListOf<ScanFilter>()
        if (!targetMac.isNullOrEmpty() && try { BluetoothAdapter.checkBluetoothAddress(targetMac) } catch (e: IllegalArgumentException) { false }) {
            filters.add(ScanFilter.Builder().setDeviceAddress(targetMac).build())
        } else {
            filters.addAll(uuidFilters)
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        
        try {
            scanner.startScan(filters, settings, scanCallback)
            _isScanning.value = true
            AppLogger.log("BLE", "Scanner STARTED")
        } catch (e: Exception) {
            AppLogger.log("BLE", "Start Scan Failed: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        try {
            scanner?.stopScan(scanCallback)
            AppLogger.log("BLE", "Scanner STOPPED")
        } catch (e: Exception) {
            AppLogger.log("BLE", "Stop Scan Error: ${e.message}")
        }
        _isScanning.value = false
    }


    @SuppressLint("MissingPermission")
    suspend fun downloadHistory(deviceAddress: String, records: Int = 70, lastDbTimestamp: Long? = null, onLog: (String) -> Unit = {}): List<SensorData> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val historyList = mutableListOf<SensorData>()
        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress) ?: return@withContext emptyList()
        
        var gatt: BluetoothGatt? = null
        val completion = kotlinx.coroutines.CompletableDeferred<Boolean>()
        
        val gattCallback = object : BluetoothGattCallback() {
            private val writeStep = java.util.concurrent.atomic.AtomicInteger(0)

            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    g.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (!completion.isCompleted) {
                        completion.complete(false)
                    }
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = g.getService(UUID.fromString(SERVICE_UUID))
                    val char = service?.getCharacteristic(UUID.fromString(CHAR_UUID))
                    if (char != null) {
                        g.setCharacteristicNotification(char, true)
                        val desc = char.getDescriptor(UUID.fromString(DESC_UUID))
                        desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        g.writeDescriptor(desc)
                    } else {
                        g.disconnect()
                    }
                } else {
                    g.disconnect()
                }
            }
             
            override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor?, status: Int) {
                val char = g.getService(UUID.fromString(SERVICE_UUID))
                    ?.getCharacteristic(UUID.fromString(CHAR_UUID)) ?: return
                
                val currentTime = (System.currentTimeMillis() / 1000).toInt()
                val timeCmd = ByteArray(5).apply {
                    this[0] = 0x23.toByte()
                    System.arraycopy(java.nio.ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(currentTime).array(), 0, this, 1, 4)
                }
                writeStep.set(1)
                writeCharacteristic(g, char, timeCmd)
             }

            override fun onCharacteristicWrite(g: BluetoothGatt, char: BluetoothGattCharacteristic?, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS && writeStep.get() == 1) {
                     val cmd = ByteArray(5).apply {
                        this[0] = 0x35.toByte()
                        this[1] = (records and 0xFF).toByte()
                        this[2] = ((records shr 8) and 0xFF).toByte()
                     }
                     writeStep.set(2)
                     writeCharacteristic(g, char!!, cmd)
                }
            }

            override fun onCharacteristicChanged(g: BluetoothGatt, char: BluetoothGattCharacteristic, value: ByteArray) {
                if (value.size == 3 && value[0] == 0x35.toByte() && value[1] == 0.toByte() && value[2] == 0.toByte()) {
                    completion.complete(true)
                    g.disconnect()
                    return
                }

                if (value.size >= 13 && value[0] == 0x35.toByte()) {
                    val buffer = java.nio.ByteBuffer.wrap(value).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    val time = (buffer.getInt(3).toLong() and 0xFFFFFFFFL) * 1000L
                    val temp = buffer.getShort(7) / 100.0
                    val hum = buffer.getShort(9) / 100.0
                    val vbat = buffer.getShort(11)
                    val batPct = SensorParser.calculateBatteryPercentage(vbat.toInt())

                    historyList.add(SensorData(
                        macAddress = g.device.address,
                        deviceName = g.device.name ?: "",
                        temperature = Math.round(temp * 100) / 100.0,
                        humidity = Math.round(hum * 100) / 100.0,
                        battery = batPct,
                        timestamp = time
                    ))
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
             gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                 device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
             } else {
                 device.connectGatt(context, false, gattCallback)
             }
        } else return@withContext emptyList()
        
        val success = try {
            kotlinx.coroutines.withTimeout(60000) { completion.await() }
        } catch (e: TimeoutCancellationException) {
            AppLogger.log("BluetoothSensorManager", "Download timeout: ${e.message}")
            false
        } finally {
             gatt?.disconnect()
             gatt?.close()
        }

        if (!success) {
            throw Exception("Bluetooth connection lost or timed out during history download")
        }
        
        return@withContext fixTimestamps(historyList, lastDbTimestamp)
    }

    private fun fixTimestamps(history: List<SensorData>, lastDbTimestamp: Long? = null): List<SensorData> {
        if (history.isEmpty()) return history
        val now = System.currentTimeMillis()
        val sorted = history.sortedBy { it.timestamp }
        val wrongRecords = sorted.filter { it.timestamp < 1577836800000L }
        if (wrongRecords.isEmpty()) return history

        val sensorEnd = wrongRecords.last().timestamp
        val sensorStart = wrongRecords.first().timestamp
        val sensorSpan = sensorEnd - sensorStart

        // Place the history window so it ends at 'now' and spans the sensor's duration.
        val windowEnd = now
        val windowStart = windowEnd - sensorSpan
        val correction = windowStart - sensorStart

        return sorted.map {
            if (it.timestamp < 1577836800000L) it.copy(timestamp = it.timestamp + correction)
            else it
        }.filter {
            // Keep only records in the past/present and strictly after lastDbTimestamp
            it.timestamp <= now && (lastDbTimestamp == null || it.timestamp > lastDbTimestamp)
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeCharacteristic(g: BluetoothGatt, char: BluetoothGattCharacteristic, value: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(char, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            char.value = value
            char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            g.writeCharacteristic(char)
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun syncTime(deviceAddress: String, onLog: (String) -> Unit = {}): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress) ?: return@withContext false
        val completion = kotlinx.coroutines.CompletableDeferred<Boolean>()
        
        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) g.discoverServices()
                else if (newState == BluetoothProfile.STATE_DISCONNECTED && !completion.isCompleted) completion.complete(false)
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = g.getService(UUID.fromString(SERVICE_UUID))
                    val char = service?.getCharacteristic(UUID.fromString(CHAR_UUID))
                    if (char != null) {
                        val currentTime = (System.currentTimeMillis() / 1000).toInt()
                        val timeCmd = ByteArray(5).apply {
                            this[0] = 0x23.toByte()
                            System.arraycopy(java.nio.ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(currentTime).array(), 0, this, 1, 4)
                        }
                        writeCharacteristic(g, char, timeCmd)
                    } else completion.complete(false)
                } else completion.complete(false)
            }
            
            override fun onCharacteristicWrite(g: BluetoothGatt, char: BluetoothGattCharacteristic?, status: Int) {
                 completion.complete(status == BluetoothGatt.GATT_SUCCESS)
                 g.disconnect()
            }
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return@withContext false
        
        val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else device.connectGatt(context, false, gattCallback)
        
        try {
            kotlinx.coroutines.withTimeout(15000) { completion.await() }
        } catch (e: TimeoutCancellationException) {
            false
        } finally {
            gatt.disconnect()
            gatt.close()
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun connectAndReadConfig(deviceAddress: String, onLog: (String) -> Unit): Map<String, String> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val configMap = mutableMapOf<String, String>()
        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress) ?: return@withContext configMap
        val completion = kotlinx.coroutines.CompletableDeferred<Boolean>()
        val readQueue = ArrayDeque<Pair<String, BluetoothGattCharacteristic>>() // UUID → char

        val gattCallback = object : BluetoothGattCallback() {
            private var reading = false

            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    onLog("Connected, discovering services...")
                    g.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    onLog("Disconnected.")
                    if (!completion.isCompleted) completion.complete(true)
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    onLog("Services discovered: ${g.services.size}")
                    g.services.forEach { service ->
                        service.characteristics.forEach { char ->
                            val props = char.properties
                            if (props and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
                                readQueue.add(service.uuid.toString().take(8) + "/" + char.uuid.toString().take(8) to char)
                            }
                        }
                    }
                    onLog("Readable characteristics: ${readQueue.size}")
                    readNext(g)
                } else {
                    onLog("Service discovery failed ($status).")
                    g.disconnect()
                }
            }

            override fun onCharacteristicRead(g: BluetoothGatt, char: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
                reading = false
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val hex = value.joinToString("") { "%02x".format(it) }
                    val key = char.uuid.toString().take(8)
                    configMap[key] = hex
                    onLog("Read $key → $hex")
                }
                readNext(g)
            }

            // API < 33 fallback
            @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
            override fun onCharacteristicRead(g: BluetoothGatt, char: BluetoothGattCharacteristic, status: Int) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    reading = false
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        val hex = (char.value ?: byteArrayOf()).joinToString("") { "%02x".format(it) }
                        val key = char.uuid.toString().take(8)
                        configMap[key] = hex
                        onLog("Read $key → $hex")
                    }
                    readNext(g)
                }
            }

            private fun readNext(g: BluetoothGatt) {
                if (reading) return
                if (readQueue.isEmpty()) {
                    onLog("All characteristics read.")
                    g.disconnect()
                    return
                }
                val (_, char) = readQueue.removeFirst()
                reading = true
                if (!g.readCharacteristic(char)) {
                    reading = false
                    readNext(g)
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return@withContext configMap

        val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else device.connectGatt(context, false, gattCallback)

        try {
            kotlinx.coroutines.withTimeout(30000) { completion.await() }
        } catch (e: TimeoutCancellationException) {
            onLog("Timeout — ${e.message}")
        } finally {
            gatt?.disconnect()
            gatt?.close()
        }

        return@withContext configMap
    }
}
