package de.michelinside.glucodatahandler.common

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import de.michelinside.glucodatahandler.common.notifier.*
import de.michelinside.glucodatahandler.common.notifier.DataSource
import de.michelinside.glucodatahandler.common.tasks.ElapsedTimeTask
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
    const val SOURCE_RES_ID = "source_resid"
    private const val WAKE_LOCK_TIMEOUT = 10000L // 10 seconds

    enum class AlarmType {
        NONE,
        LOW_ALARM,
        LOW,
        OK,
        HIGH,
        HIGH_ALARM
    }

    var sensorID: String? = null
    var rawValue: Int = 0
    var glucose: Float = 0.0F
    var rate: Float = 0.0F
    var alarm: Int = 0
    var time: Long = 0
    var timeDiff: Long = 0
    var rateLabel: String? = null
    var source: DataSource = DataSource.JUGGLUCO
    var forceAlarm: Boolean = false
    private var lowValue: Float = 0F
    private val low: Float get() {
        if(isMmol && lowValue > 0F)  // mmol/l
        {
            return Utils.mgToMmol(lowValue, 1)
        }
        return lowValue
    }
    private var highValue: Float = 0F
    private val high: Float get() {
        if(isMmol && highValue > 0F)  // mmol/l
        {
            return Utils.mgToMmol(highValue, 1)
        }
        return highValue
    }
    private var targetMinValue = 90F
    val targetMin: Float get() {
        if(isMmol)  // mmol/l
        {
            return Utils.mgToMmol(targetMinValue, 1)
        }
        return targetMinValue
    }
    private var targetMaxValue = 165F
    val targetMax: Float get() {
        if(isMmol)  // mmol/l
        {
            return Utils.mgToMmol(targetMaxValue, 1)
        }
        return targetMaxValue
    }

    private var deltaValue: Float = Float.NaN
    val delta: Float get() {
        if( deltaValue.isNaN() )
            return deltaValue
        if(isMmol)  // mmol/l
        {
            return Utils.mgToMmol(deltaValue, if (abs(deltaValue) > 1.0F) 1 else 2)
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
        Log.d(LOG_ID, "init called")
    }

    fun initData(context: Context) {
        Log.d(LOG_ID, "initData called")
        try {
            if (!initialized) {
                readTargets(context)
                loadExtras(context)
                initialized = true
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "initData exception: " + exc.toString() + "\n" + exc.stackTraceToString() )
        }
    }

    fun getAsString(context: Context): String {
        if (time == 0L)
            return context.getString(R.string.no_data)
        return (context.getString(R.string.info_label_delta) + ": " + getDeltaAsString() + " " + getUnit() + "\r\n" +
                context.getString(R.string.info_label_rate) + ": " + rate + "\r\n" +
                context.getString(R.string.info_label_timestamp) + ": " + DateFormat.getTimeInstance(DateFormat.DEFAULT).format(Date(time)) + "\r\n" +
                context.getString(R.string.info_label_alarm) + ": " + alarm + "\r\n" +
                if (isMmol) context.getString(R.string.info_label_raw) + ": " + rawValue + " mg/dl\r\n" else "" ) +
                context.getString(R.string.info_label_sensor_id) + ": " + sensorID + "\r\n" +
                context.getString(R.string.info_label_source) + ": " + context.getString(source.resId)
    }

    fun isObsolete(timeoutSec: Int = Constants.VALUE_OBSOLETE_LONG_SEC): Boolean = (System.currentTimeMillis()- time) >= (timeoutSec * 1000)

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
            return AlarmType.HIGH_ALARM
        if(((alarm and 7) == 7) || (low > 0F && glucose <= low))
            return AlarmType.LOW_ALARM
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
            AlarmType.HIGH_ALARM -> 6
            AlarmType.HIGH -> 2
            AlarmType.LOW -> 3
            AlarmType.LOW_ALARM -> 7
            else -> 0
        }
        forceAlarm = checkForceAlarm(curAlarmType)
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
            AlarmType.LOW_ALARM -> {
                if(curAlarmType < lastAlarmType || ((delta < 0F || rate < 0F) && (time - lastAlarmTime >= lowAlarmDuration)))
                {
                    lastAlarmTime = time
                    lastAlarmType = curAlarmType
                    Log.i(LOG_ID, "Force low alarm for type " + curAlarmType)
                    return true
                }
                return false
            }
            AlarmType.HIGH,
            AlarmType.HIGH_ALARM -> {
                if(curAlarmType > lastAlarmType || ((delta > 0F || rate > 0F) && (time - lastAlarmTime >= highAlarmDuration)))
                {
                    lastAlarmTime = time
                    lastAlarmType = curAlarmType
                    Log.i(LOG_ID, "Force high alarm for type " + curAlarmType)
                    return true
                }
                return false
            }
            else -> return false
        }
    }

    fun getClucoseColor(monoChrome: Boolean = false): Int {
        if(isObsolete(Constants.VALUE_OBSOLETE_SHORT_SEC))
            return Color.GRAY
        if (monoChrome)
            return Color.WHITE

        return when(getAlarmType()) {
            AlarmType.NONE -> Color.GRAY
            AlarmType.LOW_ALARM -> colorAlarm
            AlarmType.LOW -> colorOutOfRange
            AlarmType.OK -> colorOK
            AlarmType.HIGH -> colorOutOfRange
            AlarmType.HIGH_ALARM -> colorAlarm
        }
    }

    fun getRateSymbol(): Char {
        if(isObsolete(Constants.VALUE_OBSOLETE_SHORT_SEC) || java.lang.Float.isNaN(rate))
            return '?'
        if (rate >= 3.0f) return '⇈'
        if (rate >= 2.0f) return '↑'
        if (rate >= 1.0f) return '↗'
        if (rate > -1.0f) return '→'
        if (rate > -2.0f) return '↘'
        if (rate > -3.0f) return '↓'
        return '⇊'
    }

    fun getDexcomLabel(): String {
        if (rate >= 3.0f) return "DoubleUp"
        if (rate >= 2.0f) return "SingleUp"
        if (rate >= 1.0f) return "FortyFiveUp"
        if (rate > -1.0f) return "Flat"
        if (rate > -2.0f) return "FortyFiveDown"
        if (rate > -3.0f) return "SingleDown"
        return if (java.lang.Float.isNaN(rate)) "" else "DoubleDown"
    }

    fun getRateFromLabel(trendLabel: String): Float {
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

    fun getRateLabel(context: Context): String {
        if (rate >= 3.0f) return context.getString(R.string.rate_double_up)
        if (rate >= 2.0f) return context.getString(R.string.rate_single_up)
        if (rate >= 1.0f) return context.getString(R.string.rate_forty_five_up)
        if (rate > -1.0f) return context.getString(R.string.rate_flat)
        if (rate > -2.0f) return context.getString(R.string.rate_forty_five_down)
        if (rate > -3.0f) return context.getString(R.string.rate_single_down)
        return if (rate.isNaN()) "" else context.getString(R.string.rate_double_down)
    }

    fun getTimeDiffMinute(new_time: Long): Long {
        return Utils.round((new_time-time).toFloat()/60000, 0).toLong()
    }

    fun getElapsedTimeMinute(roundingMode: RoundingMode = RoundingMode.DOWN): Long {
        return Utils.round((System.currentTimeMillis()-time).toFloat()/60000, 0, roundingMode).toLong()
    }

    fun getElapsedTimeMinuteAsString(context: Context, short: Boolean = true): String {
        if (time == 0L)
            return "--"
        if (ElapsedTimeTask.relativeTime) {
            val elapsed_time = getElapsedTimeMinute()
            if (elapsed_time > 60)
                return context.getString(R.string.elapsed_time_hour)
            return String.format(context.getString(R.string.elapsed_time), elapsed_time)
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
        val wakeLock: PowerManager.WakeLock =
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GlucoDataHandler::BackgroundTaskTag").apply {
                    acquire(WAKE_LOCK_TIMEOUT)
                }
            }
        try {
            val new_time = extras.getLong(TIME)
            Log.d(
                LOG_ID, "Glucodata received from " + dataSource.toString() + ": " +
                        extras.toString() +
                        " - timestamp: " + DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT).format(Date(new_time)) +
                        " - difference: " + (new_time-time)
            )

            if(getTimeDiffMinute(new_time) >= 1) // check for new value received (diff must around one minute at least to prevent receiving same data from different sources with similar timestamps
            {
                Log.i(
                    LOG_ID, "Glucodata received from " + dataSource.toString() + ": " +
                            extras.toString() +
                            " - timestamp: " + DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT).format(Date(extras.getLong(TIME)))
                )
                source = dataSource
                sensorID = extras.getString(SERIAL) //Name of sensor
                glucose = Utils.round(extras.getFloat(GLUCOSECUSTOM), 1) //Glucose value in unit in setting
                rate = extras.getFloat(RATE) //Rate of change of glucose. See libre and dexcom label functions
                rateLabel = getRateLabel(context)
                alarm = extras.getInt(ALARM) // if bit 8 is set, then an alarm is triggered
                deltaValue = Float.NaN
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
                }
                rawValue = extras.getInt(MGDL)
                time = extras.getLong(TIME) //time in msec
                changeIsMmol(rawValue!=glucose.toInt(), context)

                // check for alarm
                alarm = calculateAlarm()
                val notifySource = if(interApp) NotifySource.MESSAGECLIENT else NotifySource.BROADCAST

                InternalNotifier.notify(context, notifySource, createExtras())  // re-create extras to have all changed value inside...
                saveExtras(context)
                result = true
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Receive exception: " + exc.toString() + "\n" + exc.stackTraceToString() )
        } finally {
            wakeLock.release()
        }
        return result
    }

    fun changeIsMmol(newValue: Boolean, context: Context) {
        if (isMmol != newValue) {
            val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            isMmolValue = newValue
            Log.i(LOG_ID, "Unit changed to " + if(isMmolValue) "mmol/l" else "mg/dl")
            with(sharedPref.edit()) {
                putBoolean(Constants.SHARED_PREF_USE_MMOL, isMmol)
                apply()
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
        isMmolValue = sharedPref.getBoolean(Constants.SHARED_PREF_USE_MMOL, isMmol)
        use5minDelta = sharedPref.getBoolean(Constants.SHARED_PREF_FIVE_MINUTE_DELTA, use5minDelta)
        colorOK = sharedPref.getInt(Constants.SHARED_PREF_COLOR_OK, colorOK)
        colorOutOfRange = sharedPref.getInt(Constants.SHARED_PREF_COLOR_OUT_OF_RANGE, colorOutOfRange)
        colorAlarm = sharedPref.getInt(Constants.SHARED_PREF_COLOR_ALARM, colorAlarm)        
        lowAlarmDuration = sharedPref.getLong(Constants.SHARED_PREF_NOTIFY_DURATION_LOW, lowAlarmDuration/60000)*60000
        highAlarmDuration = sharedPref.getLong(Constants.SHARED_PREF_NOTIFY_DURATION_HIGH, highAlarmDuration/60000)*60000
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
            isMmolValue = Utils.isMmolValue(targetMinValue)
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
        if (Utils.isMmolValue(mgdlValue)) {
            mgdlValue = Utils.mmolToMg(value)
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
                putInt(SOURCE_RES_ID, source.resId)
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
                    Log.i(LOG_ID, "Read saved values...")
                    val extras = Bundle()
                    extras.putLong(TIME, sharedGlucosePref.getLong(TIME, time))
                    extras.putFloat(GLUCOSECUSTOM, sharedGlucosePref.getFloat(GLUCOSECUSTOM, glucose))
                    extras.putInt(MGDL, sharedGlucosePref.getInt(MGDL, rawValue))
                    extras.putString(SERIAL, sharedGlucosePref.getString(SERIAL, sensorID))
                    extras.putFloat(RATE, sharedGlucosePref.getFloat(RATE, rate))
                    extras.putInt(ALARM, sharedGlucosePref.getInt(ALARM, alarm))
                    extras.putFloat(DELTA, sharedGlucosePref.getFloat(DELTA, deltaValue))
                    handleIntent(context, DataSource.fromResId(sharedGlucosePref.getInt(SOURCE_RES_ID,
                        DataSource.JUGGLUCO.resId)), extras)
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
