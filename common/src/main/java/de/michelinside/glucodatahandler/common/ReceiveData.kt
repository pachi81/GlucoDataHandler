package de.michelinside.glucodatahandler.common

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import de.michelinside.glucodatahandler.common.notifier.*
import de.michelinside.glucodatahandler.common.notifier.DataSource
import de.michelinside.glucodatahandler.common.tasks.ElapsedTimeTask
import de.michelinside.glucodatahandler.common.tasks.NightscoutSourceTask
import de.michelinside.glucodatahandler.common.tasks.TimeTaskService
import de.michelinside.glucodatahandler.common.utils.GlucoDataUtils
import de.michelinside.glucodatahandler.common.utils.Utils
import de.michelinside.glucodatahandler.common.utils.WakeLockHelper
import java.math.RoundingMode
import java.text.DateFormat
import java.util.*
import kotlin.math.abs

object ReceiveData: SharedPreferences.OnSharedPreferenceChangeListener {
    private const val LOG_ID = "GDH.ReceiveData"
    const val SERIAL = "glucodata.Minute.SerialNumber"
    const val MGDL = "glucodata.Minute.mgdl"
    const val GLUCOSECUSTOM = "glucodata.Minute.glucose"
    const val RATE = "glucodata.Minute.Rate"
    const val ALARM = "glucodata.Minute.Alarm"
    const val TIME = "glucodata.Minute.Time"
    const val DELTA = "glucodata.Minute.Delta"
    const val SOURCE_INDEX = "source_idx"
    const val IOB = "gdh.IOB"
    const val COB = "gdh.COB"
    const val IOBCOB_TIME = "gdh.IOB_COB_time"

    private const val LAST_ALARM_INDEX = "last_alarm_index"
    private const val LAST_ALARM_TIME = "last_alarm_time"

    enum class AlarmType(val resId: Int) {
        NONE(R.string.alarm_none),
        VERY_LOW(R.string.alarm_very_low),
        LOW(R.string.alarm_low),
        OK(R.string.alarm_none),
        HIGH(R.string.alarm_high),
        VERY_HIGH(R.string.alarm_very_high);

        companion object {
            fun fromIndex(idx: Int): AlarmType {
                AlarmType.values().forEach {
                    if(it.ordinal == idx) {
                        return it
                    }
                }
                return NONE
            }
        }
    }

    var sensorID: String? = null
    var rawValue: Int = 0
    var glucose: Float = 0.0F
    var rate: Float = 0.0F
    var alarm: Int = 0
    var time: Long = 0
    var timeDiff: Long = 0
    var rateLabel: String? = null
    var source: DataSource = DataSource.NONE
    var forceAlarm: Boolean = false
    var iob: Float = Float.NaN
    var cob: Float = Float.NaN
    var iobCobTime: Long = 0
    private var lowValue: Float = 70F
    private val low: Float get() {
        if(isMmol && lowValue > 0F)  // mmol/l
        {
            return GlucoDataUtils.mgToMmol(lowValue, 1)
        }
        return lowValue
    }
    private var highValue: Float = 240F
    private val high: Float get() {
        if(isMmol && highValue > 0F)  // mmol/l
        {
            return GlucoDataUtils.mgToMmol(highValue, 1)
        }
        return highValue
    }
    private var targetMinValue = 90F
    val targetMin: Float get() {
        if(isMmol)  // mmol/l
        {
            return GlucoDataUtils.mgToMmol(targetMinValue, 1)
        }
        return targetMinValue
    }
    private var targetMaxValue = 165F
    val targetMax: Float get() {
        if(isMmol)  // mmol/l
        {
            return GlucoDataUtils.mgToMmol(targetMaxValue, 1)
        }
        return targetMaxValue
    }

