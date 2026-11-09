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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
        /** Caller-visible ceiling for a full GATT history download (cancellable). */
        const val DOWNLOAD_TIMEOUT_MS = 5 * 60 * 1000L
    }

    private val bluetoothManager: BluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val scanner: BluetoothLeScanner?
        get() = bluetoothAdapter?.bluetoothLeScanner

    private val _sensorDataFlow = MutableSharedFlow<SensorData>(extraBufferCapacity = 64)
    val sensorDataFlow: SharedFlow<SensorData> = _sensorDataFlow.asSharedFlow()

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
                // Buffer (drop oldest) instead of conflating: every parsed packet is delivered.
                _sensorDataFlow.tryEmit(data)
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

        // Always keep UUID filters so other/unknown sensors remain visible.
        // An optional device-address filter narrows the hardware scan when a known
        // MAC is selected; filtering for the selected device still happens in
        // onScanResult-equivalent paths via handleSensorData.
        val filters = mutableListOf<ScanFilter>()
        val hasTargetMac = !targetMac.isNullOrEmpty() &&
            try { BluetoothAdapter.checkBluetoothAddress(targetMac) } catch (e: IllegalArgumentException) { false }
        filters.addAll(uuidFilters)
        if (hasTargetMac) {
            filters.add(ScanFilter.Builder().setDeviceAddress(requireNotNull(targetMac)).build())
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
    suspend fun downloadHistory(deviceAddress: String, records: Int = 70, lastDbTimestamp: Long? = null, onLog: (String) -> Unit = {}): List<SensorData> =
        kotlinx.coroutines.withTimeout(DOWNLOAD_TIMEOUT_MS) {
            downloadHistoryInternal(deviceAddress, records, lastDbTimestamp, onLog)
        }

    @SuppressLint("MissingPermission")
    private suspend fun downloadHistoryInternal(deviceAddress: String, records: Int = 70, lastDbTimestamp: Long? = null, onLog: (String) -> Unit = {}): List<SensorData> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        fun log(msg: String) {
            AppLogger.log("BluetoothSensorManager", msg)
            onLog(msg)
        }

        gattMutex.withLock {
            isGattBusy = true
            try {
                val historyList = mutableListOf<SensorData>()
                val device = bluetoothAdapter?.getRemoteDevice(deviceAddress) ?: return@withLock emptyList()

                var gatt: BluetoothGatt? = null
                val completion = kotlinx.coroutines.CompletableDeferred<Boolean>()
                val lastPacketTime = java.util.concurrent.atomic.AtomicLong(0L)
                val packetsReceived = java.util.concurrent.atomic.AtomicInteger(0)

                fun handleIncomingPacket(g: BluetoothGatt, value: ByteArray) {
                    lastPacketTime.set(System.currentTimeMillis())
                    val count = packetsReceived.incrementAndGet()
                    if (count == 1 || count % 50 == 0) {
                        log("History stream packet #$count received (${value.size} bytes)")
                    }

                    if (value.size >= 3 && value[0] == 0x35.toByte() && value[1] == 0.toByte() && value[2] == 0.toByte()) {
                        log("End-of-history marker received. Total records: ${historyList.size}")
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

                val gattCallback = object : BluetoothGattCallback() {
                    private val writeStep = java.util.concurrent.atomic.AtomicInteger(0)

                    override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                        log("Connection state change: status=$status, newState=$newState")
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            log("Connection state error status=$status, newState=$newState")
                            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                                if (!completion.isCompleted) {
                                    completion.complete(historyList.isNotEmpty())
                                }
                                return
                            }
                        }
                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            log("Connected, requesting high priority and discovering services...")
                            g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                            g.discoverServices()
                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            log("Disconnected. Total records collected: ${historyList.size}")
                            if (!completion.isCompleted) {
                                completion.complete(historyList.isNotEmpty())
                            }
                        }
                    }

                    override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                        log("Services discovered: status=$status, count=${g.services.size}")
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            log("Service discovery failed with status $status")
                            completion.complete(false)
                            g.disconnect()
                            return
                        }

                        val service = g.getService(UUID.fromString(SERVICE_UUID))
                            ?: g.services.firstOrNull { it.uuid.toString().contains("1f10", ignoreCase = true) }

                        if (service == null) {
                            val available = g.services.map { it.uuid.toString().take(8) }.joinToString(",")
                            log("History service 0x1F10 not found. Discovered services: [$available]")
                            completion.complete(false)
                            g.disconnect()
                            return
                        }

                        val char = service.getCharacteristic(UUID.fromString(CHAR_UUID))
                            ?: service.characteristics.firstOrNull { it.uuid.toString().contains("1f1f", ignoreCase = true) }

                        if (char == null) {
                            log("History characteristic 0x1F1F not found in service ${service.uuid}")
                            completion.complete(false)
                            g.disconnect()
                            return
                        }

                        val notifyOk = g.setCharacteristicNotification(char, true)
                        log("setCharacteristicNotification: $notifyOk")

                        val desc = char.getDescriptor(UUID.fromString(DESC_UUID))
                            ?: char.descriptors.firstOrNull { it.uuid.toString().contains("2902", ignoreCase = true) }

                        if (desc == null) {
                            log("Notification descriptor 0x2902 not found")
                            completion.complete(false)
                            g.disconnect()
                            return
                        }

                        val descOk = writeDescriptor(g, desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        log("writeDescriptor initiated: $descOk")
                        if (!descOk) {
                            log("Failed to initiate descriptor write, aborting")
                            completion.complete(false)
                            g.disconnect()
                        }
                    }

                    override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor?, status: Int) {
                        log("onDescriptorWrite: status=$status")
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            log("Descriptor write failed ($status)")
                            completion.complete(false)
                            g.disconnect()
                            return
                        }
                        val service = g.getService(UUID.fromString(SERVICE_UUID))
                            ?: g.services.firstOrNull { it.uuid.toString().contains("1f10", ignoreCase = true) }
                        val char = service?.getCharacteristic(UUID.fromString(CHAR_UUID))
                            ?: service?.characteristics?.firstOrNull { it.uuid.toString().contains("1f1f", ignoreCase = true) }

                        if (char == null) {
                            log("Characteristic not found on descriptor write")
                            completion.complete(false)
                            g.disconnect()
                            return
                        }

                        val currentTime = (System.currentTimeMillis() / 1000).toInt()
                        val timeCmd = ByteArray(5).apply {
                            this[0] = 0x23.toByte()
                            System.arraycopy(java.nio.ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(currentTime).array(), 0, this, 1, 4)
                        }
                        writeStep.set(1)
                        val timeOk = writeCharacteristic(g, char, timeCmd)
                        log("Wrote timeCmd (0x23), initiated=$timeOk")
                        if (!timeOk) {
                            writeStep.set(2)
                            val cmd = byteArrayOf(
                                0x35.toByte(),
                                (records and 0xFF).toByte(),
                                ((records shr 8) and 0xFF).toByte()
                            )
                            val histOk = writeCharacteristic(g, char, cmd)
                            log("Fallback: wrote historyCmd (0x35, $records records), initiated=$histOk")
                            if (!histOk) {
                                completion.complete(false)
                                g.disconnect()
                            }
                        }
                    }

                    override fun onCharacteristicWrite(g: BluetoothGatt, char: BluetoothGattCharacteristic?, status: Int) {
                        log("onCharacteristicWrite: status=$status, step=${writeStep.get()}")
                        if (char == null) return
                        if (writeStep.get() == 1) {
                            writeStep.set(2)
                            val cmd = byteArrayOf(
                                0x35.toByte(),
                                (records and 0xFF).toByte(),
                                ((records shr 8) and 0xFF).toByte()
                            )
                            val histOk = writeCharacteristic(g, char, cmd)
                            log("Wrote historyCmd (0x35, $records records), initiated=$histOk")
                            if (!histOk) {
                                completion.complete(false)
                                g.disconnect()
                            }
                        }
                    }

                    override fun onCharacteristicChanged(g: BluetoothGatt, char: BluetoothGattCharacteristic, value: ByteArray) {
                        handleIncomingPacket(g, value)
                    }

                    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
                    override fun onCharacteristicChanged(g: BluetoothGatt, char: BluetoothGattCharacteristic) {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            val value = char.value ?: byteArrayOf()
                            handleIncomingPacket(g, value)
                        }
                    }
                }

                stopScanning()
                kotlinx.coroutines.delay(300)

                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    log("Connecting GATT to $deviceAddress...")
                    gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                    } else {
                        device.connectGatt(context, false, gattCallback)
                    }
                } else {
                    log("BLUETOOTH_CONNECT permission not granted")
                    return@withLock emptyList()
                }

                val maxWaitMs = if (records > 1000) 300_000L else 60_000L
                val startTime = System.currentTimeMillis()

                try {
                    while (!completion.isCompleted) {
                        kotlinx.coroutines.delay(500)
                        val now = System.currentTimeMillis()
                        if (now - startTime > maxWaitMs) {
                            log("Download max timeout reached ($maxWaitMs ms)")
                            break
                        }
                        if (packetsReceived.get() == 0 && (now - startTime) > 15_000L) {
                            log("Connection or stream start timeout (15s), aborting download")
                            break
                        }
                        if (packetsReceived.get() > 0 && (now - lastPacketTime.get()) > 8000L) {
                            log("Stream idle for 8s, completing download with ${historyList.size} records")
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
    private fun writeCharacteristic(g: BluetoothGatt, char: BluetoothGattCharacteristic, value: ByteArray): Boolean {
        val writeType = if (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val res = g.writeCharacteristic(char, value, writeType)
                res == android.bluetooth.BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                char.value = value
                char.writeType = writeType
                @Suppress("DEPRECATION")
                g.writeCharacteristic(char)
            }
        } catch (e: Exception) {
            AppLogger.log("BluetoothSensorManager", "writeCharacteristic exception: ${e.message}")
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeDescriptor(g: BluetoothGatt, desc: BluetoothGattDescriptor, value: ByteArray): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val res = g.writeDescriptor(desc, value)
                res == android.bluetooth.BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                desc.value = value
                @Suppress("DEPRECATION")
                g.writeDescriptor(desc)
            }
        } catch (e: Exception) {
            AppLogger.log("BluetoothSensorManager", "writeDescriptor exception: ${e.message}")
            false
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun syncTime(deviceAddress: String, onLog: (String) -> Unit = {}): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) {
        fun log(msg: String) {
            AppLogger.log("BluetoothSensorManager", msg)
            onLog(msg)
        }

        gattMutex.withLock {
            isGattBusy = true
            try {
                val device = bluetoothAdapter?.getRemoteDevice(deviceAddress) ?: return@withLock false
                val completion = kotlinx.coroutines.CompletableDeferred<Boolean>()
                
                val gattCallback = object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                        log("syncTime: onConnectionStateChange status=$status newState=$newState")
                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            g.discoverServices()
                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED && !completion.isCompleted) {
                            completion.complete(false)
                        }
                    }

                    override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                        log("syncTime: onServicesDiscovered status=$status")
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            val service = g.getService(UUID.fromString(SERVICE_UUID))
                                ?: g.services.firstOrNull { it.uuid.toString().contains("1f10", ignoreCase = true) }
                            val char = service?.getCharacteristic(UUID.fromString(CHAR_UUID))
                                ?: service?.characteristics?.firstOrNull { it.uuid.toString().contains("1f1f", ignoreCase = true) }

                            if (char != null) {
                                val currentTime = (System.currentTimeMillis() / 1000).toInt()
                                val timeCmd = ByteArray(5).apply {
                                    this[0] = 0x23.toByte()
                                    System.arraycopy(java.nio.ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(currentTime).array(), 0, this, 1, 4)
                                }
                                val ok = writeCharacteristic(g, char, timeCmd)
                                log("syncTime: writeCharacteristic initiated=$ok")
                                if (!ok) completion.complete(false)
                            } else {
                                log("syncTime: service or characteristic not found")
                                completion.complete(false)
                            }
                        } else {
                            completion.complete(false)
                        }
                    }
                    
                    override fun onCharacteristicWrite(g: BluetoothGatt, char: BluetoothGattCharacteristic?, status: Int) {
                        log("syncTime: onCharacteristicWrite status=$status")
                        completion.complete(status == BluetoothGatt.GATT_SUCCESS)
                        g.disconnect()
                    }
                }

                stopScanning()
                kotlinx.coroutines.delay(300)

                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return@withLock false
                
                val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                } else device.connectGatt(context, false, gattCallback)
                
                try {
                    kotlinx.coroutines.withTimeout(15000) { completion.await() }
                } catch (e: TimeoutCancellationException) {
                    log("syncTime: timeout (15s)")
                    false
                } finally {
                    gatt?.disconnect()
                    gatt?.close()
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
