package de.michelinside.glucodatahandler.common.receiver

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.microtechmd.blecomm.entity.BleMessage
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.notifier.DataSource
import de.michelinside.glucodatahandler.common.utils.AiDexCgmParser
import de.michelinside.glucodatahandler.common.utils.GlucoDataUtils

class AidexBroadcastReceiver : NamedBroadcastReceiver() {
    private val LOG_ID = "GDH.AidexBroadcastReceiver"
    override fun getName(): String {
        return "AidexBroadcastReceiver"
    }

    override fun onReceiveData(context: Context, intent: Intent) {
        try {
            Log.d(LOG_ID, "onReceiveData called for action ${intent.action}")
            if (intent.action == "com.microtechmd.cgms.NOTIFICATION") {
                // Set the class loader explicitly to allow deserialization of BleMessage,
                // which resides in the common module but is loaded by the main app's class loader.
                intent.extras?.classLoader = BleMessage::class.java.classLoader
                val message = intent.getSerializableExtra("message")
                if (message is BleMessage) {
                    if (message.operation == 1 && message.isSuccess) {
                        val rawData = message.data
                        Log.d(LOG_ID, "Hex dump of data payload: ${getHexDump(rawData)}")
                        val serialNumber = intent.getStringExtra("sn")
                        val record = AiDexCgmParser.parse(rawData, serialNumber)
                        if (record != null) {
                            Log.d(LOG_ID, "Parsed record: $record")
                            val extras = Bundle()
                            extras.putLong(ReceiveData.TIME, record.timestamp)
                            extras.putFloat(ReceiveData.GLUCOSECUSTOM, record.glucose)
                            extras.putInt(ReceiveData.MGDL, GlucoDataUtils.mmolToMg(record.glucose).toInt())
                            extras.putString(ReceiveData.SERIAL, record.serialNumber + "-" + record.sensorNumber)
                            // Battery is not directly supported in ReceiveData bundle, but we have it.
                            // extras.putInt("battery", record.battery)
                            // Sensor age in minutes, to seconds, to ms, subtracted from time now.
                            extras.putLong(ReceiveData.SENSOR_START_TIME, System.currentTimeMillis() - (record.sensorAge)*60*1000)

                            ReceiveData.handleIntent(context, DataSource.AIDEX, extras)
                        }
                    }
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onReceiveData exception: " + exc.message.toString() )
        }
    }

    // Utility function to convert ByteArray to a hex dump string (compatible with old API)
    private fun getHexDump(bytes: ByteArray): String {
        val result = StringBuilder()
        for (byte in bytes) {
            result.append(String.format("%02X ", byte))
        }
        return result.toString().trim()
    }
}