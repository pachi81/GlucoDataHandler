package de.michelinside.glucodatahandler.common.utils

import android.util.Log
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.database.dbAccess

object GlucoseStatistics {
    private val LOG_ID = "GDH.GlucoseStatistics"
    private var lastUpdate = 0L
    var averageGlucose: Float = Float.NaN
        private set

    private var veryLow = 0
    private var low: Int = 0
    private var inRange = 0
    private var high = 0
    private var veryHigh = 0

    val hasData: Boolean get() {
        return count > 300 // at least needed to calculate average values
    }

    val count: Int get() {
        return veryLow + low + inRange + high + veryHigh
    }
    val percentVeryLow: Float get() {
        return (veryLow.toFloat() / count.toFloat()) * 100f
    }
    val percentLow: Float get() {
        return (low.toFloat() / count.toFloat()) * 100f
    }
    val percentInRange: Float get() {
        return (inRange.toFloat() / count.toFloat()) * 100f
    }
    val percentHigh: Float get() {
        return (high.toFloat() / count.toFloat()) * 100f
    }
    val percentVeryHigh: Float get() {
        return (veryHigh.toFloat() / count.toFloat()) * 100f
    }

    fun reset() {
        lastUpdate = 0
    }

    fun update() {
        if(Utils.getElapsedTimeMinute(lastUpdate) >= 30 || count == 0) {
            Log.d(LOG_ID, "update statistics")
            // recalculate statistics data
            lastUpdate = System.currentTimeMillis()
            val minTime = System.currentTimeMillis() - (7*24*60*60*1000)
            averageGlucose = dbAccess.getAverageValue(minTime)
            veryLow = dbAccess.getValuesInRangeCount(minTime, 0, ReceiveData.lowRaw.toInt())
            low = dbAccess.getValuesInRangeCount(minTime, ReceiveData.lowRaw.toInt()+1, ReceiveData.targetMinRaw.toInt()-1)
            inRange = dbAccess.getValuesInRangeCount(minTime, ReceiveData.targetMinRaw.toInt(), ReceiveData.targetMaxRaw.toInt())
            high = dbAccess.getValuesInRangeCount(minTime, ReceiveData.targetMaxRaw.toInt()+1, ReceiveData.highRaw.toInt()-1)
            veryHigh = dbAccess.getValuesInRangeCount(minTime, ReceiveData.highRaw.toInt(), Int.MAX_VALUE)
            Log.i(LOG_ID, "statistics updated: count: $count, average: $averageGlucose")

        }
    }
}