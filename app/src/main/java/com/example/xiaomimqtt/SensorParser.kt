package com.example.xiaomimqtt

import android.util.Log

object SensorParser {
    
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
                        batt = ((vRaw - 2100) / 10).coerceIn(0, 100)
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
                ((vbat - 2100).coerceIn(0, 1000) / 10.0).toInt()
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
        val low = data[offset].toUByte().toInt()
        val high = data[offset + 1].toUByte().toInt()
        var raw = (high shl 8) or low
        if (raw > 32767) raw -= 65536
        return raw
    }

    private fun readUInt16LE(data: ByteArray, offset: Int): Int {
        val low = data[offset].toUByte().toInt()
        val high = data[offset + 1].toUByte().toInt()
        return (high shl 8) or low
    }

    private fun createSensorData(mac: String, name: String, temp: Double, hum: Double, batt: Int): SensorData? {
        if (temp < -40 || temp > 80 || hum < 0 || hum > 100) return null
        if (temp == 0.0 && hum == 0.0) return null
        
        return SensorData(
            macAddress = mac,
            deviceName = name,
            temperature = Math.round(temp * 100) / 100.0,
            humidity = Math.round(hum * 100) / 100.0,
            battery = batt
        )
    }
}
