package de.michelinside.glucodatahandler.common

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import de.michelinside.glucodatahandler.common.database.GlucoseValue
import de.michelinside.glucodatahandler.common.database.dbAccess
import de.michelinside.glucodatahandler.common.notification.AlarmHandler
import de.michelinside.glucodatahandler.common.notification.AlarmNotificationBase
import de.michelinside.glucodatahandler.common.notification.AlarmType
import de.michelinside.glucodatahandler.common.notifier.*
import de.michelinside.glucodatahandler.common.notifier.DataSource
import de.michelinside.glucodatahandler.common.receiver.ScreenEventReceiver
import de.michelinside.glucodatahandler.common.tasks.ElapsedTimeTask
import de.michelinside.glucodatahandler.common.tasks.TimeTaskService
import de.michelinside.glucodatahandler.common.utils.GlucoDataUtils
import de.michelinside.glucodatahandler.common.utils.Utils
import de.michelinside.glucodatahandler.common.utils.WakeLockHelper
import java.math.RoundingMode
import java.text.DateFormat
import java.util.*

object ReceiveData: SharedPreferences.OnSharedPreferenceChangeListener {
    private const val LOG_ID = "GDH.ReceiveData"
    const val SERIAL = "glucodata.Minute.SerialNumber"
    const val MGDL = "glucodata.Minute.mgdl"
    const val GLUCOSECUSTOM = "glucodata.Minute.glucose"
    const val RATE = "glucodata.Minute.Rate"
    const val ALARM = "glucodata.Minute.Alarm"
    const val TIME = "glucodata.Minute.Time"
    const val DELTA = "glucodata.Minute.Delta"
    const val IOB = "glucodata.Minute.IOB"
    const val COB = "glucodata.Minute.COB"
    const val IOBCOB_TIME = "gdh.IOB_COB_time"
    const val SENSOR_START_TIME = "gdh.sensor_start_time"
    const val DELTA_FALLING_COUNT = "gdh.delta_falling_count"
    const val DELTA_RISING_COUNT = "gdh.delta_rising_count"

    const val CALCULATED_BUNDLE = "gdh.bundle.calculated"
    const val DELTA1MIN = "gdh.delta.1min"
    const val DELTA5MIN = "gdh.delta.5min"
    const val DELTA15MIN = "gdh.delta.15min"
    const val RATE_SOURCE = "gdh.rate.source"
    const val RATE_CALC = "gdh.rate.calculated"

    var sensorID: String? = null
    var sensorStartTime: Long = 0L
    var rawValue: Int = 0
    var glucose: Float = 0.0F
    var sourceRate = Float.NaN
    var calculatedRate = Float.NaN
    val rate: Float get() {
        if((useRateCalculation && !calculatedRate.isNaN()) || sourceRate.isNaN())
            return calculatedRate
        return sourceRate
    }
    private var useRateCalculation = true
    var alarm: Int = 0
    var time: Long = 0
    var receiveTime: Long = 0
    var timeDiff: Long = 0
    var rateLabel: String? = null
    var source: DataSource = DataSource.NONE
    val forceGlucoseAlarm: Boolean get() {
        return ((alarm and 8) == 8)
    }
    val forceDeltaAlarm: Boolean get() {
        return ((alarm and 16) == 16)
    }
    val forceAlarm: Boolean get() {
        return (forceGlucoseAlarm || forceDeltaAlarm)
    }

    var forceObsoleteOnScreenOff: Boolean = false
    private val forceObsolete: Boolean get() {
        return forceObsoleteOnScreenOff && ScreenEventReceiver.isDisplayOff()
    }

    var iob: Float = Float.NaN
    var cob: Float = Float.NaN
    var iobCobTime: Long = 0
    private var lowValue: Float = 70F
    val lowRaw: Float get() = lowValue
    val low: Float get() {
        if(isMmol && lowValue > 0F)  // mmol/l
        {
            return GlucoDataUtils.mgToMmol(lowValue)
        }
        return lowValue
    }
    private var highValue: Float = 240F
    val highRaw: Float get() = highValue
    val high: Float get() {
        if(isMmol && highValue > 0F)  // mmol/l
        {
            return GlucoDataUtils.mgToMmol(highValue)
        }
        return highValue
    }
    private var targetMinValue = 90F
    val targetMinRaw: Float get() = targetMinValue
    val targetMin: Float get() {
        if(isMmol)  // mmol/l
        {
            return GlucoDataUtils.mgToMmol(targetMinValue)
        }
        return targetMinValue
    }
    private var targetMaxValue = 165F
    val targetMaxRaw: Float get() = targetMaxValue
    val targetMax: Float get() {
        if(isMmol)  // mmol/l
        {
            return GlucoDataUtils.mgToMmol(targetMaxValue)
        }
        return targetMaxValue
    }

    private var deltaValue1Min: Float = Float.NaN
    val delta1Min: Float get() {
        if(isMmol)  // mmol/l
        {
            return GlucoDataUtils.mgToMmol(deltaValue1Min)
        }
        return Utils.round(deltaValue1Min, 1)
    }
    private var deltaValue5Min: Float = Float.NaN
    val delta5Min: Float get() {
        if(isMmol)  // mmol/l
        {
            return GlucoDataUtils.mgToMmol(deltaValue5Min)
        }
        return Utils.round(deltaValue5Min, 1)
    }
    private var deltaValue10Min: Float = Float.NaN
    val delta10Min: Float get() {
        if(isMmol)  // mmol/l
        {
            return GlucoDataUtils.mgToMmol(deltaValue10Min)
        }
        return Utils.round(deltaValue10Min, 1)
    }
    private var deltaValue15Min: Float = Float.NaN
    val delta15Min: Float get() {
        if(isMmol)  // mmol/l
        {
            return GlucoDataUtils.mgToMmol(deltaValue15Min)
        }
        return Utils.round(deltaValue15Min, 1)
    }
    private val deltaValue: Float
        get() {
            if(use5minDelta)
                return deltaValue5Min
            return deltaValue1Min
        }
    val delta: Float get() {
        if( deltaValue.isNaN() )
            return deltaValue
        if(isMmol)  // mmol/l
        {
            return GlucoDataUtils.mgToMmol(deltaValue)
        }
        return Utils.round(deltaValue, 1)
    }
    val deltaValueMgDl: Float get() {
        if( deltaValue.isNaN() )
            return deltaValue
        return Utils.round(deltaValue, 1)
    }

