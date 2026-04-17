package de.michelinside.glucodatahandler.common.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Bundle
import de.michelinside.glucodatahandler.common.utils.Log
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.notifier.*
import de.michelinside.glucodatahandler.common.utils.Utils

class BatteryReceiver: BroadcastReceiver() {
    private val LOG_ID = "GDH.BatteryReceiver"
    override fun onReceive(context: Context, intent: Intent) {
        try {
            if (intent.extras == null || intent.extras!!.isEmpty) {
                return
            }
            Log.v(LOG_ID, "Batter intent received: ${Utils.dumpBundle(intent.extras)}")
            val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            val enabled = sharedPref!!.getBoolean(Constants.SHARED_PREF_BATTERY_RECEIVER_ENABLED, true)
            val curValue = intent.extras!!.getInt(LEVEL, -1)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            Log.i(LOG_ID, "Received batter level: ${curValue}% - status: $status - isCharging: ${isCharging(status)} - enabled: $enabled")
            if (enabled) {
                if (curValue >= 0 && curValue != batteryPercentage || status != batteryStatus) {
                    batteryPercentage = curValue
                    batteryStatus = status
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
        const val LEVEL = BatteryManager.EXTRA_LEVEL
        const val STATUS = BatteryManager.EXTRA_STATUS
        var batteryPercentage: Int = 0
        var batteryStatus: Int = BatteryManager.BATTERY_STATUS_UNKNOWN
        fun isCharging(status: Int): Boolean {
            return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
        }
        val batteryBundle: Bundle get() {
            val extra = Bundle()
            extra.putInt(LEVEL, batteryPercentage)
            extra.putInt(STATUS, batteryStatus)
            return extra
        }
    }
}