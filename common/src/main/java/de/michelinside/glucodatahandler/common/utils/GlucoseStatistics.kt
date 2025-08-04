package de.michelinside.glucodatahandler.common.utils

import android.util.Log
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.database.dbAccess


class StatisticsData(val days: Int) {
    private val LOG_ID = "GDH.StatisticsData"
    private val MIN_DATA_AGE_HOURS = 5
    var averageGlucose: Float = Float.NaN
        private set

    private var veryLow = 0
    private var low: Int = 0
    private var inRange = 0
    private var high = 0
    private var veryHigh = 0
    private var firstTime = 0L
    private val dataAgeHours: Long get() {
        if(firstTime > 0L) {
            return Utils.getElapsedTimeMinute(firstTime) / 60
        }
        return 0L
    }

    val needUpdate: Boolean get() {
        if(firstTime==0L)
            return true
        else if(count == 0 && dataAgeHours >= MIN_DATA_AGE_HOURS)
            return true
        return false
    }

    fun reset() {
        firstTime = 0L
    }

    val hasData: Boolean get() {
        return dataAgeHours >= MIN_DATA_AGE_HOURS && count > 50 && averageGlucose > 0F // at least needed to calculate average values
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

    fun update() {
        try {
            val minTime = System.currentTimeMillis() - (days*24*60*60*1000)
            firstTime = dbAccess.getFirstTimestamp()
            Log.d(LOG_ID, "update statistics - firstTime: ${Utils.getUiTimeStamp(firstTime)}, days: $days, dataAgeHours: $dataAgeHours")
            if(dataAgeHours >= MIN_DATA_AGE_HOURS) {
                averageGlucose = dbAccess.getAverageValue(minTime)
                veryLow = dbAccess.getValuesInRangeCount(minTime, 0, ReceiveData.lowRaw.toInt())
                low = dbAccess.getValuesInRangeCount(minTime, ReceiveData.lowRaw.toInt()+1, ReceiveData.targetMinRaw.toInt()-1)
                inRange = dbAccess.getValuesInRangeCount(minTime, ReceiveData.targetMinRaw.toInt(), ReceiveData.targetMaxRaw.toInt())
                high = dbAccess.getValuesInRangeCount(minTime, ReceiveData.targetMaxRaw.toInt()+1, ReceiveData.highRaw.toInt()-1)
                veryHigh = dbAccess.getValuesInRangeCount(minTime, ReceiveData.highRaw.toInt(), Int.MAX_VALUE)
                Log.i(LOG_ID, "statistics updated for $days days: average: $averageGlucose, count: $count, veryLow: $veryLow, low: $low, inRange: $inRange, high: $high, veryHigh: $veryHigh")
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "update exception: $exc")
        }
    }
}


object GlucoseStatistics {
    private val LOG_ID = "GDH.Statistics"
    private var lastUpdate = 0L
    val statData1d = StatisticsData(1)
    val statData7d = StatisticsData(7)

    fun reset() {
        lastUpdate = 0
        statData1d.reset()
        statData7d.reset()
    }

    fun update() {
        try {
            if(Utils.getElapsedTimeMinute(lastUpdate) >= 30 || statData1d.needUpdate || statData7d.needUpdate) {
                Log.d(LOG_ID, "update statistics - lastUpdate: ${Utils.getUiTimeStamp(lastUpdate)}, needUpdate: ${statData1d.needUpdate || statData7d.needUpdate}")
                // recalculate statistics data
                lastUpdate = System.currentTimeMillis()
                statData1d.update()
                statData7d.update()
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "update exception: $exc")
        }
    }

    val hasStatistics: Boolean get() {
        return statData1d.hasData || statData7d.hasData
    }
}