    private var deltaValue: Float = Float.NaN
    val delta: Float get() {
        if( deltaValue.isNaN() )
            return deltaValue
        if(isMmol)  // mmol/l
        {
            return GlucoDataUtils.mgToMmol(deltaValue, if (abs(deltaValue) > 1.0F) 1 else 2)
        }
        return Utils.round(deltaValue, 1)
    }
    private var isMmolValue = false
    val isMmol get() = isMmolValue
    private var use5minDelta = false
    private var colorAlarm: Int = Color.RED
    private var colorOutOfRange: Int = Color.YELLOW
    private var colorOK: Int = Color.GREEN
    private var lowAlarmDuration = 15L*60*1000 // ms -> 15 minutes
    private var highAlarmDuration = 25L*60*1000 // ms -> 25 minutes
    private var lastAlarmTime = 0L
    private var lastAlarmType = AlarmType.OK
    private var initialized = false

    init {
        Log.v(LOG_ID, "init called")
    }

    fun initData(context: Context) {
        try {
            if (!initialized) {
                Log.v(LOG_ID, "initData called")
                initialized = true
                readTargets(context)
                loadExtras(context)
                TimeTaskService.run(context)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "initData exception: " + exc.toString() + "\n" + exc.stackTraceToString() )
        }
    }

    fun getAsString(context: Context, noDataResId: Int = R.string.no_data): String {
        if (time == 0L)
            return context.getString(noDataResId)
        return (context.getString(R.string.info_label_delta) + ": " + getDeltaAsString() + " " + getUnit() + "\r\n" +
                context.getString(R.string.info_label_rate) + ": " + rate + "\r\n" +
                context.getString(R.string.info_label_timestamp) + ": " + DateFormat.getTimeInstance(DateFormat.DEFAULT).format(Date(time)) + "\r\n" +
                context.getString(R.string.info_label_alarm) + ": " + context.getString(getAlarmType().resId) + (if (forceAlarm) " ⚠" else "" ) + " (" + alarm + ")\r\n" +
                (if (isMmol) context.getString(R.string.info_label_raw) + ": " + rawValue + " mg/dl\r\n" else "") +
                (       if (NightscoutSourceTask.iobCobSupport && iobCobTime > 0) {
                            context.getString(R.string.info_label_iob) + ": " + getIobAsString() + " / " + context.getString(R.string.info_label_cob) + ": " + getCobAsString() + "\r\n" +
                                    context.getString(R.string.info_label_iob_cob_timestamp) + ": " + DateFormat.getTimeInstance(DateFormat.DEFAULT).format(Date(iobCobTime)) + "\r\n"
                        }
                        else "" ) +
                (if (sensorID.isNullOrEmpty()) "" else context.getString(R.string.info_label_sensor_id) + ": " + (if(BuildConfig.DEBUG) "ABCDE12345" else sensorID) + "\r\n") +
                context.getString(R.string.info_label_source) + ": " + context.getString(source.resId)
                )
    }

    fun isObsolete(timeoutSec: Int = Constants.VALUE_OBSOLETE_LONG_SEC): Boolean = (System.currentTimeMillis()- time) >= (timeoutSec * 1000)
    fun isIobCobObsolete(timeoutSec: Int = Constants.VALUE_OBSOLETE_LONG_SEC): Boolean = (System.currentTimeMillis()- iobCobTime) >= (timeoutSec * 1000)

    fun getClucoseAsString(): String {
        if(isObsolete())
            return "---"
        if (isMmol)
            return glucose.toString()
        return rawValue.toString()
    }

    fun getDeltaAsString(): String {
        if(isObsolete(Constants.VALUE_OBSOLETE_SHORT_SEC) || deltaValue.isNaN())
            return "--"
        var deltaVal = ""
        if (delta > 0)
            deltaVal += "+"
        if( !isMmol && delta.toDouble() == Math.floor(delta.toDouble()) )
            deltaVal += delta.toInt().toString()
        else
            deltaVal += delta.toString()
        return deltaVal
    }

    fun getRateAsString(): String {
        if(isObsolete(Constants.VALUE_OBSOLETE_SHORT_SEC))
            return "--"
        return (if (rate > 0) "+" else "") + rate.toString()
    }

    fun getUnit(): String {
        if (isMmol)
            return "mmol/l"
        return "mg/dl"
    }

    fun isIobCob() : Boolean {
        if (isIobCobObsolete(Constants.VALUE_OBSOLETE_LONG_SEC)) {
            iob = Float.NaN
            cob = Float.NaN
        }
        return !iob.isNaN() || !cob.isNaN()
    }