    private var isMmolValue = false
    val isMmol get() = isMmolValue
    var use5minDelta = false
    private var colorAlarm: Int = Color.RED
    private var colorOutOfRange: Int = Color.YELLOW
    private var colorOK: Int = Color.GREEN
    private var colorObsolete: Int = Color.GRAY
    private var obsoleteTimeMin: Int = 6
    val obsoleteTimeInMinute get() = obsoleteTimeMin
    private var initialized = false
    private var deltaFallingCount = 0
    private var deltaRisingCount = 0

    private var unitMgDl = "mg/dl"
    private var unitMmol = "mmol/l"

    init {
        Log.v(LOG_ID, "init called")
    }

    fun initData(context: Context) {
        try {
            if (!initialized) {
                Log.v(LOG_ID, "initData called")
                initialized = true
                unitMgDl = context.resources.getString(R.string.unit_mgdl)
                unitMmol = context.resources.getString(R.string.unit_mmol)
                dbAccess.init(context)
                AlarmHandler.initData(context)
                readTargets(context)
                loadExtras(context)
                TimeTaskService.run(context)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "initData exception: " + exc.toString() + "\n" + exc.stackTraceToString() )
        }
    }

    fun getAsText(context: Context, withIobCob: Boolean = false, withZeroTime: Boolean = true): String {
        if(isObsoleteShort())
            return context.getString(R.string.no_new_value, getElapsedTimeMinute())
        var text = ""
        val resId: Int? = if(getAlarmType() != AlarmType.OK) {
                AlarmNotificationBase.getAlarmTextRes(getAlarmType())
            } else if(getDeltaAlarmType() != AlarmType.NONE) {
                AlarmNotificationBase.getAlarmTextRes(getDeltaAlarmType())
            } else {
                null
            }
        if (resId != null) {
            text += context.getString(resId) + ", "
        }
        text += getGlucoseAsString()
        if(!rate.isNaN())
            text += ", " + getRateAsText(context)
        if(!delta.isNaN())
            text += ", Δ " + getDeltaAsString()
        if(withZeroTime || getElapsedTimeMinute() > 0)
            text += ", " + getElapsedRelativeTimeAsString(context, true)
        if(withIobCob && !isIobCobObsolete()) {
            if(!iob.isNaN())
                text += ", " + context.getString(R.string.info_label_iob_talkback) + " " + getIobAsString()
            if(!cob.isNaN())
                text += ", " + context.getString(R.string.info_label_cob_talkback) + " " + getCobAsString()
        }
        return text
    }

    fun isObsoleteTime(timeoutSec: Int): Boolean = (System.currentTimeMillis()- time) >= (timeoutSec * 1000)

    fun isObsoleteShort(): Boolean = forceObsolete || isObsoleteTime(obsoleteTimeMin*60)
    fun isObsoleteLong(): Boolean = forceObsolete || isObsoleteTime(obsoleteTimeMin*120)
    fun isIobCobObsolete(timeoutSec: Int = Constants.VALUE_IOB_COBOBSOLETE_SEC): Boolean = (System.currentTimeMillis()- iobCobTime) >= (timeoutSec * 1000)

    fun getGlucoseAsString(): String {
        if(isObsoleteLong())
            return "---"
        if (isMmol)
            return glucose.toString()
        return rawValue.toString()
    }

    fun getDbValue(): Int {
        if(isMmol) {
            return GlucoDataUtils.mmolToMg(glucose).toInt()
        }
        return rawValue
    }

    fun getDeltaAsString(): String {
        if(isObsoleteShort() || deltaValue.isNaN())
            return "--"
        return GlucoDataUtils.deltaToString(delta)
    }

    fun getRateAsString(): String {
        if(isObsoleteShort())
            return "--"
        return (if (rate > 0) "+" else "") + Utils.round(rate, 2).toString()
    }

    fun getRateAsText(context: Context): String {
        var rateText = if(rate < 2F && rate > -2F && rate != 0F) {
            // calculate degrees
            val degrees = GlucoDataUtils.getRateDegrees(rate)
            if (degrees > 0) {
                context.getString(R.string.rate_rising, degrees)
            } else if (degrees < 0) {
                context.getString(R.string.rate_falling, degrees)
            } else null
        } else null
        if(rateText.isNullOrEmpty())
            rateText = GlucoDataUtils.getRateLabel(context, rate)
        return context.resources.getString(R.string.trend) + " " + rateText
    }

    fun getUnit(): String {
        if (isMmol)
            return unitMmol
        return unitMgDl
    }

    fun getOtherUnit(): String {
        if (isMmol)
            return unitMgDl
        return unitMmol
    }

    fun getGlucoseAsOtherUnit(): String {
        if(isObsoleteLong())
            return "---"
        if (isMmol)
            return rawValue.toString()
        return GlucoDataUtils.mgToMmol(rawValue.toFloat()).toString()
    }

    fun getDeltaAsOtherUnit(): String {
        if(isObsoleteShort() || deltaValue.isNaN())
            return "--"
        val otherDelta = if(isMmol) Utils.round(deltaValue, 1) else GlucoDataUtils.mgToMmol(deltaValue)
        var deltaVal = ""
        if (otherDelta > 0F)
            deltaVal += "+"
        deltaVal += if( isMmol && otherDelta.toDouble() == Math.floor(otherDelta.toDouble()) )
            otherDelta.toInt().toString()
        else
            otherDelta.toString()
        return deltaVal
    }

