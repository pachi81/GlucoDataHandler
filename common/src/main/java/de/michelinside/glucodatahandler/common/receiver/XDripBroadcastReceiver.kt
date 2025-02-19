package de.michelinside.glucodatahandler.common.receiver

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.R
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.SourceState
import de.michelinside.glucodatahandler.common.SourceStateData
import de.michelinside.glucodatahandler.common.utils.Utils
import de.michelinside.glucodatahandler.common.notifier.DataSource
import de.michelinside.glucodatahandler.common.utils.GlucoDataUtils

open class XDripBroadcastReceiver: NamedBroadcastReceiver() {
    companion object {
        const val BG_ESTIMATE = "com.eveningoutpost.dexdrip.Extras.BgEstimate"
        const val BG_SLOPE = "com.eveningoutpost.dexdrip.Extras.BgSlope"
        const val BG_SLOPE_NAME = "com.eveningoutpost.dexdrip.Extras.BgSlopeName"
        const val TIME = "com.eveningoutpost.dexdrip.Extras.Time"
        const val RAW = "com.eveningoutpost.dexdrip.Extras.Raw"
        const val SOURCE_DESC = "com.eveningoutpost.dexdrip.Extras.SourceDesc"
        const val SOURCE_INFO = "com.eveningoutpost.dexdrip.Extras.SourceInfo"
        const val NOISE_BLOCK_LEVEL = "com.eveningoutpost.dexdrip.Extras.NoiseBlockLevel"
        const val NOISE = "com.eveningoutpost.dexdrip.Extras.Noise"
        fun createExtras(context: Context?): Bundle? {
            if(ReceiveData.time == 0L)
                return null
            val extras = Bundle()
            extras.putDouble(BG_ESTIMATE,ReceiveData.rawValue.toDouble())
            extras.putString(BG_SLOPE_NAME, GlucoDataUtils.getDexcomLabel(ReceiveData.rate))
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

    override fun getName(): String {
        return LOG_ID
    }

    override fun onReceiveData(context: Context, intent: Intent) {
        try {
            Log.i(LOG_ID, "onReceive called for " + intent.action + ": " + Utils.dumpBundle(intent.extras))
            if (intent.extras != null) {
                val extras = intent.extras!!
                if (extras.containsKey(Constants.EXTRA_SOURCE_PACKAGE)) {
                    val packageSource = extras.getString(Constants.EXTRA_SOURCE_PACKAGE, "")
                    Log.d(LOG_ID, "Intent received from " + packageSource)
                    if (packageSource == context.packageName) {
                        Log.d(LOG_ID, "Ignore received intent from itself!")
                        return
                    }
                }
                if ((extras.containsKey(BG_ESTIMATE) || extras.containsKey(RAW)) && extras.containsKey(TIME)) {
                    var errorOccurs = false
                    var slope = Float.NaN
                    if (extras.containsKey(BG_SLOPE) || extras.containsKey(BG_SLOPE_NAME)) {
                        slope = extras.getDouble(BG_SLOPE, Double.NaN).toFloat()
                        if (!slope.isNaN())
                            slope = Utils.round(( slope * 60000), 2)
                        if (extras.containsKey(BG_SLOPE_NAME)) {
                            val slopeName = extras.getString(BG_SLOPE_NAME)
                            if (slopeName.isNullOrEmpty()) {
                                Log.w(LOG_ID, "No valid trend value received: " + Utils.dumpBundle(intent.extras))
                                slope = Float.NaN
                            } else if (slope.isNaN()) {
                                slope = GlucoDataUtils.getRateFromLabel(slopeName)
                            }
                        }
                    } else if(!extras.containsKey(BG_ESTIMATE)) {
                        // no slope, no estimate, maybe noise blocking
                        val noiseBlock = extras.getInt(NOISE_BLOCK_LEVEL, -1).toDouble()
                        val noise = extras.getDouble(NOISE, Double.NaN)
                        if(!noise.isNaN() && noiseBlock > 0.0 && noise > noiseBlock) {
                            Log.w(LOG_ID, "xDrip+ blocks sending data caused by noise-block: " + noiseBlock + " - noise: " + noise)
                            SourceStateData.setError(DataSource.XDRIP, context.getString(R.string.src_xdrip_noise_blocking) )
                            errorOccurs = true
                        }
                    }

                    var source: String? = if (extras.containsKey(SOURCE_INFO)) extras.getString(
                        SOURCE_INFO
                    ) else extras.getString(SOURCE_DESC)
                    if (source == null)
                        source = "xDrip+"
                    var mgdl = Utils.round(extras.getDouble(BG_ESTIMATE, 0.0).toFloat(), 0).toInt()
                    if (!GlucoDataUtils.isGlucoseValid(mgdl)) {
                        mgdl = Utils.round(extras.getDouble(RAW, 0.0).toFloat(), 0).toInt()
                    }
                    if (GlucoDataUtils.isGlucoseValid(mgdl)) {
                        val glucoExtras = Bundle()
                        glucoExtras.putLong(ReceiveData.TIME, extras.getLong(TIME))
                        glucoExtras.putInt(ReceiveData.MGDL,mgdl)
                        glucoExtras.putString(ReceiveData.SERIAL, source)
                        glucoExtras.putFloat(ReceiveData.RATE, slope)
                        glucoExtras.putInt(ReceiveData.ALARM, 0)
                        ReceiveData.handleIntent(context, DataSource.XDRIP, glucoExtras)
                        if (!errorOccurs) {
                            SourceStateData.setState(DataSource.XDRIP, SourceState.NONE)
                        }
                    } else {
                        Log.w(LOG_ID, "Invalid value: " + Utils.dumpBundle(intent.extras))
                        SourceStateData.setError(DataSource.XDRIP, "Invalid glucose value: " + mgdl )
                    }
                }
                else {
                    Log.w(LOG_ID, "Missing extras: " + Utils.dumpBundle(intent.extras))
                    SourceStateData.setError(DataSource.XDRIP, "Missing data in message!" )
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Receive exception: " + exc.message.toString() )
            SourceStateData.setError(DataSource.XDRIP, exc.message.toString() )
        }
    }
}
