package de.michelinside.glucodatahandler.common.notification

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import java.text.DateFormat
import java.util.Date

object AlarmHandler: SharedPreferences.OnSharedPreferenceChangeListener {
    private const val LOG_ID = "GDH.AlarmHandler"

    private const val LAST_ALARM_INDEX = "last_alarm_index"
    private const val LAST_ALARM_TIME = "last_alarm_time"

    private var veryLowInterval = 15*60000 // ms -> 15 minutes
    private var lowInterval = 25*60000 // ms -> 25 minutes
    private var highInterval = 30*60000 // ms -> 35 minutes
    private var veryHighInterval = 30*60000 // ms -> 30 minutes
    private var lastAlarmTime = 0L
    private var lastAlarmType = AlarmType.OK
    private var initialized = false
    private lateinit var sharedExtraPref: SharedPreferences

    fun initData(context: Context) {
        try {
            if (!initialized) {
                Log.v(LOG_ID, "initData called")
                val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
                sharedPref.registerOnSharedPreferenceChangeListener(this)
                updateSettings(sharedPref)
                sharedExtraPref = context.getSharedPreferences(Constants.SHARED_PREF_EXTRAS_TAG, Context.MODE_PRIVATE)
                loadExtras(context)
                AlarmNotification.initNotifications(context)
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
                " - time=" + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ReceiveData.time)) +
                " - delta=" + ReceiveData.delta.toString() +
                " - rate=" + ReceiveData.rate.toString() +
                " - diff=" + (ReceiveData.time - lastAlarmTime).toString() +
                " - veryLowInt=" + veryLowInterval.toString() +
                " - lowInt=" + lowInterval.toString() +
                " - highInt=" + highInterval.toString() +
                " - veryHighInt=" + veryHighInterval.toString()
        )
        val triggerAlarm = when(newAlarmType) {
            AlarmType.VERY_LOW -> checkLowAlarm(newAlarmType, veryLowInterval)
            AlarmType.LOW -> checkLowAlarm(newAlarmType, lowInterval)
            AlarmType.HIGH -> checkHighAlarm(newAlarmType, highInterval)
            AlarmType.VERY_HIGH -> checkHighAlarm(newAlarmType, veryHighInterval)
            else -> false
        }
        if (triggerAlarm) {
            lastAlarmTime = ReceiveData.time
            lastAlarmType = newAlarmType
            saveExtras()
            Log.i(LOG_ID, "Trigger alarm for type $newAlarmType")
        }
        return triggerAlarm
    }

    private fun checkHighAlarm(newAlarmType: AlarmType, alarmInterval: Int): Boolean {
        if(newAlarmType > lastAlarmType || ((ReceiveData.delta > 0F || ReceiveData.rate > 0F) && (ReceiveData.time - lastAlarmTime >= alarmInterval)))
        {
            return true
        }
        return false
    }

    private fun checkLowAlarm(newAlarmType: AlarmType, alarmInterval: Int): Boolean {
        if(newAlarmType < lastAlarmType || ((ReceiveData.delta < 0F || ReceiveData.rate < 0F) && (ReceiveData.time - lastAlarmTime >= alarmInterval)))
        {
            return true
        }
        return false
    }

    private fun saveExtras() {
        Log.d(LOG_ID, "Saving extras")
        with(sharedExtraPref.edit()) {
            putLong(LAST_ALARM_TIME, lastAlarmTime)
            putInt(LAST_ALARM_INDEX, lastAlarmType.ordinal)
            apply()
        }
    }

    private fun loadExtras(context: Context) {
        try {
            Log.i(LOG_ID, "Reading saved values...")
            lastAlarmType = AlarmType.fromIndex(sharedExtraPref.getInt(LAST_ALARM_INDEX, AlarmType.NONE.ordinal))
            lastAlarmTime = sharedExtraPref.getLong(LAST_ALARM_TIME, 0L)

        } catch (exc: Exception) {
            Log.e(LOG_ID, "Loading receivers exception: " + exc.toString() + "\n" + exc.stackTraceToString() )
        }
    }

    val alarmPreferencesToSend = mutableSetOf(
        Constants.SHARED_PREF_ALARM_VERY_LOW_INTERVAL,
        Constants.SHARED_PREF_ALARM_LOW_INTERVAL,
        Constants.SHARED_PREF_ALARM_HIGH_INTERVAL,
        Constants.SHARED_PREF_ALARM_VERY_HIGH_INTERVAL,
    )

    fun getSettings(): Bundle {
        val bundle = Bundle()
        bundle.getInt(Constants.SHARED_PREF_ALARM_VERY_LOW_INTERVAL, veryLowInterval/60000)
        bundle.getInt(Constants.SHARED_PREF_ALARM_LOW_INTERVAL, lowInterval/60000)
        bundle.getInt(Constants.SHARED_PREF_ALARM_HIGH_INTERVAL, highInterval/60000)
        bundle.getInt(Constants.SHARED_PREF_ALARM_VERY_HIGH_INTERVAL, veryHighInterval/60000)
        return bundle
    }

    fun setSettings(context: Context, bundle: Bundle) {
        val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putInt(Constants.SHARED_PREF_ALARM_VERY_LOW_INTERVAL, bundle.getInt(Constants.SHARED_PREF_ALARM_VERY_LOW_INTERVAL, veryLowInterval/60000))
            putInt(Constants.SHARED_PREF_ALARM_LOW_INTERVAL, bundle.getInt(Constants.SHARED_PREF_ALARM_LOW_INTERVAL, lowInterval/60000))
            putInt(Constants.SHARED_PREF_ALARM_HIGH_INTERVAL, bundle.getInt(Constants.SHARED_PREF_ALARM_HIGH_INTERVAL, highInterval/60000))
            putInt(Constants.SHARED_PREF_ALARM_VERY_HIGH_INTERVAL, bundle.getInt(Constants.SHARED_PREF_ALARM_VERY_HIGH_INTERVAL, veryHighInterval/60000))
            apply()
        }
        updateSettings(sharedPref)
    }

    fun updateSettings(sharedPref: SharedPreferences) {
        veryLowInterval = sharedPref.getInt(Constants.SHARED_PREF_ALARM_VERY_LOW_INTERVAL, veryLowInterval/60000)*60000
        lowInterval = sharedPref.getInt(Constants.SHARED_PREF_ALARM_LOW_INTERVAL, lowInterval/60000)*60000
        highInterval = sharedPref.getInt(Constants.SHARED_PREF_ALARM_HIGH_INTERVAL, highInterval/60000)*60000
        veryHighInterval = sharedPref.getInt(Constants.SHARED_PREF_ALARM_VERY_HIGH_INTERVAL, veryHighInterval/60000)*60000
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