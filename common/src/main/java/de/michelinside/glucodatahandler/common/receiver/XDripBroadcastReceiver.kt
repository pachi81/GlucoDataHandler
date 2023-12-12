package de.michelinside.glucodatahandler.common.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.utils.Utils
import de.michelinside.glucodatahandler.common.notifier.DataSource
import java.text.DateFormat

open class XDripBroadcastReceiver: BroadcastReceiver() {
    companion object {
        const val BG_ESTIMATE = "com.eveningoutpost.dexdrip.Extras.BgEstimate"
        const val BG_SLOPE = "com.eveningoutpost.dexdrip.Extras.BgSlope"
        const val BG_SLOPE_NAME = "com.eveningoutpost.dexdrip.Extras.BgSlopeName"
        const val TIME = "com.eveningoutpost.dexdrip.Extras.Time"
        const val RAW = "com.eveningoutpost.dexdrip.Extras.Raw"
        const val SOURCE_DESC = "com.eveningoutpost.dexdrip.Extras.SourceDesc"
        const val SOURCE_INFO = "com.eveningoutpost.dexdrip.Extras.SourceInfo"
        fun createExtras(context: Context?): Bundle? {
            if(ReceiveData.time == 0L)
                return null
            val extras = Bundle()
            extras.putDouble(BG_ESTIMATE,ReceiveData.rawValue.toDouble())
            extras.putString(BG_SLOPE_NAME,ReceiveData.getDexcomLabel())
            extras.putDouble(BG_SLOPE,ReceiveData.rate.toDouble()/60000.0)
            extras.putLong(TIME,ReceiveData.time)
            extras.putString(SOURCE_INFO,ReceiveData.sensorID)
            if (context != null) {
                extras.putString(SOURCE_DESC,context.getString(ReceiveData.source.resId))
            }
            return extras
        }
    }
    private val LOG_ID = "GDH.XDripBroadcastReceiver"

    init {
        Log.d(LOG_ID, "init called")
    }
    override fun onReceive(context: Context, intent: Intent) {
        try {
            Log.d(LOG_ID, "onReceive called for " + intent.action + ": " + intent.extras.toString())
            if (intent.extras != null) {
                val extras = intent.extras!!
                val slope = Utils.round((extras.getDouble(BG_SLOPE) * 60000).toFloat(), 2)
                var source: String? = if(extras.containsKey(SOURCE_INFO)) extras.getString(
                    SOURCE_INFO
                ) else extras.getString(SOURCE_DESC)
                if (source == null)
                    source = "xDrip+"
                Log.i(LOG_ID, "Glucose: " + extras.getDouble(BG_ESTIMATE).toString() +
                        " - Time: " + DateFormat.getTimeInstance(DateFormat.DEFAULT).format((extras.getLong(TIME))) +
                        " - Slope: " + slope.toString() +
                        " - SlopeName: " + extras.getString(BG_SLOPE_NAME) +
                        " - Raw: " + extras.getDouble(RAW).toString() +
                        " - Source: " + extras.getString(SOURCE_INFO) + " (" + extras.getString(
                    SOURCE_DESC
                ) + ")"
                )

                val glucoExtras = Bundle()
                glucoExtras.putLong(ReceiveData.TIME, extras.getLong(TIME))
                val mgdl = extras.getDouble(BG_ESTIMATE).toFloat()
                if (ReceiveData.isMmol) {
                    glucoExtras.putFloat(ReceiveData.GLUCOSECUSTOM, Utils.mgToMmol(mgdl))
                } else {
                    glucoExtras.putFloat(ReceiveData.GLUCOSECUSTOM, mgdl)
                }
                glucoExtras.putInt(ReceiveData.MGDL, extras.getDouble(BG_ESTIMATE).toInt())
                glucoExtras.putString(ReceiveData.SERIAL, source)
                glucoExtras.putFloat(ReceiveData.RATE, slope)
                glucoExtras.putInt(ReceiveData.ALARM, 0)
                ReceiveData.handleIntent(context, DataSource.XDRIP, glucoExtras)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Receive exception: " + exc.message.toString() )
        }
    }
}
