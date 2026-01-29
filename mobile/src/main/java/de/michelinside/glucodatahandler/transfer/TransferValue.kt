package de.michelinside.glucodatahandler.transfer

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import de.michelinside.glucodatahandler.android_auto.CarModeReceiver
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.Intents
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.receiver.XDripBroadcastReceiver
import de.michelinside.glucodatahandler.common.utils.Log
import de.michelinside.glucodatahandler.common.utils.Utils
import java.math.RoundingMode

object TransferValue {
    private val LOG_ID = "GDH.TransferValue"
    private var lastForwardTime = 0L

    fun transferNewValue(context: Context, extras: Bundle) {
        Log.v(LOG_ID, "transferNewValue called")
        CarModeReceiver.sendToGlucoDataAuto(context)

        val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, MODE_PRIVATE)
        /*
        if (sharedPref.getBoolean(Constants.SHARED_PREF_SEND_TO_BANGLEJS, false)) {
            sendToBangleJS(context)
        }*/

        val interval = sharedPref.getInt(Constants.SHARED_PREF_SEND_TO_RECEIVER_INTERVAL, 1)
        val elapsed = Utils.getElapsedTimeMinute(lastForwardTime, RoundingMode.HALF_UP)
        if (interval > 1 && elapsed < interval) {
            Log.d(LOG_ID, "Ignore data because of interval $interval - elapsed: $elapsed")
            return
        }

        if (sharedPref.getBoolean(Constants.SHARED_PREF_SEND_TO_XDRIP, false)) {
            val intent = Intent(Constants.XDRIP_ACTION_GLUCOSE_READING)
            // always sends time as start time, because it is only set, if the sensorId have changed!
            val sensor = Bundle()
            sensor.putLong("sensorStartTime", if(ReceiveData.sensorStartTime > 0) ReceiveData.sensorStartTime else Utils.getDayStartTime())  // use start time of the current day
            val currentSensor = Bundle()
            currentSensor.putBundle("currentSensor", sensor)
            intent.putExtra("sas", currentSensor)
            val bleManager = Bundle()
            bleManager.putString("sensorSerial", ReceiveData.sensorID ?: context.packageName)
            intent.putExtra("bleManager", bleManager)
            intent.putExtra("glucose", ReceiveData.rawValue.toDouble())
            intent.putExtra("timestamp", ReceiveData.time)
            sendBroadcast(intent, Constants.SHARED_PREF_XDRIP_RECEIVERS, context, sharedPref)
        }

        if (sharedPref.getBoolean(Constants.SHARED_PREF_SEND_XDRIP_BROADCAST, false)) {
            val xDripExtras = XDripBroadcastReceiver.createExtras(context)
            if (xDripExtras != null) {
                val intent = Intent(Intents.XDRIP_BROADCAST_ACTION)
                intent.putExtras(xDripExtras)
                sendBroadcast(intent, Constants.SHARED_PREF_XDRIP_BROADCAST_RECEIVERS, context, sharedPref)
            }
        }

        if (sharedPref.getBoolean(Constants.SHARED_PREF_SEND_TO_GLUCODATA_AOD, false)) {
            val intent = Intent(Constants.GLUCODATA_BROADCAST_ACTION)
            intent.putExtras(extras)
            sendBroadcast(intent, Constants.SHARED_PREF_GLUCODATA_RECEIVERS, context, sharedPref)
        }
    }

    private fun sendBroadcast(intent: Intent, receiverPrefKey: String, context: Context, sharedPref: SharedPreferences) {
        try {
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            var receivers = sharedPref.getStringSet(receiverPrefKey, HashSet<String>())
            Log.i(LOG_ID, "Forward " + receiverPrefKey + " Broadcast to " + receivers?.size.toString() + " receivers")
            if(Log.isLoggable(LOG_ID, android.util.Log.DEBUG))
                Log.d(LOG_ID, "Forward package: ${Utils.dumpBundle(intent.extras)}")
            if (receivers == null || receivers.size == 0) {
                receivers = setOf("")
            }
            for( receiver in receivers ) {
                val sendIntent = intent.clone() as Intent
                if (!receiver.isEmpty()) {
                    sendIntent.setPackage(receiver)
                    Log.d(LOG_ID, "Send broadcast " + receiverPrefKey + " to " + receiver.toString())
                } else {
                    Log.d(LOG_ID, "Send global broadcast " + receiverPrefKey)
                    sendIntent.putExtra(Constants.EXTRA_SOURCE_PACKAGE, context.packageName)
                }
                context.sendBroadcast(sendIntent)
            }
            lastForwardTime = ReceiveData.time
        } catch (ex: Exception) {
            Log.e(LOG_ID, "Exception while sending broadcast for " + receiverPrefKey + ": " + ex)
        }
    }

    /*
    private fun sendToBangleJS(context: Context) {
        val send2Bangle = "require(\"Storage\").writeJSON(\"widbgjs.json\", {" +
                "'bg': " + ReceiveData.rawValue.toString() + "," +
                "'bgTimeStamp': " + ReceiveData.time + "," +
                "'bgDirection': '" + GlucoDataUtils.getDexcomLabel(ReceiveData.rate) + "'" +
                "});"

        Log.i(LOG_ID, "Send to bangleJS: " + send2Bangle)
        val sendIntent = Intent("com.banglejs.uart.tx")
        sendIntent.putExtra("line", send2Bangle)
        context.sendBroadcast(sendIntent)
    }
*/
}