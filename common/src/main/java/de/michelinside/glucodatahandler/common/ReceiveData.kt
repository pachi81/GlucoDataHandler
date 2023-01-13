package de.michelinside.glucodatahandler.common

import android.content.Context
import android.util.Log
import java.text.DateFormat
import java.util.*

interface ReceiveDataInterface {
    fun OnReceiveData(context: Context)
}

object ReceiveData {
    private val LOG_ID = "GlucoDataHandler.ReceiveData"
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

    fun getAsString(context: Context): String {
        if (sensorID == null)
            return context.getString(R.string.no_data)
        return context.getString(R.string.info_label_sensor_id) + ": " + sensorID + "\r\n" +
                context.getString(R.string.info_label_value) + ": " + glucose + " " + getUnit() + " " + getRateSymbol() + "\r\n" +
                context.getString(R.string.info_label_delta) + ": " + delta + " " + getUnit() + " " + context.getString(R.string.info_label_per_minute) + "\r\n" +
                context.getString(R.string.info_label_rate) + ": " + rate + " (" + rateLabel + ")\r\n" +
                context.getString(R.string.info_label_timestamp) + ": " + dateformat.format(Date(time)) + "\r\n" +
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

    fun getDexcomLabel(): String {
        if (rate >= 3.5f) return "DoubleUp"
        if (rate >= 2.0f) return "SingleUp"
        if (rate >= 1.0f) return "FortyFiveUp"
        if (rate > -1.0f) return "Flat"
        if (rate > -2.0f) return "FortyFiveDown"
        if (rate > -3.5f) return "SingleDown"
        return if (java.lang.Float.isNaN(rate)) "" else "DoubleDown"
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

    fun notify(context: Context)
    {
        Log.d(LOG_ID, "Sending new data to " + notifiers.size.toString() + " notifier(s).")
        notifiers.forEach{ it.OnReceiveData(context) }
    }
}