    val iobString: String get() {
        if (isIobCobObsolete(Constants.VALUE_OBSOLETE_LONG_SEC))
            iob = Float.NaN
        if(iob.isNaN() || isIobCobObsolete(Constants.VALUE_OBSOLETE_SHORT_SEC)) {
            return " - "
        }
        return "%.2f".format(Locale.ROOT, iob)
    }
    val cobString: String get() {
        if (isIobCobObsolete(Constants.VALUE_OBSOLETE_LONG_SEC))
            cob = Float.NaN
        if(cob.isNaN() || isIobCobObsolete(Constants.VALUE_OBSOLETE_SHORT_SEC)) {
            return " - "
        }
        return Utils.round(cob, 0).toInt().toString()
    }

    fun getIobAsString(withUnit: Boolean = true): String {
        if (withUnit)
            return iobString + "U"
        return iobString
    }
    fun getCobAsString(withUnit: Boolean = true): String {
        if (withUnit)
            return cobString + "g"
        return cobString
    }

    // alarm bits: 1111 -> first: force alarm (8), second: high or low alarm (4), last: low (1) 
    /* examples: 
        6 = 0110 -> very high
        2 = 0010 -> high (out of range)
        3 = 0011 -> low (out of range)
        7 = 0111 -> very low
    */
    fun getAlarmType(): AlarmType {
        if(isObsolete(Constants.VALUE_OBSOLETE_SHORT_SEC))
            return AlarmType.NONE
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
        setForceAlarm(checkForceAlarm(curAlarmType), curAlarmType)
        if (curAlarm != 0 && forceAlarm) {
            return curAlarm or 8
        }
        return curAlarm
    }

