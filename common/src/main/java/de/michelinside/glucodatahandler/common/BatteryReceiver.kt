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
            val curValue = intent.extras!!.getInt("level", -1)
            Log.i(LOG_ID, "Received batter level: " + curValue.toString() + "%")
            if (curValue >= 0) {
                batteryPercentage = curValue
                val extra = Bundle()
                extra.putInt("level", curValue)
                ReceiveData.notify(context, ReceiveDataSource.BATTERY_LEVEL, extra)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "BatteryReceiver exception: " + exc.message.toString() )
        }
    }

    companion object {
        var batteryPercentage: Int = 0
    }
}