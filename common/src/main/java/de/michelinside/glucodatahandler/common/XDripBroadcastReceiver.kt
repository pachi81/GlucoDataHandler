package de.michelinside.glucodatahandler.common

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log

open class XDripBroadcastReceiver: BroadcastReceiver() {
    companion object {
        const val BG_ESTIMATE = "com.eveningoutpost.dexdrip.Extras.BgEstimate"
        const val BG_SLOPE = "com.eveningoutpost.dexdrip.Extras.BgSlope"
        const val BG_SLOPE_NAME = "com.eveningoutpost.dexdrip.Extras.BgSlopeName"
        const val TIME = "com.eveningoutpost.dexdrip.Extras.Time"
        const val RAW = "com.eveningoutpost.dexdrip.Extras.Raw"
        const val SOURCE_DESC = "com.eveningoutpost.dexdrip.Extras.SourceDesc"
        const val SOURCE_INFO = "com.eveningoutpost.dexdrip.Extras.SourceInfo"
        const val NOISE = "com.eveningoutpost.dexdrip.Extras.Noise"
        const val NOISE_WARNING = "com.eveningoutpost.dexdrip.Extras.NoiseWarning"
        const val NOISE_LEVEL = "com.eveningoutpost.dexdrip.Extras.NsNoiseLevel"
        const val NOISE_BLOCK_LEVEL = "com.eveningoutpost.dexdrip.Extras.NoiseBlockLevel"
        private var lowValue: Float = 70F
        private var highValue: Float = 250F
        fun updateSettings(sharedPref: SharedPreferences) {
            lowValue = sharedPref.getFloat(Constants.SHARED_PREF_LOW_GLUCOSE, lowValue)
            highValue = sharedPref.getFloat(Constants.SHARED_PREF_HIGH_GLUCOSE, highValue)
        }
    }
    private val LOG_ID = "GlucoDataHandler.XDripBroadcastReceiver"

    init {
        Log.d(LOG_ID, "init called")
    }
    override fun onReceive(context: Context, intent: Intent) {
        try {
            Log.d(LOG_ID, "onReceive called for " + intent.action + ": " + intent.extras.toString())
            val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            if (!sharedPref.getBoolean(Constants.SHARED_PREF_SEND_TO_XDRIP, false)) {  // only receive values, if send is disabled!
                if (intent.extras != null) {
                    val extras = intent.extras!!
                    val slope = Utils.round((extras.getDouble(BG_SLOPE)*60000).toFloat(), 2)
                    var source: String? = if(extras.containsKey(SOURCE_INFO)) extras.getString(SOURCE_INFO) else extras.getString(SOURCE_DESC)
                    if (source == null)
                        source = "xDrip+"
                    Log.i(LOG_ID, "Glucose: " + extras.getDouble(BG_ESTIMATE).toString() +
                            " - Time: " + ReceiveData.timeformat.format((extras.getLong(TIME))) +
                            " - Slope: " + slope.toString() +
                            " - SlopeName: " + extras.getString(BG_SLOPE_NAME) +
                            " - Raw: " + extras.getDouble(RAW).toString() +
                            " - Source: " + extras.getString(SOURCE_INFO) + " (" + extras.getString(SOURCE_DESC) + ")" +
                            " - Noise: " + extras.getDouble(NOISE).toString() +
                            " - Noise-Warning: " + extras.getInt(NOISE_WARNING).toString() +
                            " - Noise-Blocklevel: " + extras.getInt(NOISE_BLOCK_LEVEL).toString() +
                            " - NS Noise-Level: " + extras.getString(NOISE_LEVEL)
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
                    if (mgdl >= highValue)
                        glucoExtras.putInt(ReceiveData.ALARM, 6)
                    else if (mgdl <= lowValue)
                        glucoExtras.putInt(ReceiveData.ALARM, 7)
                    else
                        glucoExtras.putInt(ReceiveData.ALARM, 0)

                    ReceiveData.handleIntent(context, ReceiveDataSource.BROADCAST, glucoExtras)
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Receive exception: " + exc.message.toString() )
        }
    }
}