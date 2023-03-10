package de.michelinside.glucodatahandler.common

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log

class BatteryReceiver: BroadcastReceiver() {
    private val LOG_ID = "GlucoDataHandler.BatteryReceiver"
    override fun onReceive(context: Context, intent: Intent) {
        try {
            if (intent.extras == null || intent.extras!!.isEmpty) {
                return
            }
            val curValue = intent.extras!!.getInt(LEVEL, -1)
            Log.i(LOG_ID, "Received batter level: " + curValue.toString() + "%")
            if (curValue >= 0) {
                batteryPercentage = curValue
                ReceiveData.notify(context, ReceiveDataSource.BATTERY_LEVEL, batteryBundle)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "BatteryReceiver exception: " + exc.message.toString() )
        }
    }

    companion object {
        const val LEVEL = "level"
        var batteryPercentage: Int = 0
        val batteryBundle: Bundle get() {
            val extra = Bundle()
            extra.putInt(LEVEL, batteryPercentage)
            return extra
        }
    }
}