    fun isIobCob() : Boolean {
        if (isIobCobObsolete()) {
            iob = Float.NaN
            cob = Float.NaN
        }
        return !iob.isNaN() || !cob.isNaN()
    }

    val iobString: String get() {
        if (isIobCobObsolete())
            iob = Float.NaN
        if(iob.isNaN()) {
            return " - "
        }
        return "%.2f".format(Locale.ROOT, iob)
    }
    val cobString: String get() {
        if (isIobCobObsolete())
            cob = Float.NaN
        if(cob.isNaN()) {
            return " - "
        }
        return Utils.round(cob, 0).toInt().toString()
    }

    fun getIobAsString(withUnit: Boolean = true): String {
        if (withUnit)
            return iobString + " U"
        return iobString
    }
    fun getCobAsString(withUnit: Boolean = true): String {
        if (withUnit)
            return cobString + " g"
        return cobString
    }

    // alarm bits: 1111 -> first: force alarm (8), second: high or low alarm (4), last: low (1)
    // additional: 1 0000 -> force delta alarm (16)
    /* examples: 
        6 = 0110 -> very high
        2 = 0010 -> high (out of range)
        3 = 0011 -> low (out of range)
        7 = 0111 -> very low
    */
    fun getAlarmType(): AlarmType {
        if(isObsoleteShort())
            return AlarmType.OBSOLETE
        if(((alarm and 7) == 6) || (high > 0F && glucose >= high))
            return AlarmType.VERY_HIGH
        if(((alarm and 7) == 7) || (low > 0F && glucose <= low))
            return AlarmType.VERY_LOW
        if(((alarm and 3) == 3) || (targetMin > 0 && glucose < targetMin ))
            return AlarmType.LOW
        if(((alarm and 3) == 2) || (targetMax > 0 && glucose > targetMax ))
            return AlarmType.HIGH
        return AlarmType.OK
    }

    fun getValueColor(rawValue: Int): Int {
        val customValue = if(isMmol) GlucoDataUtils.mgToMmol(rawValue.toFloat()) else rawValue.toFloat()
        if(high>0F && customValue >= high)
            return getAlarmTypeColor(AlarmType.VERY_HIGH)
        if(low>0F && customValue <= low)
            return getAlarmTypeColor(AlarmType.VERY_LOW)
        if(targetMin>0F && customValue < targetMin)
            return getAlarmTypeColor(AlarmType.LOW)
        if(targetMax>0F && customValue > targetMax)
            return getAlarmTypeColor(AlarmType.HIGH)
        return getAlarmTypeColor(AlarmType.OK)
    }

    private fun calculateRate(glucoseValues: List<GlucoseValue>, new_time: Long, new_value: Int) {
        var count = 0F
        var sum = 0F
        glucoseValues.forEach {
            val diffTime = Utils.getTimeDiffMinute(new_time, it.timestamp, RoundingMode.HALF_UP)
            if(diffTime > 25)
                return@forEach
            val factor = if(diffTime <= 5) 2F else if(diffTime <= 10) 1.5F else if(diffTime <= 15) 1F else 0.5F
            sum += (new_value-it.value)*factor/diffTime
            count += factor
        }
        if(count > 0) {
            calculatedRate = (sum*10/count)/Constants.GLUCOSE_CONVERSION_FACTOR
            Log.d(LOG_ID, "Calculated rate for $count values - sum $sum: $calculatedRate")
        } else if(!sourceRate.isNaN()) {
            calculatedRate = sourceRate
        }
    }

    private fun calculateDeltasAndRate(new_time: Long, new_value: Int) {
        // reset old deltas
        deltaValue1Min = Float.NaN
        deltaValue5Min = Float.NaN
        deltaValue10Min = Float.NaN
        deltaValue15Min = Float.NaN
        if(dbAccess.active) {
            val glucoseValues = dbAccess.getGlucoseValuesInRange(new_time-(25*60*1000), new_time) // max 20 minutes to calculate the delta
            if (glucoseValues.isNotEmpty()) {
                glucoseValues.reversed().forEach {
                    if(it.timestamp == time)
                        it.value = rawValue  // override for correct delta calculation
                    val diffTime = Utils.getTimeDiffMinute(new_time, it.timestamp, RoundingMode.HALF_UP)
                    if(deltaValue1Min.isNaN() && diffTime >= 1) {
                        deltaValue1Min = (new_value-it.value).toFloat()/diffTime
                        Log.d(LOG_ID, "1 Calculate $diffTime min delta - new_value $new_value, old_value ${it.value}, delta $deltaValue1Min ")
                    }
                    if(deltaValue5Min.isNaN() && diffTime >= 5) {
                        deltaValue5Min = (new_value-it.value).toFloat()/(diffTime/5)
                        Log.d(LOG_ID, "5 Calculate $diffTime min delta - new_value $new_value, old_value ${it.value}, delta $deltaValue5Min ")
                    }
                    if(deltaValue10Min.isNaN() && diffTime >= 10) {
                        deltaValue10Min = (new_value-it.value).toFloat()/(diffTime/10)
                        Log.d(LOG_ID, "10 Calculate $diffTime min delta - new_value $new_value, old_value ${it.value}, delta $deltaValue10Min ")
                    }
                    if(deltaValue15Min.isNaN() && diffTime >= 15) {
                        deltaValue15Min = (new_value-it.value).toFloat()/(diffTime/15)
                        Log.d(LOG_ID, "15 Calculate $diffTime min delta - new_value $new_value, old_value ${it.value}, delta $deltaValue15Min ")
                        return@forEach
                    }
                }
                calculateRate(glucoseValues, new_time, new_value)
            }
            if(!deltaValue.isNaN())
                return  // calculated
        }
        // else compare with last value
        calculatedRate = sourceRate
        if (time > 0) {
            // calculate delta value
            timeDiff = new_time-time
            val timeDiffMinute = getTimeDiffMinute(new_time)
            Log.d(LOG_ID, "Calculate delta with db data with time diff minute: $timeDiffMinute min")
            if (timeDiffMinute == 0L) {
                Log.w(LOG_ID, "Time diff is less than a minute! Can not calculate delta value!")
            } else if (timeDiffMinute <= 20L) {
                val newDelta = (new_value - rawValue).toFloat()
                val deltaTime = if(use5minDelta) 5L else 1L
                val factor = if(timeDiffMinute != deltaTime) {
                    val factor: Float = timeDiffMinute.toFloat() / deltaTime.toFloat()
                    Log.d(LOG_ID, "Divide delta " + newDelta.toString() + " with factor " + factor.toString() + " for time diff: " + timeDiffMinute.toString() + " minute(s)")
                    factor
                } else {
                    1F
                }
                if(use5minDelta) {
                    deltaValue5Min = newDelta / factor
                    deltaValue1Min = deltaValue5Min/5
                } else
                    deltaValue1Min = newDelta / factor
            }
        }
    }

