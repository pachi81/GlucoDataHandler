package de.michelinside.glucodatahandler.common

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.math.RoundingMode
import java.util.*
import kotlin.math.abs

open class GlucoseDataReceiverBase : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val action = intent.action
            if (action != ACTION) {
                Log.e(LOG_ID, "action=" + action + " != " + ACTION)
                return
            }
            val extras = intent.extras
            Log.i(LOG_ID, "Glucodata received from sensor: " +  extras!!.getString(SERIAL) + " - value: " + extras.getFloat(GLUCOSECUSTOM).toString() + " - timestamp: " + ReceiveData.dateformat.format(
                Date(
                extras.getLong(TIME)
            )
            ))

            val timeDiff = extras.getLong(TIME) - ReceiveData.time
            if(timeDiff > 50000) // check for new value received
            {
                ReceiveData.sensorID = extras.getString(SERIAL) //Name of sensor
                ReceiveData.glucose = extras.getFloat(GLUCOSECUSTOM).toBigDecimal().setScale(1, RoundingMode.HALF_UP).toFloat() //Glucose value in unit in setting
                ReceiveData.rate = extras.getFloat(RATE) //Rate of change of glucose. See libre and dexcom label functions
                ReceiveData.rateLabel = getRateLabel(context)
                ReceiveData.alarm = extras.getInt(ALARM) //See showalarm.
                if (ReceiveData.time > 0) {
                    ReceiveData.timeDiff = timeDiff
                    val timeDiffMinute = (ReceiveData.timeDiff.toFloat()/60000).toBigDecimal().setScale(0, RoundingMode.HALF_UP).toInt()
                    if(timeDiffMinute > 0) {
                        ReceiveData.delta =
                            ((extras.getInt(MGDL) - ReceiveData.rawValue) / timeDiffMinute).toFloat()
                    } else {
                        Log.w(LOG_ID, "Timediff is less than a minute: " + ReceiveData.timeDiff + "ms")
                    }
                    val newRaw = extras.getInt(MGDL)
                    if(newRaw!=ReceiveData.glucose.toInt())  // mmol/l
                    {
                        val scale = if (abs(ReceiveData.delta) > 1.0F) 1 else 2
                        ReceiveData.delta = (ReceiveData.delta / 18.0182F).toBigDecimal().setScale( scale, RoundingMode.HALF_UP).toFloat()
                    }
                }
                ReceiveData.rawValue = extras.getInt(MGDL)
                ReceiveData.time = extras.getLong(TIME) //time in mmsec

                notify(context)

            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Receive exception: " + exc.message.toString() )
        }
    }

    open fun notify(context: Context)
    {
        try {
            ReceiveData.notify(context)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Notify exception: " + exc.message.toString() )
        }
    }

    fun getRateLabel(context: Context): String {
        if (ReceiveData.rate >= 3.5f) return context.getString(R.string.rate_double_up)
        if (ReceiveData.rate >= 2.0f) return context.getString(R.string.rate_single_up)
        if (ReceiveData.rate >= 1.0f) return context.getString(R.string.rate_forty_five_up)
        if (ReceiveData.rate > -1.0f) return context.getString(R.string.rate_flat)
        if (ReceiveData.rate > -2.0f) return context.getString(R.string.rate_forty_five_down)
        if (ReceiveData.rate > -3.5f) return context.getString(R.string.rate_single_down)
        return if (java.lang.Float.isNaN(ReceiveData.rate)) "" else context.getString(R.string.rate_double_down)
    }

    companion object {
        private const val LOG_ID = "GlucoDataHandler.Receiver.Base"
        private const val ACTION = "glucodata.Minute"
        private const val SERIAL = "glucodata.Minute.SerialNumber"
        private const val MGDL = "glucodata.Minute.mgdl"
        private const val GLUCOSECUSTOM = "glucodata.Minute.glucose"
        private const val RATE = "glucodata.Minute.Rate"
        private const val ALARM = "glucodata.Minute.Alarm"
        private const val TIME = "glucodata.Minute.Time"
    }
}