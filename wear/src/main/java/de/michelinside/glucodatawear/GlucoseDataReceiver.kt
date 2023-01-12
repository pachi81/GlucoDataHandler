package de.michelinside.glucodatahandler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.math.RoundingMode
import java.text.DateFormat
import java.util.*
import kotlin.math.abs

object ReceiveData {
    var sensorID: String? = null
    var rawValue: Int = 0
    var glucose: Float = 0.0F
    var rate: Float = 0.0F
    var alarm: Int = 0
    var time: Long = 0
    var timeDiff: Long = 0
    var delta: Float = 0.0F

    fun getAsString(context: Context): String {
        if (sensorID == null)
            return context.getString(R.string.no_data)
        return context.getString(R.string.info_label_sensor_id) + ": " + sensorID + "\r\n" +
                context.getString(R.string.info_label_value) + ": " + glucose + " " + getUnit() + " " + getRateSymbol() + "\r\n" +
                context.getString(R.string.info_label_delta) + ": " + delta + " " + getUnit() + " " + context.getString(R.string.info_label_per_minute) + "\r\n" +
                context.getString(R.string.info_label_rate) + ": " + rate + "\r\n" +
                context.getString(R.string.info_label_timestamp) + ": " + GlucoseDataReceiver.dateformat.format(Date(time)) + "\r\n" +
                context.getString(R.string.info_label_timediff) + ": " + timeDiff + "ms\r\n" +
                context.getString(R.string.info_label_alarm) + ": " + alarm
    }

    fun getUnit(): String {
        if (rawValue!= glucose.toInt())
            return "mmol/l"
        return "mg/dl"
    }

    fun getRateSymbol(): Char {
        if (rate >= 3.5f) return '\u21C8'
        if (rate >= 2.0f) return '\u2191'
        if (rate >= 1.0f) return '\u2197'
        if (rate > -1.0f) return '\u2192'
        if (rate > -2.0f) return '\u2198'
        if (rate > -3.5f) return '\u2193'
        return if (java.lang.Float.isNaN(rate)) '?' else '\u21CA'
    }
}

class GlucoseDataReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val action = intent.action
            if (action != ACTION) {
                Log.e(LOG_ID, "action=" + action + " != " + ACTION)
                return
            }
            val extras = intent.extras
            Log.i(LOG_ID, "Glucodata received from sensor: " +  extras!!.getString(SERIAL) + " - value: " + extras.getFloat(GLUCOSECUSTOM).toString() + " - timestamp: " + dateformat.format(Date(
                extras.getLong(TIME)
            )))

            val timeDiff = extras.getLong(TIME) - ReceiveData.time
            if(timeDiff > 50000) // check for new value received
            {
                ReceiveData.sensorID = extras.getString(SERIAL) //Name of sensor
                ReceiveData.glucose = extras.getFloat(GLUCOSECUSTOM).toBigDecimal().setScale(1, RoundingMode.HALF_UP).toFloat() //Glucose value in unit in setting
                ReceiveData.rate = extras.getFloat(RATE) //Rate of change of glucose. See libre and dexcom label functions
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

                notifier?.newIntent()
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Exception: " + exc.message.toString() )
        }
    }

    companion object {
        private const val LOG_ID = "GlucoDataHandler.Receiver.Intent"
        private const val ACTION = "glucodata.Minute"
        private const val SERIAL = "glucodata.Minute.SerialNumber"
        private const val MGDL = "glucodata.Minute.mgdl"
        private const val GLUCOSECUSTOM = "glucodata.Minute.glucose"
        private const val RATE = "glucodata.Minute.Rate"
        private const val ALARM = "glucodata.Minute.Alarm"
        private const val TIME = "glucodata.Minute.Time"
        var dateformat = DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT)
        var notifier: NewIntentReceiver? = null
    }
}
