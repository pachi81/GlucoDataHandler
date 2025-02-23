package de.michelinside.glucodatahandler.common.receiver

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.SourceState
import de.michelinside.glucodatahandler.common.SourceStateData
import de.michelinside.glucodatahandler.common.notifier.DataSource
import de.michelinside.glucodatahandler.common.utils.GlucoDataUtils
import de.michelinside.glucodatahandler.common.utils.Utils

open class AAPSReceiver: NamedBroadcastReceiver() {
    private val LOG_ID = "GDH.AAPSReceiver"
    companion object {
        private const val BG_VALUE = "glucoseMgdl" // double
        private const val BG_TIMESTAMP = "glucoseTimeStamp" // long (ms)
        private const val BG_UNITS = "units" // string: "mg/dl" or "mmol"
        private const val BG_SLOPE = "slopeArrow" // string: direction arrow as string
        private const val IOB_VALUE = "iob" // double
        private const val COB_VALUE = "cob" // double: COB [g] or -1 if N/A
        //private const val PUMP_STATUS = "pumpStatus"         // string
        private const val PROFILE_NAME = "profile"         // string
    }

    override fun getName(): String {
        return LOG_ID
    }

    override fun onReceiveData(context: Context, intent: Intent) {
        try {
            Log.i( LOG_ID,"onReceive called for " + intent.action + ": " + Utils.dumpBundle(intent.extras))
            if (intent.extras != null) {
                val extras = intent.extras!!
                if (extras.containsKey(BG_VALUE) && extras.containsKey(BG_TIMESTAMP)) {
                    val mgdl = Utils.round(extras.getDouble(BG_VALUE, 0.0).toFloat(), 0)
                    if (GlucoDataUtils.isGlucoseValid(mgdl)) {
                        val glucoExtras = Bundle()
                        glucoExtras.putLong(ReceiveData.TIME, extras.getLong(BG_TIMESTAMP))
                        glucoExtras.putInt(ReceiveData.MGDL,mgdl.toInt())
                        if(extras.containsKey(BG_UNITS)) {
                            val unit = extras.getString(BG_UNITS)
                            if(unit == "mmol")
                                glucoExtras.putFloat(ReceiveData.GLUCOSECUSTOM, GlucoDataUtils.mgToMmol(mgdl))
                            else if(unit == "mg/dl")
                                glucoExtras.putFloat(ReceiveData.GLUCOSECUSTOM, mgdl)
                            else
                                Log.w(LOG_ID, "No valid unit received: " + unit)
                        }
                        val slopeName = extras.getString(BG_SLOPE)
                        var slope = Float.NaN
                        if (extras.containsKey(BG_SLOPE)) {
                            if (slopeName.isNullOrEmpty()) {
                                Log.w(LOG_ID, "No valid trend value received: " + Utils.dumpBundle(intent.extras))
                                slope = Float.NaN
                            } else if (slope.isNaN()) {
                                slope = GlucoDataUtils.getRateFromLabel(slopeName)
                            }
                        }
                        glucoExtras.putFloat(ReceiveData.RATE, slope)
                        if(extras.containsKey(IOB_VALUE)) {
                            glucoExtras.putFloat(ReceiveData.IOB, extras.getDouble(IOB_VALUE, Double.NaN).toFloat())
                        } else {
                            glucoExtras.putFloat(ReceiveData.IOB, Float.NaN)
                        }
                        if(extras.containsKey(COB_VALUE)) {
                            glucoExtras.putFloat(ReceiveData.COB, Utils.getCobValue(extras.getDouble(COB_VALUE, Double.NaN).toFloat()))
                        } else {
                            glucoExtras.putFloat(ReceiveData.COB, Float.NaN)
                        }
                        if(extras.containsKey(PROFILE_NAME)) {
                            glucoExtras.putString(ReceiveData.SERIAL, extras.getString(PROFILE_NAME))
                        }
                        ReceiveData.handleIntent(context, DataSource.AAPS, glucoExtras)
                        SourceStateData.setState(DataSource.AAPS, SourceState.NONE)
                    } else {
                        SourceStateData.setError(DataSource.AAPS, "Invalid glucose value: " + extras.getDouble(BG_VALUE, 0.0))
                    }
                } else {
                    SourceStateData.setError(DataSource.AAPS, "Missing values in AAPS intent!")
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Receive exception: " + exc.message.toString() )
            SourceStateData.setError(DataSource.AAPS, exc.message.toString() )
        }
    }
}