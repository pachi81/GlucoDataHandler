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


open class DexcomBroadcastReceiver: NamedBroadcastReceiver() {
    private val LOG_ID = "GDH.DexcomBroadcastReceiver"

    private val EXTRA_GLUCOSE_VALUES = "glucoseValues"
    private val EXTRA_TIMESTAMP = "timestamp"
    private val EXTRA_GLUCOSE = "glucoseValue"
    private val EXTRA_TREND = "trendArrow"

    override fun getName(): String {
        return LOG_ID
    }

    override fun onReceiveData(context: Context, intent: Intent) {
        try {
            val extras = intent.extras
            Log.i(LOG_ID, "onReceive called for " + intent.action + ": " + Utils.dumpBundle(extras))
            if (extras != null) {
                val glucoseValues = extras.getBundle(EXTRA_GLUCOSE_VALUES)
                if (glucoseValues != null) {
                    // get only the latest values
                    var maxTimestamp = 0L
                    var glucoseValue = 0
                    var trendStr: String? = null
                    Log.i(LOG_ID, "${glucoseValues.size()} values included")
                    for (i in 0 until glucoseValues.size()) {
                        val glucoseValueBundle = glucoseValues.getBundle(i.toString())
                        if (glucoseValueBundle != null) {
                            val timestamp = glucoseValueBundle.getLong(EXTRA_TIMESTAMP)
                            val value = glucoseValueBundle.getInt(EXTRA_GLUCOSE)
                            if (timestamp > maxTimestamp && value > 0) {
                                maxTimestamp = timestamp
                                glucoseValue = value
                                trendStr = glucoseValueBundle.getString(EXTRA_TREND)
                                //Log.d(LOG_ID, "Newer value found: $glucoseValue - time: $maxTimestamp - trend: $trendStr")
                            }
                        }
                    }

                    Log.i(LOG_ID, "Using last value: $glucoseValue - time: $maxTimestamp - trend: $trendStr")
                    if (GlucoDataUtils.isGlucoseValid(glucoseValue) && maxTimestamp > 0) {
                        val glucoExtras = Bundle()
                        glucoExtras.putLong(ReceiveData.TIME, maxTimestamp)
                        glucoExtras.putInt(ReceiveData.MGDL,glucoseValue)
                        glucoExtras.putFloat(ReceiveData.RATE, GlucoDataUtils.getRateFromLabel(trendStr))
                        glucoExtras.putInt(ReceiveData.ALARM, 0)
                        ReceiveData.handleIntent(context, DataSource.DEXCOM_BYODA, glucoExtras)
                        SourceStateData.setState(DataSource.DEXCOM_BYODA, SourceState.NONE)
                    } else {
                        Log.w(LOG_ID, "Invalid value: " + Utils.dumpBundle(intent.extras))
                        SourceStateData.setError(DataSource.DEXCOM_BYODA, "Invalid glucose value: " + glucoseValue )
                    }
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Receive exception: " + exc.message.toString() )
            SourceStateData.setError(DataSource.DEXCOM_BYODA, exc.message.toString() )
        }
    }
}