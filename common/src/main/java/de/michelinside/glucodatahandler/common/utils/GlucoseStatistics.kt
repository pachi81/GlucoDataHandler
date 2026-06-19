package de.michelinside.glucodatahandler.common.utils

import android.content.Context
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.R
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.database.dbAccess
import de.michelinside.glucodatahandler.common.notification.AlarmType


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
    val gmiPercent: Float get() {
        return 3.31F + (0.02392F*averageGlucose)
    }
    val gmiMmolPerMol: Int get() {
        return Utils.round(12.71F + (0.2615F*averageGlucose), 0).toInt()
    }
    val hba1cPercent: Float get() {
        return (averageGlucose + 46.7F) / 28.7F
    }
    val hba1cMmolPerMol: Int get() {
        return Utils.round((averageGlucose * 0.3808F) - 10.615F, 0).toInt()
    }

    fun update(standardStats: Boolean, useTITR: Boolean) {
        try {
            /* standard statistics:
                very high: > 250
                high: 141/181 - 250
                in range: 70 - 140/180 (depending of using TITR)
                low: 54 - 69
                very low: < 54
            */
            val minTime = System.currentTimeMillis() - (days*24*60*60*1000)
            val inRangeUpper = if(useTITR) 140 else 180
            firstTime = dbAccess.getFirstTimestamp()
            Log.d(LOG_ID, "update statistics - firstTime: ${Utils.getUiTimeStamp(firstTime)}, days: $days, dataAgeHours: $dataAgeHours")
            if(dataAgeHours >= MIN_DATA_AGE_HOURS) {
                averageGlucose = dbAccess.getAverageValue(minTime)
                veryLow = dbAccess.getValuesInRangeCount(minTime, 0, if(standardStats) 53 else ReceiveData.lowRaw.toInt())
                low = dbAccess.getValuesInRangeCount(minTime, if(standardStats) 54 else ReceiveData.lowRaw.toInt()+1, if(standardStats) 69 else ReceiveData.targetMinRaw.toInt()-1)
                inRange = dbAccess.getValuesInRangeCount(minTime, if(standardStats) 70 else ReceiveData.targetMinRaw.toInt(), if(standardStats) inRangeUpper else ReceiveData.targetMaxRaw.toInt())
                high = dbAccess.getValuesInRangeCount(minTime, if(standardStats) inRangeUpper+1 else ReceiveData.targetMaxRaw.toInt()+1, if(standardStats) 250 else ReceiveData.highRaw.toInt()-1)
                veryHigh = dbAccess.getValuesInRangeCount(minTime, if(standardStats) 251 else ReceiveData.highRaw.toInt(), Int.MAX_VALUE)
                Log.i(LOG_ID, "statistics (standard: $standardStats - upper range: $inRangeUpper) updated for $days days: average: $averageGlucose, count: $count, veryLow: $veryLow, low: $low, inRange: $inRange, high: $high, veryHigh: $veryHigh")
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "update exception: $exc")
        }
    }
}


object GlucoseStatistics {
    private val LOG_ID = "GDH.Statistics"
    private var lastUpdate = 0L
    private var useStandard = true
    private var useTITR = false
    val statData1d = StatisticsData(1)
    val statData7d = StatisticsData(7)

    fun reset() {
        lastUpdate = 0
        statData1d.reset()
        statData7d.reset()
    }


    fun update() {
        try {
            val newUseStandard = GlucoDataService.sharedPref?.getBoolean(Constants.SHARED_PREF_STANDARD_STATISTICS, true)?: true
            val newUseTITR =  GlucoDataService.sharedPref?.getBoolean(Constants.SHARED_PREF_STANDARD_CHILDREN_STATISTICS, false)?: false
            if(Utils.getElapsedTimeMinute(lastUpdate) >= 30 || useTITR != newUseTITR || useStandard != newUseStandard || statData1d.needUpdate || statData7d.needUpdate) {
                Log.d(LOG_ID, "update statistics - lastUpdate: ${Utils.getUiTimeStamp(lastUpdate)}, standard: $newUseStandard ($useStandard), TITR: $newUseTITR ($useTITR), needUpdate: ${statData1d.needUpdate || statData7d.needUpdate}")
                // recalculate statistics data
                useStandard = newUseStandard
                useTITR = newUseTITR
                lastUpdate = System.currentTimeMillis()
                statData1d.update(useStandard, useTITR)
                statData7d.update(useStandard, useTITR)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "update exception: $exc")
        }
    }

    val hasStatistics: Boolean get() {
        return statData1d.hasData || statData7d.hasData
    }

    fun getStatisticsTitle(context: Context, statType: AlarmType): String {
        when(statType) {
            AlarmType.VERY_LOW -> {
                if(useStandard)
                    return if(ReceiveData.isMmol) "< 3.0" else "< 54"
                return context.getString(R.string.very_low)
            }
            AlarmType.LOW -> {
                if(useStandard)
                    return if(ReceiveData.isMmol) "3.0 - 3.8" else "54 - 69"
                return context.getString(R.string.low)
            }
            AlarmType.OK -> {
                if(useStandard) {
                    return if(ReceiveData.isMmol)
                    {
                        if(useTITR) "3.9 - 7.8" else "3.9 - 10.0"
                    } else
                    {
                        if(useTITR) "70 - 140" else "70 - 180"
                    }
                }
                return context.getString(R.string.time_in_range)
            }
            AlarmType.HIGH -> {
                if(useStandard) {
                    return if(ReceiveData.isMmol)
                    {
                        if(useTITR) "7.9 - 13.9" else "10.1 - 13.9"
                    } else
                    {
                        if(useTITR) "141 - 250" else "181 - 250"
                    }
                }
                return context.getString(R.string.high)
            }
            AlarmType.VERY_HIGH -> {
                if(useStandard)
                    return if(ReceiveData.isMmol) "> 13.9" else "> 250"
                return context.getString(R.string.very_high)
            }
            else -> return ""
        }
    }

}