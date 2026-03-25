package de.michelinside.glucodatahandler.common.utils

import java.nio.ByteBuffer
import java.nio.ByteOrder

object AiDexCgmParser {

    data class AiDexRecord(
        val timestamp: Long,
        val glucose: Float,
        val battery: Int,
        val serialNumber: String?,
        val sensorAge: Long,
        val sensorNumber: Int,
        val readingAge: Long

    )

    fun parse(rawData: ByteArray, serialNumber: String?): AiDexRecord? {
        // A fair bit of this is also documented on https://blog.ivor.org/2022/03/sniffing-sugar.html
        // I did manage to decode a few of these pieces myself
        try {
            if (rawData.size < 11) return null

            val buffer = ByteBuffer.wrap(rawData).order(ByteOrder.LITTLE_ENDIAN)

            // Header, seems to be battery voltage minus 100 (to fit in 1 byte), in 0.01 volt increments
            val battery = buffer.get(0).toUByte().toInt()
            // Sample age, in 10s of seconds - convert to seconds by multiplying
            val sampleAge = buffer.get(1)*10;
            // Timestamp calculation
            // Formula: 0x386CD300L + (rawData[1] & 0xFF) * 10L + (uint32 from rawData[2..5])
            // Above gives the last broadcast time, rounded to 10s
            // Don't add byte1 and you get reading time
            val byte1 = buffer.get(1).toUByte().toLong()
            val time32 = buffer.getInt(2).toUInt().toLong()
            val baseTime = 0x386CD300L
            val timestampSec = baseTime  + time32
            val timestampMs = timestampSec * 1000L
            // The age of the sensor, in minutes - index 6 is in 5 minute 'ticks'
            val sensorAgeMin = buffer.getShort(6)*5;
            // Sensor number used on this transmitter - it's not an ID of the sensor itself, but a
            // count of how many sensors have been plugged into this transmitter
            val sensorNumber = buffer.get(8).toUByte().toInt()


            // State & Glucose Logic
            val byte9 = rawData[9].toInt()
            val state = when {
                (byte9 and (1 shl 5)) != 0 -> 2
                (byte9 and (1 shl 6)) != 0 -> 1
                (byte9 and (1 shl 7)) != 0 -> 3
                else -> 0
            }

            // Event Index (masked byte 9)
            val eventIndex = byte9 and 0x1F

            // Event Data (byte 10)
            val eventDataByte = rawData[10]

            // Valid Glucose Check
            // 1. Event Index < 0x1F
            // 2. Bitmask check
            // 3. State is 0 or 3
            
            if (eventIndex < 0x1F) {
                val mask = 0x40019D80
                // 1 << eventIndex
                if (((1 shl eventIndex) and mask) != 0) {
                    if (state == 0 || state == 3) {
                        var glucose: Float
                        if (eventIndex == 4) {
                            glucose = eventDataByte.toFloat()
                        } else {
                            glucose = (eventDataByte.toUByte().toInt()).toFloat() / 10.0f
                        }

                        if (glucose > 0) {
                            return AiDexRecord(timestampMs, glucose, battery, serialNumber, sensorAgeMin.toLong(), sensorNumber, sampleAge.toLong())
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GDH.AiDexParser", "Error parsing AiDex data: " + e.message)
        }
        return null
    }
}