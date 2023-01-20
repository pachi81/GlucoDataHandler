package de.michelinside.glucodatahandler.common

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import com.google.android.gms.wearable.CapabilityInfo
import java.math.RoundingMode
import java.text.DateFormat
import java.util.*
import kotlin.math.abs

interface ReceiveDataInterface {
    fun OnReceiveData(context: Context, dataSource: ReceiveDataSource, extras: Bundle?)
}

enum class ReceiveDataSource {
    BROADCAST,
    MESSAGECLIENT,
    CAPILITY_INFO
}

object ReceiveData {
    private const val LOG_ID = "GlucoDataHandler.ReceiveData"
    private const val SERIAL = "glucodata.Minute.SerialNumber"
    private const val MGDL = "glucodata.Minute.mgdl"
    private const val GLUCOSECUSTOM = "glucodata.Minute.glucose"
    private const val RATE = "glucodata.Minute.Rate"
    private const val ALARM = "glucodata.Minute.Alarm"
    private const val TIME = "glucodata.Minute.Time"

    init {
        Log.d(LOG_ID, "init called")
    }
    var sensorID: String? = null
    var rawValue: Int = 0
    var glucose: Float = 0.0F
    var rate: Float = 0.0F
    var alarm: Int = 0
    var time: Long = 0
    var timeDiff: Long = 0
    var delta: Float = 0.0F
    var rateLabel: String? = null
    var dateformat = DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT)
    var source: ReceiveDataSource = ReceiveDataSource.BROADCAST
    var capabilityInfo: CapabilityInfo? = null

    fun getAsString(context: Context): String {
        if (sensorID == null)
            return context.getString(R.string.no_data)
        return context.getString(R.string.info_label_sensor_id) + ": " + sensorID + "\r\n" +
                context.getString(R.string.info_label_value) + ": " + glucose + " " + getRateSymbol() + "\r\n" +
                context.getString(R.string.info_label_delta) + ": " + delta + " " + getUnit() + " " + context.getString(R.string.info_label_per_minute) + "\r\n" +
                context.getString(R.string.info_label_rate) + ": " + rate + " (" + rateLabel + ")\r\n" +
                context.getString(R.string.info_label_timestamp) + ": " + dateformat.format(Date(time)) + "\r\n" +
                context.getString(R.string.info_label_timediff) + ": " + timeDiff + "ms\r\n" +
                context.getString(R.string.info_label_alarm) + ": " + alarm + "\r\n" +
                context.getString(R.string.info_label_source) + ": " + source
    }

    fun isMmol(): Boolean = rawValue != glucose.toInt()

    fun isObsolete(): Boolean = (System.currentTimeMillis()- time) >= (600 * 1000)

    fun getClucoseAsString(): String {
        if(isObsolete())
            return "---"
        if (isMmol())
            return glucose.toString()
        return rawValue.toString()
    }

    fun getDeltaAsString(): String {
        if(isObsolete())
            return "???"
        var deltaVal = ""
        if (delta > 0)
            deltaVal += "+"
        if( !isMmol() && delta.toDouble() == Math.floor(delta.toDouble()) )
            deltaVal += delta.toInt().toString()
        else
            deltaVal += delta.toString()
        return deltaVal
    }

    fun getUnit(): String {
        if (isMmol())
            return "mmol/l"
        return "mg/dl"
    }

    fun getClucoseColor(): Int {
        if(isObsolete())
            return Color.GRAY
        if(alarm!=0)
            return Color.RED
        if(isMmol()) {
            if(glucose < 5.0 || glucose > 9.2 )
                return Color.YELLOW
        } else {
            if(glucose < 90 || glucose > 165 )
                return Color.YELLOW
        }
        return Color.GREEN
    }

    fun getRateSymbol(): Char {
        if(isObsolete())
            return '?'
        if (rate >= 3.5f) return '\u21C8'
        if (rate >= 2.0f) return '\u2191'
        if (rate >= 1.0f) return '\u2197'
        if (rate > -1.0f) return '\u2192'
        if (rate > -2.0f) return '\u2198'
        if (rate > -3.5f) return '\u2193'
        return if (java.lang.Float.isNaN(rate)) '?' else '\u21CA'
    }

    fun getDexcomLabel(): String {
        if (rate >= 3.5f) return "DoubleUp"
        if (rate >= 2.0f) return "SingleUp"
        if (rate >= 1.0f) return "FortyFiveUp"
        if (rate > -1.0f) return "Flat"
        if (rate > -2.0f) return "FortyFiveDown"
        if (rate > -3.5f) return "SingleDown"
        return if (java.lang.Float.isNaN(rate)) "" else "DoubleDown"
    }

    fun getRateLabel(context: Context): String {
        if (rate >= 3.5f) return context.getString(R.string.rate_double_up)
        if (rate >= 2.0f) return context.getString(R.string.rate_single_up)
        if (rate >= 1.0f) return context.getString(R.string.rate_forty_five_up)
        if (rate > -1.0f) return context.getString(R.string.rate_flat)
        if (rate > -2.0f) return context.getString(R.string.rate_forty_five_down)
        if (rate > -3.5f) return context.getString(R.string.rate_single_down)
        return if (java.lang.Float.isNaN(rate)) "" else context.getString(R.string.rate_double_down)
    }

    fun getTimeDiffMinute(): Long {
        return (timeDiff.toFloat()/60000).toBigDecimal().setScale(0, RoundingMode.HALF_UP).toLong()
    }

    private var notifiers = mutableSetOf<ReceiveDataInterface>()
    fun addNotifier(notifier: ReceiveDataInterface)
    {
        Log.d(LOG_ID, "add notifier " + notifier.toString() )
        notifiers.add(notifier)
        Log.d(LOG_ID, "notifier size: " + notifiers.size.toString() )
    }
    fun remNotifier(notifier: ReceiveDataInterface)
    {
        Log.d(LOG_ID, "rem notifier " + notifier.toString() )
        notifiers.remove(notifier)
        Log.d(LOG_ID, "notifier size: " + notifiers.size.toString() )
    }

    fun notify(context: Context, dataSource: ReceiveDataSource, extras: Bundle?)
    {
        Log.d(LOG_ID, "Sending new data to " + notifiers.size.toString() + " notifier(s).")
        notifiers.forEach{
            try {
                it.OnReceiveData(context, dataSource, extras)
            } catch (exc: Exception) {
                Log.e(LOG_ID, "OnReceiveData exception: " + exc.message.toString() )
            }
        }
    }

    fun handleIntent(context: Context, dataSource: ReceiveDataSource, extras: Bundle?) : Boolean
    {
        if (extras == null || extras.isEmpty) {
            return false
        }
        try {
            Log.i(
                LOG_ID, "Glucodata received from " + dataSource.toString() + " - sensor: " +  extras.getString(
                    SERIAL
                ) + " - value: " + extras.getFloat(GLUCOSECUSTOM).toString() + " - timestamp: " + dateformat.format(
                Date(
                    extras.getLong(TIME)
                )
            ))

            val curTimeDiff = extras.getLong(TIME) - time
            if(curTimeDiff > 50000) // check for new value received
            {
                source = dataSource
                sensorID = extras.getString(SERIAL) //Name of sensor
                glucose = extras.getFloat(GLUCOSECUSTOM).toBigDecimal().setScale(1, RoundingMode.HALF_UP).toFloat() //Glucose value in unit in setting
                rate = extras.getFloat(RATE) //Rate of change of glucose. See libre and dexcom label functions
                rateLabel = getRateLabel(context)
                alarm = extras.getInt(ALARM) //See showalarm.
                if (time > 0) {
                    timeDiff = curTimeDiff
                    val timeDiffMinute = getTimeDiffMinute()
                    if(timeDiffMinute > 0) {
                        delta =
                            ((extras.getInt(MGDL) - rawValue) / timeDiffMinute).toFloat()
                    } else {
                        Log.w(LOG_ID, "Timediff is less than a minute: " + timeDiff + "ms")
                    }
                    val newRaw = extras.getInt(MGDL)
                    if(newRaw!=glucose.toInt())  // mmol/l
                    {
                        val scale = if (abs(delta) > 1.0F) 1 else 2
                        delta = (delta / 18.0182F).toBigDecimal().setScale( scale, RoundingMode.HALF_UP).toFloat()
                    }
                }
                rawValue = extras.getInt(MGDL)
                time = extras.getLong(TIME) //time in mmsec

                notify(context, source, extras)
                return true
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Receive exception: " + exc.message.toString() )
        }
        return false
    }


}