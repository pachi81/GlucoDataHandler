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

    private var lowAlarmDuration = 15L*60*1000 // ms -> 15 minutes
    private var highAlarmDuration = 25L*60*1000 // ms -> 25 minutes
    private var lastAlarmTime = 0L
    private var lastAlarmType = AlarmType.OK
    private var initialized = false

    fun initData(context: Context) {
        try {
            if (!initialized) {
                Log.v(LOG_ID, "initData called")
                val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
                sharedPref.registerOnSharedPreferenceChangeListener(this)
                updateSettings(sharedPref)
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
                " - time=" + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ReceiveData.time)) +
                " - delta=" + ReceiveData.delta.toString() +
                " - rate=" + ReceiveData.rate.toString() +
                " - diff=" + (ReceiveData.time - lastAlarmTime).toString() +
                " - lowDur=" + lowAlarmDuration.toString() +
                " - highDur=" + highAlarmDuration.toString()
        )
        val triggerAlarm = when(newAlarmType) {
            AlarmType.LOW,
            AlarmType.VERY_LOW -> checkLowAlarm(newAlarmType, lowAlarmDuration)
            AlarmType.HIGH,
            AlarmType.VERY_HIGH -> checkHighAlarm(newAlarmType, highAlarmDuration)
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

    private fun checkHighAlarm(newAlarmType: AlarmType, alarmDuration: Long): Boolean {
        if(newAlarmType > lastAlarmType || ((ReceiveData.delta > 0F || ReceiveData.rate > 0F) && (ReceiveData.time - lastAlarmTime >= alarmDuration)))
        {
            return true
        }
        return false
    }

    private fun checkLowAlarm(newAlarmType: AlarmType, alarmDuration: Long): Boolean {
        if(newAlarmType < lastAlarmType || ((ReceiveData.delta < 0F || ReceiveData.rate < 0F) && (ReceiveData.time - lastAlarmTime >= alarmDuration)))
        {
            return true
        }
        return false
    }

    private fun saveExtras() {
        Log.d(LOG_ID, "Saving extras")
        val sharedExtraPref = GlucoDataService.context!!.getSharedPreferences(Constants.SHARED_PREF_EXTRAS_TAG, Context.MODE_PRIVATE)
        with(sharedExtraPref.edit()) {
            putLong(LAST_ALARM_TIME, lastAlarmTime)
            putInt(LAST_ALARM_INDEX, lastAlarmType.ordinal)
            apply()
        }
    }

    private fun loadExtras() {
        try {
            Log.i(LOG_ID, "Reading saved values...")
            val sharedExtraPref = GlucoDataService.context!!.getSharedPreferences(Constants.SHARED_PREF_EXTRAS_TAG, Context.MODE_PRIVATE)
            lastAlarmType = AlarmType.fromIndex(sharedExtraPref.getInt(LAST_ALARM_INDEX, AlarmType.NONE.ordinal))
            lastAlarmTime = sharedExtraPref.getLong(LAST_ALARM_TIME, 0L)

        } catch (exc: Exception) {
            Log.e(LOG_ID, "Loading receivers exception: " + exc.toString() + "\n" + exc.stackTraceToString() )
        }
    }


    val alarmPreferences = mutableSetOf(
        Constants.SHARED_PREF_NOTIFY_DURATION_LOW,
        Constants.SHARED_PREF_NOTIFY_DURATION_HIGH,
    )

    fun getSettings(): Bundle {
        val bundle = Bundle()
        bundle.getLong(Constants.SHARED_PREF_NOTIFY_DURATION_LOW, lowAlarmDuration/60000)
        bundle.getLong(Constants.SHARED_PREF_NOTIFY_DURATION_HIGH, highAlarmDuration/60000)
        return bundle
    }

    fun setSettings(context: Context, bundle: Bundle) {
        val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putLong(Constants.SHARED_PREF_NOTIFY_DURATION_LOW, bundle.getLong(Constants.SHARED_PREF_NOTIFY_DURATION_LOW, lowAlarmDuration/60000))
            putLong(Constants.SHARED_PREF_NOTIFY_DURATION_HIGH, bundle.getLong(Constants.SHARED_PREF_NOTIFY_DURATION_HIGH, highAlarmDuration/60000))
            apply()
        }
        updateSettings(sharedPref)
    }

    fun updateSettings(sharedPref: SharedPreferences) {
        lowAlarmDuration = sharedPref.getLong(Constants.SHARED_PREF_NOTIFY_DURATION_LOW, lowAlarmDuration/60000)*60000
        highAlarmDuration = sharedPref.getLong(Constants.SHARED_PREF_NOTIFY_DURATION_HIGH, highAlarmDuration/60000)*60000
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        try {
            Log.d(LOG_ID, "onSharedPreferenceChanged called for key " + key)
            if (GlucoDataService.context != null && alarmPreferences.contains(key)) {
                updateSettings(sharedPreferences)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString() + "\n" + exc.stackTraceToString() )
        }
    }
}