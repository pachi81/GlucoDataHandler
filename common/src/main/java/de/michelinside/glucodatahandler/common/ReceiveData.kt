package de.michelinside.glucodatahandler.common

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Icon
import android.os.Bundle
import android.util.Log
import com.google.android.gms.wearable.CapabilityInfo
import java.text.DateFormat
import java.util.*
import kotlin.math.abs


interface ReceiveDataInterface {
    fun OnReceiveData(context: Context, dataSource: ReceiveDataSource, extras: Bundle?)
}

enum class ReceiveDataSource(private val resId: Int) {
    BROADCAST(R.string.source_broadcast),
    MESSAGECLIENT(R.string.source_message_client),
    CAPILITY_INFO(R.string.source_capility_info);

    fun getResId(): Int {
        return resId
    }
}

object ReceiveData {
    private const val LOG_ID = "GlucoDataHandler.ReceiveData"
    const val SERIAL = "glucodata.Minute.SerialNumber"
    const val MGDL = "glucodata.Minute.mgdl"
    const val GLUCOSECUSTOM = "glucodata.Minute.glucose"
    const val RATE = "glucodata.Minute.Rate"
    const val ALARM = "glucodata.Minute.Alarm"
    const val TIME = "glucodata.Minute.Time"

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
    var dateformat: DateFormat = DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT)
    var source: ReceiveDataSource = ReceiveDataSource.BROADCAST
    var capabilityInfo: CapabilityInfo? = null
    var targetMin = 90F
    var targetMax = 165F
    var curExtraBundle: Bundle? = null

    fun getAsString(context: Context, withValue: Boolean = true): String {
        if (sensorID == null)
            return context.getString(R.string.no_data)
        return (if (withValue) context.getString(R.string.info_label_value) + ": " + glucose + " " + getRateSymbol() + "\r\n" else "" ) +
                context.getString(R.string.info_label_sensor_id) + ": " + sensorID + "\r\n" +
                context.getString(R.string.info_label_delta) + ": " + delta + " " + getUnit() + " " + context.getString(R.string.info_label_per_minute) + "\r\n" +
                context.getString(R.string.info_label_rate) + ": " + rate + " (" + rateLabel + ")\r\n" +
                context.getString(R.string.info_label_timestamp) + ": " + dateformat.format(Date(time)) + "\r\n" +
                context.getString(R.string.info_label_timediff) + ": " + timeDiff + "ms\r\n" +
                context.getString(R.string.info_label_alarm) + ": " + alarm + "\r\n" +
                context.getString(R.string.info_label_source) + ": " + context.getString(source.getResId())
    }

    fun isMmol(): Boolean = rawValue != glucose.toInt()

    fun isObsolete(timeoutSec: Int = 600): Boolean = (System.currentTimeMillis()- time) >= (timeoutSec * 1000)

    fun getClucoseAsString(): String {
        if(isObsolete())
            return "---"
        if (isMmol())
            return glucose.toString()
        return rawValue.toString()
    }

    fun getDeltaAsString(): String {
        if(isObsolete(300))
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

    fun getClucoseColor(monoChrome: Boolean = false): Int {
        if(isObsolete(300))
            return Color.GRAY
        if (monoChrome)
            return Color.WHITE
        if(alarm!=0)
            return Color.RED
        if(glucose < targetMin || glucose > targetMax )
            return Color.YELLOW

        return Color.GREEN
    }

    fun getRateSymbol(): Char {
        if(isObsolete(300))
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

    fun getArrowIconRes(): Int {
        if(isObsolete(300) || java.lang.Float.isNaN(rate))
            return R.drawable.icon_question
        if (rate >= 3.0f) return R.drawable.arrow_double_up
        if (rate >= 2.0f) return R.drawable.arrow_up_90
        if (rate >= 1.66f) return R.drawable.arrow_up_75
        if (rate >= 1.33f) return R.drawable.arrow_up_60
        if (rate >= 1.0f) return R.drawable.arrow_up_45
        if (rate >= 0.66f) return R.drawable.arrow_up_30
        if (rate >= 0.33f) return R.drawable.arrow_up_15
        if (rate > -0.33f) return R.drawable.arrow_right
        if (rate > -0.66f) return R.drawable.arrow_down_15
        if (rate > -1.0f) return R.drawable.arrow_down_30
        if (rate > -1.33f) return R.drawable.arrow_down_45
        if (rate > -1.66f) return R.drawable.arrow_down_60
        if (rate > -2.0f) return R.drawable.arrow_down_75
        if (rate > -3.0f) return R.drawable.arrow_down_90
        return R.drawable.arrow_double_down
    }

    fun getArrowIcon(context: Context): Icon {
        val icon = Icon.createWithResource(context, getArrowIconRes())
        icon.setTint(getClucoseColor())
        icon.setTintMode(PorterDuff.Mode.SRC_IN)
        return icon
    }

    fun getTimeDiffMinute(): Long {
        return Utils.round(timeDiff.toFloat()/60000, 0).toLong()
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
                Log.d(LOG_ID, "Sending new data to " + it.toString())
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
                LOG_ID, "Glucodata received from " + dataSource.toString() + ": " +
                        extras.toString() +
                        " - timestamp: " + dateformat.format(Date(extras.getLong(TIME)))
            )

            val curTimeDiff = extras.getLong(TIME) - time
            if(curTimeDiff > 50000) // check for new value received
            {
                curExtraBundle = extras
                source = dataSource
                sensorID = extras.getString(SERIAL) //Name of sensor
                glucose = Utils.round(extras.getFloat(GLUCOSECUSTOM), 1) //Glucose value in unit in setting
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
                        delta = Utils.mgToMmol(delta, if (abs(delta) > 1.0F) 1 else 2)

                        if(targetMin >= Constants.GLUCOSE_MIN_VALUE.toFloat())
                        {
                            targetMin = Utils.mgToMmol(targetMin)
                            targetMax = Utils.mgToMmol(targetMax)
                            Log.i(LOG_ID, "min/max changed from mg/dl to mmol/l: " + targetMin.toString() + "/" + targetMax.toString())
                        }
                    } else if (targetMin < 20F) {
                        targetMin = Utils.mmolToMg(targetMin)
                        targetMax = Utils.mmolToMg(targetMax)
                        Log.i(LOG_ID, "min/max changed from mmol/l to mg/dl: " + targetMin.toString() + "/" + targetMax.toString())
                    }
                }
                rawValue = extras.getInt(MGDL)
                time = extras.getLong(TIME) //time in mmsec

                notify(context, source, extras)
                return true
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Receive exception: " + exc.toString() + "\n" + exc.stackTraceToString() )
        }
        return false
    }


}