    private fun calculateAlarm(): Int {
        alarm = 0 // reset to calculate again
        val curAlarmType = getAlarmType()
        val curAlarm = when(curAlarmType) {
            AlarmType.VERY_HIGH -> 6
            AlarmType.HIGH -> 2
            AlarmType.LOW -> 3
            AlarmType.VERY_LOW -> 7
            else -> 0
        }

        if (curAlarm != 0 && AlarmHandler.checkForAlarmTrigger(curAlarmType)) {
            return curAlarm or 8
        }
        return curAlarm
    }

    fun getDeltaAlarmType(): AlarmType {
        if(!isObsoleteLong() && !delta.isNaN()) {
            if (deltaFallingCount>0)
                return AlarmType.FALLING_FAST
            else if (deltaRisingCount>0)
                return AlarmType.RISING_FAST
        }
        return AlarmType.NONE
    }

    private fun calculateDeltaAlarmCount() {
        if (delta.isNaN() || delta == 0F) {
            deltaFallingCount = 0
            deltaRisingCount = 0
        } else {
            if (deltaValue >= AlarmType.RISING_FAST.setting!!.delta) {
                deltaFallingCount = 0
                deltaRisingCount++
            } else if (deltaValue <= -AlarmType.FALLING_FAST.setting!!.delta) {
                deltaFallingCount++
                deltaRisingCount = 0
            } else {
                deltaFallingCount = 0
                deltaRisingCount = 0
            }
        }
    }

    fun getGlucoseColor(monoChrome: Boolean = false): Int {
        if (monoChrome) {
            if (isObsoleteShort())
                return Color.GRAY
            return Color.WHITE
        }

        return getAlarmTypeColor(getAlarmType())
    }

    fun getAlarmTypeColor(alarmType: AlarmType): Int {
        return when(alarmType) {
            AlarmType.NONE -> Color.GRAY
            AlarmType.VERY_LOW -> colorAlarm
            AlarmType.LOW -> colorOutOfRange
            AlarmType.OK -> colorOK
            AlarmType.HIGH -> colorOutOfRange
            AlarmType.VERY_HIGH -> colorAlarm
            AlarmType.OBSOLETE -> colorObsolete
            else -> colorOK
        }
    }

    fun getTimeDiffMinute(new_time: Long): Long {
        return Utils.round((new_time-time).toFloat()/60000, 0).toLong()
    }

    fun getElapsedTimeMinute(roundingMode: RoundingMode = RoundingMode.DOWN): Long {
        return Utils.getElapsedTimeMinute(time, roundingMode)
    }
    fun getElapsedIobCobTimeMinute(roundingMode: RoundingMode = RoundingMode.HALF_UP): Long {
        return Utils.round((System.currentTimeMillis()- iobCobTime).toFloat()/60000, 0, roundingMode).toLong()
    }

    fun getElapsedRelativeTimeAsString(context: Context, fullText: Boolean = false): String {
        val elapsed_time = getElapsedTimeMinute()
        if (elapsed_time > 60)
            return context.getString(R.string.elapsed_time_hour)
        if(fullText)
            return context.resources.getQuantityString(R.plurals.elapsed_time_full_text, elapsed_time.toInt(), elapsed_time)
        return context.getString(R.string.elapsed_time, elapsed_time)
    }

