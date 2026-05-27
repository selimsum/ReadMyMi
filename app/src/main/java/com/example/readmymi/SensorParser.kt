package com.example.readmymi

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
        return when {
            voltageMv >= 3000 -> 100
            voltageMv >= 2900 -> {
                80 + ((voltageMv - 2900) * 20 / 100)
            }
            voltageMv >= 2800 -> {
                50 + ((voltageMv - 2800) * 30 / 100)
            }
            voltageMv >= 2700 -> {
                20 + ((voltageMv - 2700) * 30 / 100)
            }
            voltageMv >= 2500 -> {
                5 + ((voltageMv - 2500) * 15 / 200)
            }
            else -> {
                ((voltageMv - 2100).coerceAtLeast(0) * 5 / 400)
            }
        }
    }

    fun parse(deviceName: String, macAddress: String, serviceData: Map<String, ByteArray>): SensorData? {
        serviceData.forEach { (uuid, data) ->
            val uuidString = uuid.lowercase()
            val hexData = data.joinToString("") { "%02x".format(it) }
            
            try {
                val parsed = when {
                    uuidString.contains("fcd2") -> parseBThome(macAddress, deviceName, data)
                    uuidString.contains("181a") || deviceName.contains("ATC", true) -> parseATC(macAddress, deviceName, data)
                    uuidString.contains("fe95") -> parseXiaomi(macAddress, deviceName, data)
                    else -> null
                }
                if (parsed != null) {
                    AppLogger.log("Parser", "SUCCESS parsed $deviceName ($macAddress) via UUID $uuidString: temp=${parsed.temperature}°C hum=${parsed.humidity}% batt=${parsed.battery}% (raw: $hexData)")
                    return parsed
                }
            } catch (e: Exception) {
                Log.e("SensorParser", "Error parsing $deviceName ($macAddress): ${e.message}")
                AppLogger.log("Parser", "ERROR parsing $deviceName ($macAddress) via UUID $uuidString (raw: $hexData): ${e.message}")
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
        var rawBattPct: Int? = null
        var voltageBattPct: Int? = null
        var rawVoltageMv: Int? = null
        var i = 1

        while (i < data.size) {
            val typeId = data[i].toUByte().toInt()
            i++
            if (i >= data.size) break

            when (typeId) {
                0x01 -> { 
                    rawBattPct = data[i].toUByte().toInt()
                    i += 1 
                }
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
                    if (i + 1 < data.size) {
                        val vRaw = readUInt16LE(data, i)
                        rawVoltageMv = vRaw
                        voltageBattPct = calculateBatteryPercentage(vRaw)
                    }
                    i += 2
                }
                0x00, 0x10, 0x11 -> i += 1
                else -> break // Unknown type, stop parsing to avoid desync
            }
        }

        val batt = voltageBattPct ?: rawBattPct ?: 0
        if (rawVoltageMv != null || rawBattPct != null) {
            AppLogger.log("Parser", "BTHome parsed $mac: voltage=$rawVoltageMv mV -> percentage=$voltageBattPct%, rawBattPct=$rawBattPct% -> final=$batt%")
        }

        return if (temp != 0.0 || hum != 0.0) createSensorData(mac, name, temp, hum, batt) else null
    }

    private fun parseATC(mac: String, name: String, data: ByteArray): SensorData? {
        if (data.size < 12) return null
        
        val tempLE = readInt16LE(data, 6) / 100.0
        val tempBE = ((data[6].toInt() shl 8) or (data[7].toUByte().toInt())) / 100.0
        val temp = if (tempLE in -40.0..80.0) tempLE else tempBE

        val hum = if (data.size >= 13) {
            readUInt16LE(data, 8) / 100.0
        } else {
            data[8].toUByte().toInt().toDouble()
        }
        
        val vbatLE = readUInt16LE(data, 10)
        val vbatBE = ((data[10].toUByte().toInt() shl 8) or data[11].toUByte().toInt()) and 0xFFFF
        val vbat = when {
            vbatLE in 1800..3600 -> vbatLE
            vbatBE in 1800..3600 -> vbatBE
            else -> 0
        }
        
        val rawPct = if (data.size >= 13) data[12].toUByte().toInt() else data[9].toUByte().toInt()
        val batt = if (vbat > 0) {
            calculateBatteryPercentage(vbat)
        } else {
            rawPct
        }
        
        AppLogger.log("Parser", "ATC parsed $mac: tempLE=$tempLE tempBE=$tempBE hum=$hum vbatLE=$vbatLE vbatBE=$vbatBE vbat=$vbat -> calculated=${if (vbat > 0) batt else "N/A"}, rawPct=$rawPct -> final=$batt%")
        
        return createSensorData(mac, name, temp, hum, batt)
    }

    private fun parseXiaomi(mac: String, name: String, data: ByteArray): SensorData? {
        if (name.contains("ATC", true) || data.size >= 15) {
            if (data.size < 15) return null
            val temp = readInt16LE(data, 11) / 10.0
            val hum = readUInt16LE(data, 13) / 10.0
            var vbat = 0
            var vbatLE = 0
            var vbatBE = 0
            val batt = if (data.size >= 17) {
                vbatLE = readUInt16LE(data, 15)
                vbatBE = ((data[15].toUByte().toInt() shl 8) or data[16].toUByte().toInt()) and 0xFFFF
                vbat = when {
                    vbatLE in 1800..3600 -> vbatLE
                    vbatBE in 1800..3600 -> vbatBE
                    else -> 0
                }
                if (vbat > 0) calculateBatteryPercentage(vbat) else 0
            } else 0
            
            AppLogger.log("Parser", "Xiaomi parsed $mac (size>=15): temp=$temp hum=$hum vbatLE=$vbatLE vbatBE=$vbatBE vbat=$vbat -> batt=$batt%")
            return createSensorData(mac, name, temp, hum, batt)
        } else if (data.size >= 13) {
            val temp = (data[7].toUByte().toInt() * 256 + data[6].toUByte().toInt()) / 100.0
            val hum = data[8].toUByte().toInt().toDouble()
            val batt = data[12].toUByte().toInt()
            
            AppLogger.log("Parser", "Xiaomi parsed $mac (size>=13): temp=$temp hum=$hum batt=$batt%")
            return createSensorData(mac, name, temp, hum, batt)
        }
        return null
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
            battery = batt,
            timestamp = System.currentTimeMillis()
        )
    }
}
