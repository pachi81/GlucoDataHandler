package de.michelinside.glucodatahandler.common.receiver

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.SourceState
import de.michelinside.glucodatahandler.common.SourceStateData
import de.michelinside.glucodatahandler.common.notifier.DataSource
import de.michelinside.glucodatahandler.common.utils.Utils
import org.json.JSONObject


/* data:
    * {
    *   "realTimeGlucose": {
        *     "timestamp": 1599077082859,  // start of the sensor
        *     "index": 20060,
        *     "raw": 53
        *   },
    *   "historicGlucose": [{
        *     "timestamp": 1600252482859,
        *     "index": 19590,
        *     "raw": 45
        *   }, {
        *     "timestamp": 1600253382859,
        *     "index": 19605,
        *     "raw": 32
        *   }]
    * }
*/

open class DiaboxReceiver: NamedBroadcastReceiver() {
    private val LOG_ID = "GDH.DiaboxReceiver"
    private val JSON_DATA = "data"
    private val REALTIME_GLUCOSE = "realTimeGlucose"
    //private val TIMESTAMP = "timestamp"
    private val GLUCOSE = "raw"

    override fun getName(): String {
        return LOG_ID
    }

    override fun onReceiveData(context: Context, intent: Intent) {
        try {
            val extras = intent.extras
            Log.i(LOG_ID, "onReceive called for " + intent.action + ": " + Utils.dumpBundle(extras))
            if (extras != null) {
                val data = extras.getString(JSON_DATA)
                if (data != null) {
                    val dataObj = JSONObject(data)
                    val realtimeObj = dataObj.optJSONObject(REALTIME_GLUCOSE)
                    if (realtimeObj != null) {
                        val now = System.currentTimeMillis()    // use this timestamp, as the timestamp of the data is the start time of the sensor
                        val glucoseValue = realtimeObj.optInt(GLUCOSE, 0)
                        if (glucoseValue > 0) {
                            val glucoExtras = Bundle()
                            glucoExtras.putLong(ReceiveData.TIME, now)
                            glucoExtras.putInt(ReceiveData.MGDL, glucoseValue)
                            glucoExtras.putFloat(ReceiveData.RATE, Float.NaN)
                            ReceiveData.handleIntent(context, DataSource.DIABOX, glucoExtras)
                            SourceStateData.setState(DataSource.DIABOX, SourceState.NONE)
                        } else {
                            Log.w(LOG_ID, "No glucose value: " + Utils.dumpBundle(intent.extras))
                            SourceStateData.setError(DataSource.DIABOX, "Missing glucose value!")
                        }
                    } else {
                        Log.w(LOG_ID, "No realTimeGlucose: " + Utils.dumpBundle(intent.extras))
                        SourceStateData.setError(DataSource.DIABOX, "Missing data!")
                    }
                } else {
                    Log.w(LOG_ID, "No data: " + Utils.dumpBundle(intent.extras))
                    SourceStateData.setError(DataSource.DIABOX, "Missing data!")
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Receive exception: " + exc.message.toString() )
            SourceStateData.setError(DataSource.DIABOX, exc.message.toString() )
        }
    }
}