    fun getElapsedTimeMinuteAsString(context: Context, short: Boolean = true): String {
        if (time == 0L || forceObsolete)
            return "--"
        return if (ElapsedTimeTask.relativeTime) {
            getElapsedRelativeTimeAsString(context)
        } else if (short)
            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(time))
        else
            DateFormat.getTimeInstance(DateFormat.DEFAULT).format(Date(time))
    }

    fun hasNewValue(extras: Bundle?, checkIobCob: Boolean = true): Boolean {
        if(extras == null || extras.isEmpty)
            return false
        if(extras.containsKey(TIME) && isNewValueTime(extras.getLong(TIME))) {
            return true
        }
        if(!checkIobCob)
            return false
        return hasNewIobCob(extras)
    }

    private fun isNewValueTime(newTime: Long): Boolean {
        return (newTime-time) >= 45000  // at least 45 seconds should be passed to be considered a new value
    }

    fun handleIntent(context: Context, dataSource: DataSource, extras: Bundle?, interApp: Boolean = false) : Boolean
    {
        if (extras == null || extras.isEmpty) {
            return false
        }
        forceObsoleteOnScreenOff = false
        var result = false
        WakeLockHelper(context).use {
            initData(context)
            try {
                val new_time = extras.getLong(TIME)
                Log.d(
                    LOG_ID, "Glucodata received from " + dataSource.toString() + ": " +
                            extras.toString() +
                            " - timestamp: " + DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT).format(Date(new_time)) +
                            " - difference: " + (new_time-time)
                )

                if (!GlucoDataUtils.isGlucoseValid(extras.getInt(MGDL))) {
                    Log.w(LOG_ID, "Invalid glucose values received! " + extras.toString())
                    return false
                }

                if(isNewValueTime(new_time)) // check for new value received (diff must around one minute at least to prevent receiving same data from different sources with similar timestamps
                {
                    Log.i(
                        LOG_ID, "Glucodata received from " + dataSource.toString() + ": " +
                                extras.toString() +
                                " - timestamp: " + DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT).format(Date(extras.getLong(TIME)))
                    )
                    if(time == 0L)
                        handleFirstValue(context, dataSource, extras)
                    receiveTime = System.currentTimeMillis()
                    source = dataSource
                    sensorID = extras.getString(SERIAL) //Name of sensor
                    sensorStartTime = extras.getLong(SENSOR_START_TIME)
                    sourceRate = extras.getFloat(RATE) //Rate of change of glucose. See libre and dexcom label functions

                    if(!readCalculatedBundle(extras)) {
                        calculateDeltasAndRate(new_time, extras.getInt(MGDL))
                        if (deltaValue.isNaN() && extras.containsKey(DELTA)) {
                            if(use5minDelta) {
                                deltaValue5Min = extras.getFloat(DELTA, Float.NaN)
                                deltaValue1Min = deltaValue5Min/5
                            } else {
                                deltaValue1Min = extras.getFloat(DELTA, Float.NaN)
                            }
                        }
                    }
                    rateLabel = GlucoDataUtils.getRateLabel(context, rate)
                    rawValue = extras.getInt(MGDL)
                    if (isMmol) {
                        if (extras.containsKey(GLUCOSECUSTOM)) {
                            glucose = Utils.round(extras.getFloat(GLUCOSECUSTOM), 1)
                            if(!GlucoDataUtils.isMmolValue(glucose))
                                glucose = GlucoDataUtils.mgToMmol(rawValue.toFloat())
                        }
                        else
                            glucose = GlucoDataUtils.mgToMmol(rawValue.toFloat())
                    } else {
                        glucose = rawValue.toFloat()
                    }
                    time = extras.getLong(TIME) //time in msec

                    if(extras.containsKey(IOB) || extras.containsKey(COB)) {
                        iob = extras.getFloat(IOB, Float.NaN)
                        cob = extras.getFloat(COB, Float.NaN)
                        iobCobTime = if(extras.containsKey(IOBCOB_TIME))
                            extras.getLong(IOBCOB_TIME)
                        else
                            time
                    }

                    // check for alarm
                    if (interApp) {
                        alarm = extras.getInt(ALARM) // if bit 8 is set, then an alarm is triggered
                        if(forceGlucoseAlarm)
                            AlarmHandler.setLastAlarm(getAlarmType())
                    } else {
                        alarm = calculateAlarm()
                    }
                    if(extras.containsKey(DELTA_FALLING_COUNT) && extras.containsKey(DELTA_RISING_COUNT)) {
                        deltaFallingCount = extras.getInt(DELTA_FALLING_COUNT)
                        deltaRisingCount = extras.getInt(DELTA_RISING_COUNT)
                        if(interApp && forceDeltaAlarm)
                            AlarmHandler.setLastAlarm(getDeltaAlarmType())
                    } else {
                        calculateDeltaAlarmCount()
                    }
                    if(!interApp && !forceGlucoseAlarm && !forceDeltaAlarm) {
                        val deltaAlarmType = AlarmHandler.checkDeltaAlarmTrigger(deltaFallingCount, deltaRisingCount)
                        if(deltaAlarmType != AlarmType.NONE) {
                            alarm = alarm or 16
                        }
                    }

                    dbAccess.addGlucoseValue(time, getDbValue())

                    val notifySource = if(interApp) NotifySource.MESSAGECLIENT else NotifySource.BROADCAST

                    InternalNotifier.notify(context, notifySource, createExtras())  // re-create extras to have all changed value inside...
                    if(forceGlucoseAlarm) {
                        InternalNotifier.notify(context, NotifySource.ALARM_TRIGGER, null)
                    } else if(forceDeltaAlarm) {
                        val bundle = Bundle()
                        bundle.putInt(Constants.ALARM_TYPE_EXTRA, getDeltaAlarmType().ordinal)
                        InternalNotifier.notify(context, NotifySource.DELTA_ALARM_TRIGGER, bundle)
                    }
                    saveExtras(context)
                    result = true
                } else if( extras.containsKey(IOB) || extras.containsKey(COB)) {
                    handleIobCob(context, dataSource, extras, interApp)
                } else {
                    // dummy
                }
            } catch (exc: Exception) {
                Log.e(LOG_ID, "Receive exception: " + exc.toString() + "\n" + exc.stackTraceToString() )
            }
        }
        return result
    }

    private fun hasNewIobCob(extras: Bundle?): Boolean {
        if(extras != null && (extras.containsKey(IOB) || extras.containsKey(COB))) {
            if (!isIobCob() && extras.getFloat(IOB, Float.NaN).isNaN() && extras.getFloat(COB, Float.NaN).isNaN()) {
                Log.d(LOG_ID, "Ignore NaN IOB and COB")
                return false
            }

            val newTime = if(extras.containsKey(IOBCOB_TIME))
                extras.getLong(IOBCOB_TIME)
            else
                System.currentTimeMillis()

            if(!isIobCob() || (newTime > iobCobTime && (newTime-iobCobTime) > 30000)) {
                return true
            }
        }
        return false
    }

    fun handleIobCob(context: Context, dataSource: DataSource, extras: Bundle, interApp: Boolean = false) {
        Log.v(LOG_ID, "handleIobCob for source " + dataSource + ": " + extras.toString())
        if (hasNewIobCob(extras)) {
            var iobCobChange = false
            if(iob != extras.getFloat(IOB, Float.NaN) || cob != extras.getFloat(COB, Float.NaN)) {
                Log.i(LOG_ID, "Only IOB/COB changed: " + extras.getFloat(IOB, Float.NaN) + "/" +  extras.getFloat(COB, Float.NaN))
                iob = extras.getFloat(IOB, Float.NaN)
                cob = extras.getFloat(COB, Float.NaN)
                iobCobChange = true
            } else {
                Log.d(LOG_ID, "Only IOB/COB time changed")
            }
            iobCobTime = if(extras.containsKey(IOBCOB_TIME))
                extras.getLong(IOBCOB_TIME)
            else
                System.currentTimeMillis()

            // do not forward extras as interApp to prevent sending back to source...
            val bundle: Bundle? = if(interApp) null else createExtras()  // re-create extras to have all changed value inside for sending to receiver
            if(iobCobChange)
                InternalNotifier.notify(context, NotifySource.IOB_COB_CHANGE, bundle)
            else
                InternalNotifier.notify(context, NotifySource.IOB_COB_TIME, bundle)
            saveExtras(context)
        }
    }

    private fun handleFirstValue(context: Context, dataSource: DataSource, extras: Bundle) {
        // check unit and delta for first value, only!
        Log.i(LOG_ID, "Handle first value")
        val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
        if (extras.containsKey(GLUCOSECUSTOM)) {
            val raw = extras.getInt(MGDL)
            val glucoseCustom = Utils.round(extras.getFloat(GLUCOSECUSTOM), 1) //Glucose value in unit in setting
            changeIsMmol(raw!=glucoseCustom.toInt() && GlucoDataUtils.isMmolValue(glucoseCustom), context)
        }
        if(!sharedPref.contains(Constants.SHARED_PREF_FIVE_MINUTE_DELTA) && dataSource.interval5Min) {
            Log.i(LOG_ID, "Change delta to 5 min")
            use5minDelta = true
            with(sharedPref.edit()) {
                putBoolean(Constants.SHARED_PREF_FIVE_MINUTE_DELTA, true)
                apply()
            }
        }
        if(dataSource == DataSource.DEXCOM_SHARE && !sharedPref.contains(Constants.SHARED_PREF_SOURCE_INTERVAL)) {
            with(sharedPref.edit()) {
                putString(Constants.SHARED_PREF_SOURCE_INTERVAL, "5")  // use 5 min interval for dexcom share
                apply()
            }
        }
    }

    fun changeIsMmol(newValue: Boolean, context: Context? = null) {
        if (isMmol != newValue) {
            isMmolValue = newValue
            if (time > 0 && isMmolValue != GlucoDataUtils.isMmolValue(glucose)) {
                glucose = if (isMmolValue)
                    GlucoDataUtils.mgToMmol(glucose)
                else
                    GlucoDataUtils.mmolToMg(glucose)
            }
            Log.i(LOG_ID, "Unit changed to " + glucose + if(isMmolValue) " mmol/l" else " mg/dl")
            if (context != null) {
                val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
                with(sharedPref.edit()) {
                    putBoolean(Constants.SHARED_PREF_USE_MMOL, isMmol)
                    apply()
                }
            }
        }
    }

    fun setSettings(sharedPref: SharedPreferences, bundle: Bundle) {
        with(sharedPref.edit()) {
            putFloat(Constants.SHARED_PREF_TARGET_MIN, bundle.getFloat(Constants.SHARED_PREF_TARGET_MIN, targetMinValue))
            putFloat(Constants.SHARED_PREF_TARGET_MAX, bundle.getFloat(Constants.SHARED_PREF_TARGET_MAX, targetMaxValue))
            putFloat(Constants.SHARED_PREF_LOW_GLUCOSE, bundle.getFloat(Constants.SHARED_PREF_LOW_GLUCOSE, lowValue))
            putFloat(Constants.SHARED_PREF_HIGH_GLUCOSE, bundle.getFloat(Constants.SHARED_PREF_HIGH_GLUCOSE, highValue))
            putBoolean(Constants.SHARED_PREF_USE_MMOL, bundle.getBoolean(Constants.SHARED_PREF_USE_MMOL, isMmol))
            putBoolean(Constants.SHARED_PREF_FIVE_MINUTE_DELTA, bundle.getBoolean(Constants.SHARED_PREF_FIVE_MINUTE_DELTA, use5minDelta))
            putInt(Constants.SHARED_PREF_COLOR_OK, bundle.getInt(Constants.SHARED_PREF_COLOR_OK, colorOK))
            putInt(Constants.SHARED_PREF_COLOR_OUT_OF_RANGE, bundle.getInt(Constants.SHARED_PREF_COLOR_OUT_OF_RANGE, colorOutOfRange))
            putInt(Constants.SHARED_PREF_COLOR_ALARM, bundle.getInt(Constants.SHARED_PREF_COLOR_ALARM, colorAlarm))
            putInt(Constants.SHARED_PREF_COLOR_OBSOLETE, bundle.getInt(Constants.SHARED_PREF_COLOR_OBSOLETE, colorObsolete))
            putInt(Constants.SHARED_PREF_OBSOLETE_TIME, bundle.getInt(Constants.SHARED_PREF_OBSOLETE_TIME, obsoleteTimeMin))
            putBoolean(Constants.SHARED_PREF_USE_RATE_CALCULATION, bundle.getBoolean(Constants.SHARED_PREF_USE_RATE_CALCULATION, useRateCalculation))
            if (bundle.containsKey(Constants.SHARED_PREF_RELATIVE_TIME)) {
                putBoolean(Constants.SHARED_PREF_RELATIVE_TIME, bundle.getBoolean(Constants.SHARED_PREF_RELATIVE_TIME, ElapsedTimeTask.relativeTime))
            }
            apply()
        }
        updateSettings(sharedPref)
    }

    fun getSettingsBundle(): Bundle {
        val bundle = Bundle()
        bundle.putFloat(Constants.SHARED_PREF_TARGET_MIN, targetMinValue)
        bundle.putFloat(Constants.SHARED_PREF_TARGET_MAX, targetMaxValue)
        bundle.putFloat(Constants.SHARED_PREF_LOW_GLUCOSE, lowValue)
        bundle.putFloat(Constants.SHARED_PREF_HIGH_GLUCOSE, highValue)
        bundle.putBoolean(Constants.SHARED_PREF_USE_MMOL, isMmol)
        bundle.putBoolean(Constants.SHARED_PREF_FIVE_MINUTE_DELTA, use5minDelta)
        bundle.putInt(Constants.SHARED_PREF_COLOR_OK, colorOK)
        bundle.putInt(Constants.SHARED_PREF_COLOR_OUT_OF_RANGE, colorOutOfRange)
        bundle.putInt(Constants.SHARED_PREF_COLOR_ALARM, colorAlarm)
        bundle.putInt(Constants.SHARED_PREF_COLOR_OBSOLETE, colorObsolete)
        bundle.putInt(Constants.SHARED_PREF_OBSOLETE_TIME, obsoleteTimeMin)
        bundle.putBoolean(Constants.SHARED_PREF_USE_RATE_CALCULATION, useRateCalculation)
        return bundle
    }

    fun updateSettings(sharedPref: SharedPreferences) {
        targetMinValue = sharedPref.getFloat(Constants.SHARED_PREF_TARGET_MIN, targetMinValue)
        targetMaxValue = sharedPref.getFloat(Constants.SHARED_PREF_TARGET_MAX, targetMaxValue)
        lowValue = sharedPref.getFloat(Constants.SHARED_PREF_LOW_GLUCOSE, lowValue)
        highValue = sharedPref.getFloat(Constants.SHARED_PREF_HIGH_GLUCOSE, highValue)
        use5minDelta = sharedPref.getBoolean(Constants.SHARED_PREF_FIVE_MINUTE_DELTA, use5minDelta)
        colorOK = sharedPref.getInt(Constants.SHARED_PREF_COLOR_OK, colorOK)
        colorOutOfRange = sharedPref.getInt(Constants.SHARED_PREF_COLOR_OUT_OF_RANGE, colorOutOfRange)
        colorAlarm = sharedPref.getInt(Constants.SHARED_PREF_COLOR_ALARM, colorAlarm)
        colorObsolete = sharedPref.getInt(Constants.SHARED_PREF_COLOR_OBSOLETE, colorObsolete)
        obsoleteTimeMin = sharedPref.getInt(Constants.SHARED_PREF_OBSOLETE_TIME, obsoleteTimeMin)
        useRateCalculation = sharedPref.getBoolean(Constants.SHARED_PREF_USE_RATE_CALCULATION, useRateCalculation)
        changeIsMmol(sharedPref.getBoolean(Constants.SHARED_PREF_USE_MMOL, isMmol))
        calculateAlarm()  // re-calculate alarm with new settings
        Log.i(LOG_ID, "Raw low/min/max/high set: " + lowValue.toString() + "/" + targetMinValue.toString() + "/" + targetMaxValue.toString() + "/" + highValue.toString()
                + " mg/dl - unit: " + getUnit()
                + " - 5 min delta: " + use5minDelta
                + " - obsolete time: " + obsoleteTimeMin
                + " - alarm/out/ok/obsolete colors: " + colorAlarm.toString() + "/" + colorOutOfRange.toString() + "/" + colorOK.toString() + "/" + colorObsolete.toString())
    }

    private fun readTargets(context: Context) {
        val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
        sharedPref.registerOnSharedPreferenceChangeListener(this)
        if(!sharedPref.contains(Constants.SHARED_PREF_USE_MMOL) && (sharedPref.contains(Constants.SHARED_PREF_TARGET_MIN) || sharedPref.contains(Constants.SHARED_PREF_TARGET_MAX))) {
            targetMinValue = sharedPref.getFloat(Constants.SHARED_PREF_TARGET_MIN, targetMinValue)
            targetMaxValue = sharedPref.getFloat(Constants.SHARED_PREF_TARGET_MAX, targetMaxValue)
            isMmolValue = GlucoDataUtils.isMmolValue(targetMinValue)
            if (isMmol) {
                Log.i(LOG_ID, "Upgrade to new mmol handling!")
                writeTarget(context, true, targetMinValue)
                writeTarget(context, false, targetMaxValue)
                with(sharedPref.edit()) {
                    putBoolean(Constants.SHARED_PREF_USE_MMOL, isMmol)
                    apply()
                }
            }
        }
        updateSettings(sharedPref)
    }

    private fun writeTarget(context: Context, min: Boolean, value: Float) {
        val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
        var mgdlValue = value
        if (GlucoDataUtils.isMmolValue(mgdlValue)) {
            mgdlValue = GlucoDataUtils.mmolToMg(value)
        }
        Log.i(LOG_ID, "New target" + (if (min) "Min" else "Max") + " value: " + mgdlValue.toString())
        with(sharedPref.edit()) {
            if (min) {
                putFloat(Constants.SHARED_PREF_TARGET_MIN, mgdlValue.toString().toFloat())
                targetMinValue = mgdlValue
            } else {
                putFloat(Constants.SHARED_PREF_TARGET_MAX, mgdlValue.toString().toFloat())
                targetMaxValue = mgdlValue
            }
            apply()
        }
    }

    fun createExtras(includeObsoleteIobCob: Boolean = true): Bundle? {
        if(time == 0L)
            return null
        val extras = Bundle()
        extras.putLong(TIME, time)
        extras.putFloat(GLUCOSECUSTOM, glucose)
        extras.putInt(MGDL, rawValue)
        extras.putString(SERIAL, sensorID)
        extras.putLong(SENSOR_START_TIME, sensorStartTime)
        extras.putFloat(RATE, rate)
        extras.putInt(ALARM, alarm)
        extras.putFloat(DELTA, deltaValue)
        if(includeObsoleteIobCob || !isIobCobObsolete()) {
            extras.putFloat(IOB, iob)
            extras.putFloat(COB, cob)
            extras.putLong(IOBCOB_TIME, iobCobTime)
        }
        extras.putBundle(CALCULATED_BUNDLE, getCalculatedBundle())
        extras.putInt(DELTA_FALLING_COUNT, deltaFallingCount)
        extras.putInt(DELTA_RISING_COUNT, deltaRisingCount)
        return extras
    }

    private fun getCalculatedBundle(): Bundle? {
        if(time == 0L)
            return null
        val extras = Bundle()
        extras.putFloat(DELTA1MIN, deltaValue1Min)
        extras.putFloat(DELTA5MIN, deltaValue5Min)
        extras.putFloat(DELTA15MIN, deltaValue15Min)
        extras.putFloat(RATE_CALC, calculatedRate)
        extras.putFloat(RATE_SOURCE, sourceRate)
        return extras
    }

    private fun readCalculatedBundle(bundle: Bundle): Boolean {
        if(bundle.containsKey(CALCULATED_BUNDLE)) {
            val extras = bundle.getBundle(CALCULATED_BUNDLE) ?: return false
            deltaValue1Min = extras.getFloat(DELTA1MIN, Float.NaN)
            deltaValue5Min = extras.getFloat(DELTA5MIN, Float.NaN)
            deltaValue15Min = extras.getFloat(DELTA15MIN, Float.NaN)
            calculatedRate = extras.getFloat(RATE_CALC, Float.NaN)
            sourceRate = extras.getFloat(RATE_SOURCE, Float.NaN)
            return !(deltaValue1Min.isNaN() || deltaValue5Min.isNaN() || deltaValue15Min.isNaN() || calculatedRate.isNaN())
        }
        return false
    }

    private fun saveExtras(context: Context) {
        try {
            Log.v(LOG_ID, "Saving extras")
            // use own tag to prevent trigger onChange event at every time!
            val sharedGlucosePref = context.getSharedPreferences(Constants.GLUCODATA_BROADCAST_ACTION, Context.MODE_PRIVATE)
            with(sharedGlucosePref.edit()) {
                putLong(TIME, time)
                putFloat(GLUCOSECUSTOM, glucose)
                putInt(MGDL, rawValue)
                putString(SERIAL, sensorID)
                putLong(SENSOR_START_TIME, sensorStartTime)
                putFloat(RATE, rate)
                putInt(ALARM, alarm)
                putFloat(DELTA, deltaValue)
                putFloat(IOB, iob)
                putFloat(COB, cob)
                putLong(IOBCOB_TIME, iobCobTime)
                putInt(DELTA_FALLING_COUNT, deltaFallingCount)
                putInt(DELTA_RISING_COUNT, deltaRisingCount)
                putInt(Constants.EXTRA_SOURCE_INDEX, source.ordinal)
                apply()
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Saving extras exception: " + exc.toString() + "\n" + exc.stackTraceToString() )
        }
    }

    private fun loadExtras(context: Context) {
        try {
            if (time == 0L) {
                val sharedGlucosePref = context.getSharedPreferences(Constants.GLUCODATA_BROADCAST_ACTION, Context.MODE_PRIVATE)
                if (sharedGlucosePref.contains(TIME)) {
                    Log.i(LOG_ID, "Reading saved values...")
                    val extras = Bundle()
                    extras.putLong(TIME, sharedGlucosePref.getLong(TIME, time))
                    extras.putFloat(GLUCOSECUSTOM, sharedGlucosePref.getFloat(GLUCOSECUSTOM, glucose))
                    extras.putInt(MGDL, sharedGlucosePref.getInt(MGDL, rawValue))
                    extras.putString(SERIAL, sharedGlucosePref.getString(SERIAL, sensorID))
                    extras.putLong(SENSOR_START_TIME, sharedGlucosePref.getLong(SENSOR_START_TIME, 0L))
                    extras.putFloat(RATE, sharedGlucosePref.getFloat(RATE, rate))
                    extras.putInt(ALARM, sharedGlucosePref.getInt(ALARM, alarm))
                    extras.putFloat(DELTA, sharedGlucosePref.getFloat(DELTA, deltaValue))
                    extras.putFloat(IOB, sharedGlucosePref.getFloat(IOB, iob))
                    extras.putFloat(COB, sharedGlucosePref.getFloat(COB, cob))
                    extras.putLong(IOBCOB_TIME, sharedGlucosePref.getLong(IOBCOB_TIME, iobCobTime))
                    extras.putInt(DELTA_FALLING_COUNT, sharedGlucosePref.getInt(DELTA_FALLING_COUNT, deltaFallingCount))
                    extras.putInt(DELTA_RISING_COUNT, sharedGlucosePref.getInt(DELTA_RISING_COUNT, deltaRisingCount))
                    val source = DataSource.fromIndex(sharedGlucosePref.getInt(Constants.EXTRA_SOURCE_INDEX,
                        DataSource.NONE.ordinal))
                    handleIntent(context, source, extras)
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Reading extras exception: " + exc.toString() + "\n" + exc.stackTraceToString() )
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        try {
            Log.d(LOG_ID, "onSharedPreferenceChanged called for key " + key)
            if (GlucoDataService.context != null) {
                when (key) {
                    Constants.SHARED_PREF_USE_MMOL,
                    Constants.SHARED_PREF_TARGET_MIN,
                    Constants.SHARED_PREF_TARGET_MAX,
                    Constants.SHARED_PREF_LOW_GLUCOSE,
                    Constants.SHARED_PREF_HIGH_GLUCOSE,
                    Constants.SHARED_PREF_FIVE_MINUTE_DELTA,
                    Constants.SHARED_PREF_COLOR_ALARM,
                    Constants.SHARED_PREF_COLOR_OUT_OF_RANGE,
                    Constants.SHARED_PREF_COLOR_OBSOLETE,
                    Constants.SHARED_PREF_COLOR_OK,
                    Constants.SHARED_PREF_OBSOLETE_TIME,
                    Constants.SHARED_PREF_USE_RATE_CALCULATION -> {
                        updateSettings(sharedPreferences!!)
                        val extras = Bundle()
                        extras.putBundle(Constants.SETTINGS_BUNDLE, GlucoDataService.getSettings())
                        InternalNotifier.notify(GlucoDataService.context!!, NotifySource.SETTINGS, extras)
                    }
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString() + "\n" + exc.stackTraceToString() )
        }
    }
}