    private fun checkForceAlarm(curAlarmType: AlarmType): Boolean {
        Log.d(LOG_ID, "Check force alarm:" +
            " - curAlarmType=" + curAlarmType.toString() +
            " - lastAlarmType=" + lastAlarmType.toString() +
            " - lastAlarmTime=" +  DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(lastAlarmTime)) +
            " - time=" + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(time)) +
            " - delta=" + delta.toString() +
            " - rate=" + rate.toString() +
            " - diff=" + (time - lastAlarmTime).toString() +
            " - lowDur=" + lowAlarmDuration.toString() +
            " - highDur=" + highAlarmDuration.toString()
                    )
        when(curAlarmType) {
            AlarmType.LOW,
            AlarmType.VERY_LOW -> {
                if(curAlarmType < lastAlarmType || ((delta < 0F || rate < 0F) && (time - lastAlarmTime >= lowAlarmDuration)))
                {
                    return true
                }
                return false
            }
            AlarmType.HIGH,
            AlarmType.VERY_HIGH -> {
                if(curAlarmType > lastAlarmType || ((delta > 0F || rate > 0F) && (time - lastAlarmTime >= highAlarmDuration)))
                {
                    return true
                }
                return false
            }
            else -> return false
        }
    }

    private fun setForceAlarm(force: Boolean, curAlarmType: AlarmType) {
        forceAlarm = force
        if (forceAlarm) {
            lastAlarmTime = time
            lastAlarmType = curAlarmType
            Log.i(LOG_ID, "Force alarm for type " + curAlarmType)
        }
    }

    fun getClucoseColor(monoChrome: Boolean = false): Int {
        if(isObsolete(Constants.VALUE_OBSOLETE_SHORT_SEC))
            return Color.GRAY
        if (monoChrome)
            return Color.WHITE

        return when(getAlarmType()) {
            AlarmType.NONE -> Color.GRAY
            AlarmType.VERY_LOW -> colorAlarm
            AlarmType.LOW -> colorOutOfRange
            AlarmType.OK -> colorOK
            AlarmType.HIGH -> colorOutOfRange
            AlarmType.VERY_HIGH -> colorAlarm
        }
    }

    fun getTimeDiffMinute(new_time: Long): Long {
        return Utils.round((new_time-time).toFloat()/60000, 0).toLong()
    }

    fun getElapsedTimeMinute(roundingMode: RoundingMode = RoundingMode.DOWN): Long {
        return Utils.round((System.currentTimeMillis()-time).toFloat()/60000, 0, roundingMode).toLong()
    }
    fun getElapsedIobCobTimeMinute(roundingMode: RoundingMode = RoundingMode.HALF_UP): Long {
        return Utils.round((System.currentTimeMillis()- iobCobTime).toFloat()/60000, 0, roundingMode).toLong()
    }

    fun getElapsedTimeMinuteAsString(context: Context, short: Boolean = true): String {
        if (time == 0L)
            return "--"
        if (ElapsedTimeTask.relativeTime) {
            val elapsed_time = getElapsedTimeMinute()
            if (elapsed_time > 60)
                return context.getString(R.string.elapsed_time_hour)
            return context.getString(R.string.elapsed_time, elapsed_time)
        } else if (short)
            return DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(time))
        else
            return DateFormat.getTimeInstance(DateFormat.DEFAULT).format(Date(time))
    }

    fun handleIntent(context: Context, dataSource: DataSource, extras: Bundle?, interApp: Boolean = false) : Boolean
    {
        if (extras == null || extras.isEmpty) {
            return false
        }
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

                if(getTimeDiffMinute(new_time) >= 1) // check for new value received (diff must around one minute at least to prevent receiving same data from different sources with similar timestamps
                {
                    Log.i(
                        LOG_ID, "Glucodata received from " + dataSource.toString() + ": " +
                                extras.toString() +
                                " - timestamp: " + DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT).format(Date(extras.getLong(TIME)))
                    )
                    source = dataSource
                    sensorID = extras.getString(SERIAL) //Name of sensor
                    rate = extras.getFloat(RATE) //Rate of change of glucose. See libre and dexcom label functions
                    rateLabel = GlucoDataUtils.getRateLabel(context, rate)

                    if (extras.containsKey(DELTA)) {
                        deltaValue = extras.getFloat(DELTA, Float.NaN)
                    } else if (time > 0) {
                        // calculate delta value
                        timeDiff = new_time-time
                        val timeDiffMinute = getTimeDiffMinute(new_time)
                        if (timeDiffMinute == 0L) {
                            Log.w(LOG_ID, "Time diff is less than a minute! Can not calculate delta value!")
                            deltaValue = Float.NaN
                        } else if (timeDiffMinute > 10L) {
                            deltaValue = Float.NaN   // no delta calculation for too high time diffs
                        } else {
                            deltaValue = (extras.getInt(MGDL) - rawValue).toFloat()
                            val deltaTime = if(use5minDelta) 5L else 1L
                            if(timeDiffMinute != deltaTime) {
                                val factor: Float = timeDiffMinute.toFloat() / deltaTime.toFloat()
                                Log.d(LOG_ID, "Divide delta " + deltaValue.toString() + " with factor " + factor.toString() + " for time diff: " + timeDiffMinute.toString() + " minute(s)")
                                deltaValue /= factor
                            }
                        }
                    } else {
                        deltaValue = Float.NaN
                    }

                    rawValue = extras.getInt(MGDL)
                    if (extras.containsKey(GLUCOSECUSTOM)) {
                        glucose = Utils.round(extras.getFloat(GLUCOSECUSTOM), 1) //Glucose value in unit in setting
                        changeIsMmol(rawValue!=glucose.toInt() && GlucoDataUtils.isMmolValue(glucose), context)
                    } else {
                        glucose = rawValue.toFloat()
                        if (isMmol) {
                            glucose = GlucoDataUtils.mgToMmol(glucose)
                        }
                    }
                    time = extras.getLong(TIME) //time in msec

                    if(extras.containsKey(IOB) || extras.containsKey(COB)) {
                        iob = extras.getFloat(IOB, Float.NaN)
                        cob = extras.getFloat(COB, Float.NaN)
                        if(extras.containsKey(IOBCOB_TIME))
                            iobCobTime = extras.getLong(IOBCOB_TIME)
                        else
                            iobCobTime = time
                    }

                    // check for alarm
                    if (interApp) {
                        alarm = extras.getInt(ALARM) // if bit 8 is set, then an alarm is triggered
                        setForceAlarm((alarm and 8) == 8, getAlarmType())
                    } else {
                        alarm = calculateAlarm()
                    }
                    val notifySource = if(interApp) NotifySource.MESSAGECLIENT else NotifySource.BROADCAST

                    InternalNotifier.notify(context, notifySource, createExtras())  // re-create extras to have all changed value inside...
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

    fun handleIobCob(context: Context, dataSource: DataSource, extras: Bundle, interApp: Boolean = false) {
        Log.v(LOG_ID, "handleIobCob for source " + dataSource + ": " + extras.toString())
        if (extras.containsKey(IOB) || extras.containsKey(COB)) {
            if (!isIobCob() && extras.getFloat(IOB, Float.NaN).isNaN() && extras.getFloat(COB, Float.NaN).isNaN()) {
                Log.d(LOG_ID, "Ignore NaN IOB and COB")
                return
            }

            val newTime = if(extras.containsKey(IOBCOB_TIME))
                extras.getLong(IOBCOB_TIME)
            else
                System.currentTimeMillis()

            if(!isIobCob() || (newTime > iobCobTime && (newTime-iobCobTime) > 30000)) {
                Log.i(LOG_ID, "Only IOB/COB changed: " + extras.getFloat(IOB, Float.NaN) + "/" +  extras.getFloat(COB, Float.NaN))
                iob = extras.getFloat(IOB, Float.NaN)
                cob = extras.getFloat(COB, Float.NaN)
                iobCobTime = newTime

                // do not forward extras as interApp to prevent sending back to source...
                val extras: Bundle? = if(interApp) null else createExtras()  // re-create extras to have all changed value inside for sending to receiver
                InternalNotifier.notify(context, NotifySource.IOB_COB_CHANGE, extras)
                saveExtras(context)
            }
        }
    }

    fun changeIsMmol(newValue: Boolean, context: Context? = null) {
        if (isMmol != newValue) {
            isMmolValue = newValue
            if (isMmolValue != GlucoDataUtils.isMmolValue(glucose)) {
                if (isMmolValue)
                    glucose = GlucoDataUtils.mgToMmol(glucose)
                else
                    glucose = GlucoDataUtils.mmolToMg(glucose)
            }
            Log.i(LOG_ID, "Unit changed to " + glucose + if(isMmolValue) "mmol/l" else "mg/dl")
            if (context != null) {
                val sharedPref =
                    context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
                with(sharedPref.edit()) {
                    putBoolean(Constants.SHARED_PREF_USE_MMOL, isMmol)
                    apply()
                }
            }
        }
    }

    fun setSettings(context: Context, bundle: Bundle) {
        val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
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
            putLong(Constants.SHARED_PREF_NOTIFY_DURATION_LOW, bundle.getLong(Constants.SHARED_PREF_NOTIFY_DURATION_LOW, lowAlarmDuration/60000))
            putLong(Constants.SHARED_PREF_NOTIFY_DURATION_HIGH, bundle.getLong(Constants.SHARED_PREF_NOTIFY_DURATION_HIGH, highAlarmDuration/60000))
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
        bundle.getLong(Constants.SHARED_PREF_NOTIFY_DURATION_LOW, lowAlarmDuration/60000)
        bundle.getLong(Constants.SHARED_PREF_NOTIFY_DURATION_HIGH, highAlarmDuration/60000)
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
        lowAlarmDuration = sharedPref.getLong(Constants.SHARED_PREF_NOTIFY_DURATION_LOW, lowAlarmDuration/60000)*60000
        highAlarmDuration = sharedPref.getLong(Constants.SHARED_PREF_NOTIFY_DURATION_HIGH, highAlarmDuration/60000)*60000
        changeIsMmol(sharedPref.getBoolean(Constants.SHARED_PREF_USE_MMOL, isMmol))
        calculateAlarm()  // re-calculate alarm with new settings
        Log.i(LOG_ID, "Raw low/min/max/high set: " + lowValue.toString() + "/" + targetMinValue.toString() + "/" + targetMaxValue.toString() + "/" + highValue.toString()
                + " mg/dl - unit: " + getUnit()
                + " - 5 min delta: " + use5minDelta
                + " - alarm/out/ok colors: " + colorAlarm.toString() + "/" + colorOutOfRange.toString() + "/" + colorOK.toString())
    }

    private fun readTargets(context: Context) {
        val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
        sharedPref.registerOnSharedPreferenceChangeListener(this)
        if(!sharedPref.contains(Constants.SHARED_PREF_USE_MMOL)) {
            Log.i(LOG_ID, "Upgrade to new mmol handling!")
            isMmolValue = GlucoDataUtils.isMmolValue(targetMinValue)
            if (isMmol) {
                writeTarget(context, true, targetMinValue)
                writeTarget(context, false, targetMaxValue)
            }
            with(sharedPref.edit()) {
                putBoolean(Constants.SHARED_PREF_USE_MMOL, isMmol)
                apply()
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

    fun createExtras(): Bundle? {
        if(time == 0L)
            return null
        val extras = Bundle()
        extras.putLong(TIME, time)
        extras.putFloat(GLUCOSECUSTOM, glucose)
        extras.putInt(MGDL, rawValue)
        extras.putString(SERIAL, sensorID)
        extras.putFloat(RATE, rate)
        extras.putInt(ALARM, alarm)
        extras.putFloat(DELTA, deltaValue)
        extras.putFloat(IOB, iob)
        extras.putFloat(COB, cob)
        extras.putLong(IOBCOB_TIME, iobCobTime)
        return extras
    }

    private fun saveExtras(context: Context) {
        try {
            Log.d(LOG_ID, "Saving extras")
            // use own tag to prevent trigger onChange event at every time!
            val sharedGlucosePref = context.getSharedPreferences(Constants.GLUCODATA_BROADCAST_ACTION, Context.MODE_PRIVATE)
            with(sharedGlucosePref.edit()) {
                putLong(TIME, time)
                putFloat(GLUCOSECUSTOM, glucose)
                putInt(MGDL, rawValue)
                putString(SERIAL, sensorID)
                putFloat(RATE, rate)
                putInt(ALARM, alarm)
                putFloat(DELTA, deltaValue)
                putFloat(IOB, iob)
                putFloat(COB, cob)
                putLong(IOBCOB_TIME, iobCobTime)
                putInt(SOURCE_INDEX, source.ordinal)
                putLong(LAST_ALARM_TIME, lastAlarmTime)
                putInt(LAST_ALARM_INDEX, lastAlarmType.ordinal)
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
                    extras.putFloat(RATE, sharedGlucosePref.getFloat(RATE, rate))
                    extras.putInt(ALARM, sharedGlucosePref.getInt(ALARM, alarm))
                    extras.putFloat(DELTA, sharedGlucosePref.getFloat(DELTA, deltaValue))
                    extras.putFloat(IOB, sharedGlucosePref.getFloat(IOB, iob))
                    extras.putFloat(COB, sharedGlucosePref.getFloat(COB, cob))
                    extras.putLong(IOBCOB_TIME, sharedGlucosePref.getLong(IOBCOB_TIME, iobCobTime))
                    lastAlarmType = AlarmType.fromIndex(sharedGlucosePref.getInt(LAST_ALARM_INDEX, AlarmType.NONE.ordinal))
                    lastAlarmTime = sharedGlucosePref.getLong(LAST_ALARM_TIME, 0L)
                    handleIntent(context, DataSource.fromIndex(sharedGlucosePref.getInt(SOURCE_INDEX,
                        DataSource.NONE.ordinal)), extras)
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
                    Constants.SHARED_PREF_COLOR_OK -> {
                        updateSettings(sharedPreferences!!)
                        val extras = Bundle()
                        extras.putBundle(Constants.SETTINGS_BUNDLE, getSettingsBundle())
                        InternalNotifier.notify(
                            GlucoDataService.context!!,
                            NotifySource.SETTINGS,
                            extras
                        )
                    }
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString() + "\n" + exc.stackTraceToString() )
        }
    }
}
