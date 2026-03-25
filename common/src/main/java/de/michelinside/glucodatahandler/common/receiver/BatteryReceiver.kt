package de.michelinside.glucodatahandler.common.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import de.michelinside.glucodatahandler.common.utils.Log
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.notifier.*

class BatteryReceiver: BroadcastReceiver() {
    private val LOG_ID = "GDH.BatteryReceiver"
    override fun onReceive(context: Context, intent: Intent) {
        try {
            if (intent.extras == null || intent.extras!!.isEmpty) {
                return
            }
            val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            val enabled = sharedPref!!.getBoolean(Constants.SHARED_PREF_BATTERY_RECEIVER_ENABLED, true)
            val curValue = intent.extras!!.getInt(LEVEL, -1)
            Log.i(LOG_ID, "Received batter level: " + curValue.toString() + "% - enabled: $enabled")
            if (enabled) {
                if (curValue >= 0 && curValue != batteryPercentage) {
                    batteryPercentage = curValue
                    InternalNotifier.notify(context, NotifySource.BATTERY_LEVEL, batteryBundle)
                }
            } else {
                if(batteryPercentage != 0)
                    batteryPercentage = 0
            }
            // used as watchdog!
            GlucoDataService.checkServices(context)
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