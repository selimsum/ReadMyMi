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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BluetoothSensorManager(private val context: Context) {

    companion object {
        val gattMutex = Mutex()
        @Volatile
        var isGattBusy: Boolean = false

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
            val details = serviceData.entries.joinToString(" | ") { (k, v) -> "$k:${v.joinToString("") { "%02x".format(it) }}" }
            AppLogger.log("BLE", "SCAN ${device.name ?: "(unnamed)"} ${device.address} rssi=${result.rssi} data=[$details]")
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

        // LOW_LATENCY ensures 100% duty cycle during the brief 15s scan window
        // so that alternating advertising packets are not missed.
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
        gattMutex.withLock {
            isGattBusy = true
            try {
                val historyList = mutableListOf<SensorData>()
                val device = bluetoothAdapter?.getRemoteDevice(deviceAddress) ?: return@withLock emptyList()
                
                var gatt: BluetoothGatt? = null
                val completion = kotlinx.coroutines.CompletableDeferred<Boolean>()
                val lastPacketTime = java.util.concurrent.atomic.AtomicLong(0L)
                val packetsReceived = java.util.concurrent.atomic.AtomicInteger(0)
                
                val gattCallback = object : BluetoothGattCallback() {
                    private val writeStep = java.util.concurrent.atomic.AtomicInteger(0)

                    override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                            g.discoverServices()
                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            if (!completion.isCompleted) {
                                completion.complete(historyList.isNotEmpty())
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
                                if (desc != null) {
                                    desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    g.writeDescriptor(desc)
                                } else {
                                    AppLogger.log("BluetoothSensorManager", "Notification descriptor not found")
                                    g.disconnect()
                                }
                            } else {
                                g.disconnect()
                            }
                        } else {
                            g.disconnect()
                        }
                    }
                     
                    override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor?, status: Int) {
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            AppLogger.log("BluetoothSensorManager", "Descriptor write failed ($status)")
                            completion.complete(false)
                            g.disconnect()
                            return
                        }
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
                        lastPacketTime.set(System.currentTimeMillis())
                        packetsReceived.incrementAndGet()

                        if (value.size >= 3 && value[0] == 0x35.toByte() && value[1] == 0.toByte() && value[2] == 0.toByte()) {
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

                            val parsedTemp = Math.round(temp * 100) / 100.0
                            val parsedHum = Math.round(hum * 100) / 100.0
                            val isDummyRecord = (Math.abs(parsedTemp - 21.12) < 0.05 && Math.abs(parsedHum - 43.0) < 0.5)

                            if (temp in -40.0..80.0 && hum in 0.0..100.0 && !(temp == 0.0 && hum == 0.0) && !isDummyRecord) {
                                historyList.add(SensorData(
                                    macAddress = g.device.address,
                                    deviceName = g.device.name ?: "",
                                    temperature = parsedTemp,
                                    humidity = parsedHum,
                                    battery = batPct,
                                    timestamp = time
                                ))
                            }
                        }
                    }
                }

                stopScanning()
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                     gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                         device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                     } else {
                         device.connectGatt(context, false, gattCallback)
                     }
                } else return@withLock emptyList()
                
                val maxWaitMs = if (records > 1000) 300_000L else 60_000L
                val startTime = System.currentTimeMillis()

                try {
                    while (!completion.isCompleted) {
                        kotlinx.coroutines.delay(500)
                        val now = System.currentTimeMillis()
                        if (now - startTime > maxWaitMs) {
                            AppLogger.log("BluetoothSensorManager", "Download max timeout reached ($maxWaitMs ms)")
                            break
                        }
                        if (packetsReceived.get() > 0 && (now - lastPacketTime.get()) > 8000L) {
                            AppLogger.log("BluetoothSensorManager", "Stream idle for 8s, completing download with ${historyList.size} records")
                            break
                        }
                    }
                } finally {
                     gatt?.disconnect()
                     gatt?.close()
                }

                if (historyList.isEmpty()) {
                    throw Exception("Bluetooth connection lost or timed out before history data was received")
                }
                
                return@withLock fixTimestamps(historyList, lastDbTimestamp)
            } finally {
                isGattBusy = false
            }
        }
    }

    private fun fixTimestamps(history: List<SensorData>, lastDbTimestamp: Long? = null): List<SensorData> {
        if (history.isEmpty()) return history
        val now = System.currentTimeMillis()
        val sorted = history.sortedBy { it.timestamp }
        val validEpochRecords = sorted.filter { it.timestamp in 1577836800000L..(now + 300000L) }.map {
            if (it.timestamp > now) it.copy(timestamp = now) else it
        }
        val wrongRecords = sorted.filter { it.timestamp < 1577836800000L }

        // If the sensor has valid real-time epoch records (from after time was synced),
        // keep ONLY the valid epoch records. Do not shift old pre-sync records onto today.
        if (validEpochRecords.isNotEmpty()) {
            return validEpochRecords
        }

        if (wrongRecords.isEmpty()) return emptyList()

        val sensorEnd = wrongRecords.last().timestamp
        val sensorStart = wrongRecords.first().timestamp
        val sensorSpan = sensorEnd - sensorStart

        val windowEnd = now - 60000L
        val windowStart = windowEnd - sensorSpan
        val correction = windowStart - sensorStart

        return wrongRecords.map {
            it.copy(timestamp = it.timestamp + correction)
        }.filter {
            it.timestamp <= now
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
        gattMutex.withLock {
            isGattBusy = true
            try {
                val device = bluetoothAdapter?.getRemoteDevice(deviceAddress) ?: return@withLock false
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

                stopScanning()
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return@withLock false
                
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
            } finally {
                isGattBusy = false
            }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun connectAndReadConfig(deviceAddress: String, onLog: (String) -> Unit): Map<String, String> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        gattMutex.withLock {
            isGattBusy = true
            try {
                val configMap = mutableMapOf<String, String>()
                val device = bluetoothAdapter?.getRemoteDevice(deviceAddress) ?: return@withLock configMap
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

                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return@withLock configMap

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

                return@withLock configMap
            } finally {
                isGattBusy = false
            }
        }
    }
}
