package de.michelinside.glucodatahandler.common.utils

import android.content.Context
import android.content.Intent
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.R
import de.michelinside.glucodatahandler.common.ReceiveData
import kotlin.math.abs
import kotlin.random.Random

object GlucoDataUtils {
    //private val LOG_ID = "GDH.Utils.GlucoData"

    fun isGlucoseValid(value: Float): Boolean {
        var mgdl = value
        if (isMmolValue(mgdl))
            mgdl = mmolToMg(mgdl)
        return isGlucoseValid(mgdl.toInt())
    }
    fun isGlucoseValid(mgdl: Int): Boolean {
        return mgdl >= Constants.GLUCOSE_MIN_VALUE.toFloat() && mgdl <= Constants.GLUCOSE_MAX_VALUE.toFloat()
    }

    fun isMmolValue(value: Float): Boolean = value < Constants.GLUCOSE_MIN_VALUE.toFloat()

    fun mgToMmol(value: Float): Float {
        return Utils.round(value / Constants.GLUCOSE_CONVERSION_FACTOR, if(abs(value) > 1.7F) 1 else 2)
    }

    fun mmolToMg(value: Float): Float {
        return Utils.round(value * Constants.GLUCOSE_CONVERSION_FACTOR, 0)
    }

    fun deltaToString(delta: Float, withUnit: Boolean = false): String {
        if(delta.isNaN())
            return "--"
        var deltaVal = ""
        if (delta > 0)
            deltaVal += "+"
        deltaVal += if( !ReceiveData.isMmol && delta.toDouble() == Math.floor(delta.toDouble()))
            delta.toInt().toString()
        else
            delta.toString()
        if(withUnit)
            deltaVal += " " + ReceiveData.getUnit()
        return deltaVal
    }

    fun getRateSymbol(rate: Float): Char {
        if(ReceiveData.isObsoleteShort() || java.lang.Float.isNaN(
                rate
            ))
            return '?'
        if (rate >= 3.0f) return '⇈'
        if (rate >= 2.0f) return '↑'
        if (rate >= 1.0f) return '↗'
        if (rate > -1.0f) return '→'
        if (rate > -2.0f) return '↘'
        if (rate > -3.0f) return '↓'
        return '⇊'
    }

    fun getDexcomLabel(rate: Float): String {
        if (rate >= 3.0f) return "DoubleUp"
        if (rate >= 2.0f) return "SingleUp"
        if (rate >= 1.0f) return "FortyFiveUp"
        if (rate > -1.0f) return "Flat"
        if (rate > -2.0f) return "FortyFiveDown"
        if (rate > -3.0f) return "SingleDown"
        return if (java.lang.Float.isNaN(rate)) "" else "DoubleDown"
    }

    fun getRateFromLabel(trendLabel: String?): Float {
        if(trendLabel==null)
            return Float.NaN
        return when(trendLabel) {
            "DoubleDown" -> -4F
            "SingleDown" -> -2F
            "FortyFiveDown" -> -1F
            "Flat" -> 0F
            "FortyFiveUp" -> 1F
            "SingleUp" -> 2F
            "DoubleUp" -> 4f
            else -> Float.NaN
        }
    }

    fun getRateLabel(context: Context, rate: Float): String {
        if (rate >= 3.0f) return context.getString(R.string.rate_double_up)
        if (rate >= 2.0f) return context.getString(R.string.rate_single_up)
        if (rate >= 1.0f) return context.getString(R.string.rate_forty_five_up)
        if (rate > -1.0f) return context.getString(R.string.rate_flat)
        if (rate > -2.0f) return context.getString(R.string.rate_forty_five_down)
        if (rate > -3.0f) return context.getString(R.string.rate_single_down)
        return if (rate.isNaN()) context.getString(R.string.not_available) else context.getString(R.string.rate_double_down)
    }

    fun getRateDegrees(rate: Float): Int {
        if (rate.isNaN())
            return 0
        val degree = Utils.round(maxOf(-2F, minOf(2F, rate)) / 2F * 90F, 0).toInt()
        if(degree in -14 .. 14)
            return 0  // start with 10 degree
        return (degree/5)*5  // series of 5
    }

    private var rateDelta = 0.1F
    private var rawDelta = 5
    fun getDummyGlucodataIntent(random: Boolean = true) : Intent {
        val useMmol = ReceiveData.isMmol
        val first = ReceiveData.time == 0L
        ReceiveData.time = System.currentTimeMillis()-60000
        val time =  System.currentTimeMillis()
        val intent = Intent(Constants.GLUCODATA_BROADCAST_ACTION)
        var raw: Int
        var glucose: Float
        var rate: Float
        if (random) {
            raw = Random.nextInt(40, 400)
            glucose = if(useMmol) mgToMmol(raw.toFloat()) else raw.toFloat()
            rate = Random.nextFloat() + Random.nextInt(-4, 4).toFloat()
        } else {
            if ((ReceiveData.rawValue >= 200 && rawDelta > 0) || (ReceiveData.rawValue <= 40 && rawDelta < 0)) {
                rawDelta *= -1
            }
            raw =
                if (first || ReceiveData.rawValue == 400) Constants.GLUCOSE_MIN_VALUE else ReceiveData.rawValue + rawDelta
            if (raw < Constants.GLUCOSE_MIN_VALUE)
                raw = Constants.GLUCOSE_MIN_VALUE
            glucose = if (useMmol) mgToMmol(raw.toFloat()) else raw.toFloat()
            if (useMmol && glucose == ReceiveData.glucose) {
                raw += 1
                glucose = mgToMmol(raw.toFloat())
            }
            if (ReceiveData.rate >= 3.5F) {
                rateDelta = -0.1F
                rate = 2F
            } else if (ReceiveData.rate <= -3.5F) {
                rateDelta = 0.1F
                rate = -2F
            } else {
                rate = if (first) -3.5F else ReceiveData.rate + rateDelta
            }
            if (rate > 2F && rateDelta > 0)
                rate = 3.5F
            else if (rate < -2F && rateDelta < 0)
                rate = -3.5F

        }
        intent.putExtra(ReceiveData.SERIAL, "WUSEL_DUSEL")
        intent.putExtra(ReceiveData.MGDL, raw)
        intent.putExtra(ReceiveData.GLUCOSECUSTOM, glucose)
        intent.putExtra(ReceiveData.RATE, Utils.round(rate, 2))
        intent.putExtra(ReceiveData.TIME, time)
        intent.putExtra(ReceiveData.ALARM, if (raw <= 70) 7 else if (raw >= 250) 6 else 0)
        intent.putExtra(ReceiveData.IOB, Utils.round(rate, 2))
        intent.putExtra(ReceiveData.COB, Utils.round(rate, 2)*10F+Utils.round(rate, 2))
        return intent
    }
}