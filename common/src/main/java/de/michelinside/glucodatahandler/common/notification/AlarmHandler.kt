package de.michelinside.glucodatahandler.common.notification

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import java.text.DateFormat
import java.time.Duration
import java.util.Date

object AlarmHandler: SharedPreferences.OnSharedPreferenceChangeListener {
    private const val LOG_ID = "GDH.AlarmHandler"

    private const val LAST_ALARM_INDEX = "last_alarm_index"
    private const val LAST_ALARM_TIME = "last_alarm_time"
    private const val SNOOZE_TIME = "snooze_time"

    private var veryLowEnabled = true
    private var veryLowInterval = 15*60000 // ms -> 15 minutes
    private var lowEnabled = true
    private var lowInterval = 25*60000 // ms -> 25 minutes
    private var highEnabled = true
    private var highInterval = 30*60000 // ms -> 30 minutes
    private var veryHighEnabled = true
    private var veryHighInterval = 25*60000 // ms -> 25 minutes


    private var lastAlarmTime = 0L
    private var lastAlarmType = AlarmType.OK
    private var initialized = false
    private var snoozeTime = 0L
    private lateinit var sharedExtraPref: SharedPreferences

    val isSnoozeActive: Boolean get() {
        return snoozeTime >= System.currentTimeMillis()
    }

    val snoozeTimestamp: String get() {
        return DateFormat.getTimeInstance(DateFormat.DEFAULT).format(Date(snoozeTime))
    }

    fun initData(context: Context) {
        try {
            if (!initialized) {
                Log.v(LOG_ID, "initData called")
                val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
                sharedPref.registerOnSharedPreferenceChangeListener(this)
                updateSettings(sharedPref)
                sharedExtraPref = context.getSharedPreferences(Constants.SHARED_PREF_EXTRAS_TAG, Context.MODE_PRIVATE)
                loadExtras()
                initialized = true
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "initData exception: " + exc.toString() + "\n" + exc.stackTraceToString() )
        }
    }

