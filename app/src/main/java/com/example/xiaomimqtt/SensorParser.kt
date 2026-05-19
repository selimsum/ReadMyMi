package com.example.xiaomimqtt

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

object SensorParser {

    private const val MIN_TEMP = -40.0
    private const val MAX_TEMP = 80.0
    private const val MIN_HUM = 0.0
    private const val MAX_HUM = 100.0
    private const val ROUNDING_MULTIPLIER = 100.0
    
    private const val MIN_VOLTAGE_MV = 2100

    fun calculateBatteryPercentage(voltageMv: Int): Int {
        return ((voltageMv - MIN_VOLTAGE_MV).coerceIn(0, 1000) / 10.0).toInt()
    }

    fun parse(deviceName: String, macAddress: String, serviceData: Map<String, ByteArray>): SensorData? {
        serviceData.forEach { (uuid, data) ->
            val uuidString = uuid.lowercase()
            
            try {
                when {
                    uuidString.contains("fcd2") -> return parseBThome(macAddress, deviceName, data)
                    uuidString.contains("181a") || deviceName.contains("ATC", true) -> return parseATC(macAddress, deviceName, data)
                    uuidString.contains("fe95") -> return parseXiaomi(macAddress, deviceName, data)
                }
            } catch (e: Exception) {
                Log.e("SensorParser", "Error parsing $deviceName ($macAddress): ${e.message}")
            }
        }
        return null
    }

    private fun parseBThome(mac: String, name: String, data: ByteArray): SensorData? {
        if (data.size < 2) return null
        val infoByte = data[0].toUByte().toInt()
        if ((infoByte and 0x01) != 0) return null // Encrypted

        var temp = 0.0
        var hum = 0.0
        var batt = 0
        var i = 1

        while (i < data.size) {
            val typeId = data[i].toUByte().toInt()
            i++
            if (i >= data.size) break

            when (typeId) {
                0x01 -> { batt = data[i].toUByte().toInt(); i += 1 }
                0x02 -> { // Temp (int16, 0.01)
                    if (i + 1 < data.size) {
                        temp = readInt16LE(data, i) * 0.01
                        i += 2
                    } else break
                }
                0x03 -> { // Hum (uint16, 0.01)
                    if (i + 1 < data.size) {
                        hum = readUInt16LE(data, i) * 0.01
                        i += 2
                    } else break
                }
                0x0C -> { // Voltage (uint16, 0.001V)
                    if (i + 1 < data.size && batt == 0) {
                        val vRaw = readUInt16LE(data, i)
                        batt = calculateBatteryPercentage(vRaw)
                    }
                    i += 2
                }
                0x00, 0x10, 0x11 -> i += 1
                else -> break // Unknown type, stop parsing to avoid desync
            }
        }

        return if (temp != 0.0 || hum != 0.0) createSensorData(mac, name, temp, hum, batt) else null
    }

    private fun parseATC(mac: String, name: String, data: ByteArray): SensorData? {
        if (data.size < 12) return null
        
        val temp = readInt16LE(data, 6) / 100.0
        val hum = data[8].toUByte().toInt().toDouble()
        val batt = data[9].toUByte().toInt()
        
        return createSensorData(mac, name, temp, hum, batt)
    }

    private fun parseXiaomi(mac: String, name: String, data: ByteArray): SensorData? {
        return if (name.contains("ATC", true) || data.size >= 15) {
            if (data.size < 15) return null
            val temp = readInt16LE(data, 11) / 10.0
            val hum = readUInt16LE(data, 13) / 10.0
            val batt = if (data.size >= 17) {
                val vbat = readUInt16LE(data, 15)
                calculateBatteryPercentage(vbat)
            } else 0
            createSensorData(mac, name, temp, hum, batt)
        } else if (data.size >= 13) {
            val temp = (data[7].toUByte().toInt() * 256 + data[6].toUByte().toInt()) / 100.0
            val hum = data[8].toUByte().toInt().toDouble()
            val batt = data[12].toUByte().toInt()
            createSensorData(mac, name, temp, hum, batt)
        } else null
    }

    private fun readInt16LE(data: ByteArray, offset: Int): Int {
        return ByteBuffer.wrap(data, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
    }

    private fun readUInt16LE(data: ByteArray, offset: Int): Int {
        return ByteBuffer.wrap(data, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
    }

    private fun createSensorData(mac: String, name: String, temp: Double, hum: Double, batt: Int): SensorData? {
        if (temp < MIN_TEMP || temp > MAX_TEMP || hum < MIN_HUM || hum > MAX_HUM) return null
        if (temp == 0.0 && hum == 0.0) return null
        
        return SensorData(
            macAddress = mac,
            deviceName = name,
            temperature = Math.round(temp * ROUNDING_MULTIPLIER) / ROUNDING_MULTIPLIER,
            humidity = Math.round(hum * ROUNDING_MULTIPLIER) / ROUNDING_MULTIPLIER,
            battery = batt
        )
    }
}