    fun checkForAlarmTrigger(newAlarmType: AlarmType): Boolean {
        Log.d(
            LOG_ID, "Check force alarm:" +
                " - newAlarmType=" + newAlarmType.toString() +
                " - lastAlarmType=" + lastAlarmType.toString() +
                " - lastAlarmTime=" +  DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(lastAlarmTime)) +
                " - snoozeTime=" + snoozeTimestamp +
                " - time=" + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ReceiveData.time)) +
                " - delta=" + ReceiveData.delta.toString() +
                " - rate=" + ReceiveData.rate.toString() +
                " - diff=" + (ReceiveData.time - lastAlarmTime).toString() +
                " - veryLowInt=>" + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(lastAlarmTime+veryLowInterval)) +
                " - lowInt=>" + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(lastAlarmTime+lowInterval)) +
                " - highInt=>" + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(lastAlarmTime+highInterval)) +
                " - veryHighInt=>" + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(lastAlarmTime+veryHighInterval))
        )
        if (isSnoozeActive)
            return false
        val triggerAlarm = when(newAlarmType) {
            AlarmType.VERY_LOW -> veryLowEnabled && checkLowAlarm(newAlarmType, veryLowInterval)
            AlarmType.LOW -> lowEnabled && checkLowAlarm(newAlarmType, lowInterval)
            AlarmType.HIGH -> highEnabled && checkHighAlarm(newAlarmType, highInterval)
            AlarmType.VERY_HIGH -> veryHighEnabled && checkHighAlarm(newAlarmType, veryHighInterval)
            else -> false
        }
        if (triggerAlarm) {
            setLastAlarm(newAlarmType)
            Log.i(LOG_ID, "Trigger alarm for type $newAlarmType")
        }
        return triggerAlarm
    }

    fun setLastAlarm(alarmType: AlarmType) {
        Log.v(LOG_ID, "Set last alarm type to $alarmType")
        lastAlarmTime = ReceiveData.time
        lastAlarmType = alarmType
        saveExtras()
    }

    fun setSnooze(minutes: Long) {
        Log.v(LOG_ID, "Set snooze to $minutes minutes")
        snoozeTime = System.currentTimeMillis() + Duration.ofMinutes(minutes).toMillis()
        saveExtras()
    }

    private fun checkHighAlarm(newAlarmType: AlarmType, alarmInterval: Int): Boolean {
        if(newAlarmType > lastAlarmType)
            return true
        if (ReceiveData.time - lastAlarmTime >= alarmInterval) {
            if(ReceiveData.delta.isNaN())
                return ReceiveData.rate > 0F
            return ReceiveData.delta > 0F
        }
        return false
    }

    private fun checkLowAlarm(newAlarmType: AlarmType, alarmInterval: Int): Boolean {
        if(newAlarmType < lastAlarmType)
            return true
        if (ReceiveData.time - lastAlarmTime >= alarmInterval) {
            if(ReceiveData.delta.isNaN())
                return ReceiveData.rate < 0F
            return ReceiveData.delta < 0F
        }
        return false
    }

    private fun saveExtras() {
        Log.d(LOG_ID, "Saving extras")
        with(sharedExtraPref.edit()) {
            putLong(LAST_ALARM_TIME, lastAlarmTime)
            putInt(LAST_ALARM_INDEX, lastAlarmType.ordinal)
            putLong(SNOOZE_TIME, snoozeTime)
            apply()
        }
        InternalNotifier.notify(GlucoDataService.context!!, NotifySource.ALARM_SETTINGS, null)
    }

    private fun loadExtras() {
        try {
            Log.i(LOG_ID, "Reading saved values...")
            lastAlarmType = AlarmType.fromIndex(sharedExtraPref.getInt(LAST_ALARM_INDEX, AlarmType.NONE.ordinal))
            lastAlarmTime = sharedExtraPref.getLong(LAST_ALARM_TIME, 0L)
            snoozeTime = sharedExtraPref.getLong(SNOOZE_TIME, 0L)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Loading receivers exception: " + exc.toString() + "\n" + exc.stackTraceToString() )
        }
    }

    val alarmPreferencesToSend = mutableSetOf(
        Constants.SHARED_PREF_ALARM_VERY_LOW_ENABLED,
        Constants.SHARED_PREF_ALARM_VERY_LOW_INTERVAL,
        Constants.SHARED_PREF_ALARM_LOW_ENABLED,
        Constants.SHARED_PREF_ALARM_LOW_INTERVAL,
        Constants.SHARED_PREF_ALARM_HIGH_ENABLED,
        Constants.SHARED_PREF_ALARM_HIGH_INTERVAL,
        Constants.SHARED_PREF_ALARM_VERY_HIGH_ENABLED,
        Constants.SHARED_PREF_ALARM_VERY_HIGH_INTERVAL,
    )

    fun getSettings(): Bundle {
        val bundle = Bundle()
        bundle.putInt(Constants.SHARED_PREF_ALARM_VERY_LOW_INTERVAL, veryLowInterval/60000)
        bundle.putInt(Constants.SHARED_PREF_ALARM_LOW_INTERVAL, lowInterval/60000)
        bundle.putInt(Constants.SHARED_PREF_ALARM_HIGH_INTERVAL, highInterval/60000)
        bundle.putInt(Constants.SHARED_PREF_ALARM_VERY_HIGH_INTERVAL, veryHighInterval/60000)
        bundle.putBoolean(Constants.SHARED_PREF_ALARM_VERY_LOW_ENABLED, veryLowEnabled)
        bundle.putBoolean(Constants.SHARED_PREF_ALARM_LOW_ENABLED, lowEnabled)
        bundle.putBoolean(Constants.SHARED_PREF_ALARM_HIGH_ENABLED, highEnabled)
        bundle.putBoolean(Constants.SHARED_PREF_ALARM_VERY_HIGH_ENABLED, veryHighEnabled)
        bundle.putLong(SNOOZE_TIME, snoozeTime)
        bundle.putLong(LAST_ALARM_TIME, lastAlarmTime)
        bundle.putInt(LAST_ALARM_INDEX, lastAlarmType.ordinal)
        return bundle
    }

    fun setSettings(context: Context, bundle: Bundle) {
        val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putInt(Constants.SHARED_PREF_ALARM_VERY_LOW_INTERVAL, bundle.getInt(Constants.SHARED_PREF_ALARM_VERY_LOW_INTERVAL, veryLowInterval/60000))
            putInt(Constants.SHARED_PREF_ALARM_LOW_INTERVAL, bundle.getInt(Constants.SHARED_PREF_ALARM_LOW_INTERVAL, lowInterval/60000))
            putInt(Constants.SHARED_PREF_ALARM_HIGH_INTERVAL, bundle.getInt(Constants.SHARED_PREF_ALARM_HIGH_INTERVAL, highInterval/60000))
            putInt(Constants.SHARED_PREF_ALARM_VERY_HIGH_INTERVAL, bundle.getInt(Constants.SHARED_PREF_ALARM_VERY_HIGH_INTERVAL, veryHighInterval/60000))
            putBoolean(Constants.SHARED_PREF_ALARM_VERY_LOW_ENABLED, bundle.getBoolean(Constants.SHARED_PREF_ALARM_VERY_LOW_ENABLED, veryLowEnabled))
            putBoolean(Constants.SHARED_PREF_ALARM_LOW_ENABLED, bundle.getBoolean(Constants.SHARED_PREF_ALARM_LOW_ENABLED, lowEnabled))
            putBoolean(Constants.SHARED_PREF_ALARM_HIGH_ENABLED, bundle.getBoolean(Constants.SHARED_PREF_ALARM_HIGH_ENABLED, highEnabled))
            putBoolean(Constants.SHARED_PREF_ALARM_VERY_HIGH_ENABLED, bundle.getBoolean(Constants.SHARED_PREF_ALARM_VERY_HIGH_ENABLED, veryHighEnabled))
            apply()
        }
        lastAlarmType = AlarmType.fromIndex(bundle.getInt(LAST_ALARM_INDEX, lastAlarmType.ordinal))
        lastAlarmTime = bundle.getLong(LAST_ALARM_TIME, lastAlarmTime)
        snoozeTime = bundle.getLong(SNOOZE_TIME, snoozeTime)
        updateSettings(sharedPref)
    }

    fun updateSettings(sharedPref: SharedPreferences) {
        veryLowInterval = sharedPref.getInt(Constants.SHARED_PREF_ALARM_VERY_LOW_INTERVAL, veryLowInterval/60000)*60000
        lowInterval = sharedPref.getInt(Constants.SHARED_PREF_ALARM_LOW_INTERVAL, lowInterval/60000)*60000
        highInterval = sharedPref.getInt(Constants.SHARED_PREF_ALARM_HIGH_INTERVAL, highInterval/60000)*60000
        veryHighInterval = sharedPref.getInt(Constants.SHARED_PREF_ALARM_VERY_HIGH_INTERVAL, veryHighInterval/60000)*60000
        veryLowEnabled = sharedPref.getBoolean(Constants.SHARED_PREF_ALARM_VERY_LOW_ENABLED, veryLowEnabled)
        lowEnabled = sharedPref.getBoolean(Constants.SHARED_PREF_ALARM_LOW_ENABLED, lowEnabled)
        highEnabled = sharedPref.getBoolean(Constants.SHARED_PREF_ALARM_HIGH_ENABLED, highEnabled)
        veryHighEnabled = sharedPref.getBoolean(Constants.SHARED_PREF_ALARM_VERY_HIGH_ENABLED, veryHighEnabled)
    }

    fun getDefaultIntervalMin(alarmType: AlarmType): Int {
        return when(alarmType) {
            AlarmType.VERY_LOW -> veryLowInterval/60000
            AlarmType.LOW -> lowInterval/60000
            AlarmType.HIGH -> highInterval/60000
            AlarmType.VERY_HIGH -> veryHighInterval/60000
            else -> 0
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        try {
            Log.d(LOG_ID, "onSharedPreferenceChanged called for key " + key)
            if (GlucoDataService.context != null && alarmPreferencesToSend.contains(key)) {
                updateSettings(sharedPreferences)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString() + "\n" + exc.stackTraceToString() )
        }
    }
}