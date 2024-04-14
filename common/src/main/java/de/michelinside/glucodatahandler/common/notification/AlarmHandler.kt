package de.michelinside.glucodatahandler.common.notification

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import de.michelinside.glucodatahandler.common.Command
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import java.text.DateFormat
import java.time.Duration
import java.util.Date

object AlarmHandler: SharedPreferences.OnSharedPreferenceChangeListener, NotifierInterface {
    private const val LOG_ID = "GDH.AlarmHandler"

    private const val LAST_ALARM_INDEX = "last_alarm_index"
    private const val LAST_ALARM_TIME = "last_alarm_time"
    const val SNOOZE_TIME = "snooze_time"

    private var veryLowEnabled = true
    private var veryLowInterval = 15*60000 // ms -> 15 minutes
    private var lowEnabled = true
    private var lowInterval = 25*60000 // ms -> 25 minutes
    private var highEnabled = true
    private var highInterval = 30*60000 // ms -> 30 minutes
    private var veryHighEnabled = true
    private var veryHighInterval = 25*60000 // ms -> 25 minutes
    private var obsoleteEnabled = true
    private var obsoleteIntervalMin = 20

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
                updateSettings(sharedPref, context)
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
        if(minutes > 0)
            setSnoozeTime(System.currentTimeMillis() + Duration.ofMinutes(minutes).toMillis())
        else
            setSnoozeTime(0L)
    }

    fun setSnoozeTime(time: Long, fromClient: Boolean = false) {
        snoozeTime = time
        Log.d(LOG_ID, "New snooze-time: $snoozeTimestamp")
        saveExtras()
        if(GlucoDataService.context != null) {
            InternalNotifier.notify(GlucoDataService.context!!, NotifySource.ALARM_STATE_CHANGED, null)
        }
        if(!fromClient) {
            val bundle = Bundle()
            bundle.putLong(SNOOZE_TIME, snoozeTime)
            GlucoDataService.sendCommand(Command.SNOOZE_ALARM, bundle)
        }
    }

    private fun checkHighAlarm(newAlarmType: AlarmType, alarmInterval: Int): Boolean {
        if(newAlarmType > lastAlarmType)
            return true
        if (ReceiveData.time - lastAlarmTime >= alarmInterval) {
            if(ReceiveData.delta.isNaN() || ReceiveData.delta == 0F)
                return ReceiveData.rate > 0F
            return ReceiveData.delta > 0F
        }
        return false
    }

    private fun checkLowAlarm(newAlarmType: AlarmType, alarmInterval: Int): Boolean {
        if(newAlarmType < lastAlarmType)
            return true
        if (ReceiveData.time - lastAlarmTime >= alarmInterval) {
            if(ReceiveData.delta.isNaN() || ReceiveData.delta == 0F)
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
        InternalNotifier.notify(GlucoDataService.context!!, NotifySource.ALARM_SETTINGS, getSettings())
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
        Constants.SHARED_PREF_ALARM_VERY_LOW_SOUND_DELAY,
        Constants.SHARED_PREF_ALARM_LOW_ENABLED,
        Constants.SHARED_PREF_ALARM_LOW_INTERVAL,
        Constants.SHARED_PREF_ALARM_LOW_SOUND_DELAY,
        Constants.SHARED_PREF_ALARM_HIGH_ENABLED,
        Constants.SHARED_PREF_ALARM_HIGH_INTERVAL,
        Constants.SHARED_PREF_ALARM_HIGH_SOUND_DELAY,
        Constants.SHARED_PREF_ALARM_VERY_HIGH_ENABLED,
        Constants.SHARED_PREF_ALARM_VERY_HIGH_INTERVAL,
        Constants.SHARED_PREF_ALARM_VERY_HIGH_SOUND_DELAY,
        Constants.SHARED_PREF_ALARM_OBSOLETE_ENABLED,
        Constants.SHARED_PREF_ALARM_OBSOLETE_INTERVAL,
        Constants.SHARED_PREF_ALARM_OBSOLETE_SOUND_DELAY,
        Constants.SHARED_PREF_ALARM_SNOOZE_ON_NOTIFICATION,
    )

    private val alarmStatePreferences = mutableSetOf(
        Constants.SHARED_PREF_ALARM_NOTIFICATION_ENABLED,
        Constants.SHARED_PREF_NO_ALARM_NOTIFICATION_WEAR_CONNECTED,
        Constants.SHARED_PREF_NO_ALARM_NOTIFICATION_AUTO_CONNECTED,
        Constants.SHARED_PREF_NOTIFICATION_VIBRATE
    )

    fun getSettings(): Bundle {
        val bundle = Bundle()
        bundle.putInt(Constants.SHARED_PREF_ALARM_VERY_LOW_INTERVAL, veryLowInterval/60000)
        bundle.putInt(Constants.SHARED_PREF_ALARM_LOW_INTERVAL, lowInterval/60000)
        bundle.putInt(Constants.SHARED_PREF_ALARM_HIGH_INTERVAL, highInterval/60000)
        bundle.putInt(Constants.SHARED_PREF_ALARM_VERY_HIGH_INTERVAL, veryHighInterval/60000)
        bundle.putInt(Constants.SHARED_PREF_ALARM_OBSOLETE_INTERVAL, obsoleteIntervalMin)
        bundle.putBoolean(Constants.SHARED_PREF_ALARM_VERY_LOW_ENABLED, veryLowEnabled)
        bundle.putBoolean(Constants.SHARED_PREF_ALARM_LOW_ENABLED, lowEnabled)
        bundle.putBoolean(Constants.SHARED_PREF_ALARM_HIGH_ENABLED, highEnabled)
        bundle.putBoolean(Constants.SHARED_PREF_ALARM_VERY_HIGH_ENABLED, veryHighEnabled)
        bundle.putBoolean(Constants.SHARED_PREF_ALARM_OBSOLETE_ENABLED, obsoleteEnabled)
        if(AlarmNotificationBase.instance != null) {
            bundle.putBoolean(Constants.SHARED_PREF_ALARM_SNOOZE_ON_NOTIFICATION, AlarmNotificationBase.instance!!.getAddSnooze())
            bundle.putInt(Constants.SHARED_PREF_ALARM_VERY_LOW_SOUND_DELAY, AlarmNotificationBase.instance!!.getSoundDelay(AlarmType.VERY_LOW, GlucoDataService.context!!))
            bundle.putInt(Constants.SHARED_PREF_ALARM_LOW_SOUND_DELAY, AlarmNotificationBase.instance!!.getSoundDelay(AlarmType.LOW, GlucoDataService.context!!))
            bundle.putInt(Constants.SHARED_PREF_ALARM_HIGH_SOUND_DELAY, AlarmNotificationBase.instance!!.getSoundDelay(AlarmType.HIGH, GlucoDataService.context!!))
            bundle.putInt(Constants.SHARED_PREF_ALARM_VERY_HIGH_SOUND_DELAY, AlarmNotificationBase.instance!!.getSoundDelay(AlarmType.VERY_HIGH, GlucoDataService.context!!))
            bundle.putInt(Constants.SHARED_PREF_ALARM_OBSOLETE_SOUND_DELAY, AlarmNotificationBase.instance!!.getSoundDelay(AlarmType.OBSOLETE, GlucoDataService.context!!))
        }
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
            putInt(Constants.SHARED_PREF_ALARM_OBSOLETE_INTERVAL, bundle.getInt(Constants.SHARED_PREF_ALARM_OBSOLETE_INTERVAL, obsoleteIntervalMin))
            putBoolean(Constants.SHARED_PREF_ALARM_VERY_LOW_ENABLED, bundle.getBoolean(Constants.SHARED_PREF_ALARM_VERY_LOW_ENABLED, veryLowEnabled))
            putBoolean(Constants.SHARED_PREF_ALARM_LOW_ENABLED, bundle.getBoolean(Constants.SHARED_PREF_ALARM_LOW_ENABLED, lowEnabled))
            putBoolean(Constants.SHARED_PREF_ALARM_HIGH_ENABLED, bundle.getBoolean(Constants.SHARED_PREF_ALARM_HIGH_ENABLED, highEnabled))
            putBoolean(Constants.SHARED_PREF_ALARM_VERY_HIGH_ENABLED, bundle.getBoolean(Constants.SHARED_PREF_ALARM_VERY_HIGH_ENABLED, veryHighEnabled))
            putBoolean(Constants.SHARED_PREF_ALARM_OBSOLETE_ENABLED, bundle.getBoolean(Constants.SHARED_PREF_ALARM_OBSOLETE_ENABLED, obsoleteEnabled))
            if(AlarmNotificationBase.instance != null) {
                putBoolean(Constants.SHARED_PREF_ALARM_SNOOZE_ON_NOTIFICATION, bundle.getBoolean(Constants.SHARED_PREF_ALARM_SNOOZE_ON_NOTIFICATION, AlarmNotificationBase.instance!!.getAddSnooze()))
                putInt(Constants.SHARED_PREF_ALARM_VERY_LOW_SOUND_DELAY, bundle.getInt(Constants.SHARED_PREF_ALARM_VERY_LOW_SOUND_DELAY, AlarmNotificationBase.instance!!.getSoundDelay(AlarmType.VERY_LOW, GlucoDataService.context!!)))
                putInt(Constants.SHARED_PREF_ALARM_LOW_SOUND_DELAY, bundle.getInt(Constants.SHARED_PREF_ALARM_LOW_SOUND_DELAY, AlarmNotificationBase.instance!!.getSoundDelay(AlarmType.LOW, GlucoDataService.context!!)))
                putInt(Constants.SHARED_PREF_ALARM_HIGH_SOUND_DELAY, bundle.getInt(Constants.SHARED_PREF_ALARM_HIGH_SOUND_DELAY, AlarmNotificationBase.instance!!.getSoundDelay(AlarmType.HIGH, GlucoDataService.context!!)))
                putInt(Constants.SHARED_PREF_ALARM_VERY_HIGH_SOUND_DELAY, bundle.getInt(Constants.SHARED_PREF_ALARM_VERY_HIGH_SOUND_DELAY, AlarmNotificationBase.instance!!.getSoundDelay(AlarmType.VERY_HIGH, GlucoDataService.context!!)))
                putInt(Constants.SHARED_PREF_ALARM_OBSOLETE_SOUND_DELAY, bundle.getInt(Constants.SHARED_PREF_ALARM_OBSOLETE_SOUND_DELAY, AlarmNotificationBase.instance!!.getSoundDelay(AlarmType.OBSOLETE, GlucoDataService.context!!)))
            }
            apply()
        }
        lastAlarmType = AlarmType.fromIndex(bundle.getInt(LAST_ALARM_INDEX, lastAlarmType.ordinal))
        lastAlarmTime = bundle.getLong(LAST_ALARM_TIME, lastAlarmTime)
        setSnoozeTime(bundle.getLong(SNOOZE_TIME, snoozeTime), true)
        updateSettings(sharedPref, context)
        InternalNotifier.notify(context, NotifySource.ALARM_SETTINGS, null)
    }

    fun updateSettings(sharedPref: SharedPreferences, context: Context) {
        veryLowInterval = sharedPref.getInt(Constants.SHARED_PREF_ALARM_VERY_LOW_INTERVAL, veryLowInterval/60000)*60000
        lowInterval = sharedPref.getInt(Constants.SHARED_PREF_ALARM_LOW_INTERVAL, lowInterval/60000)*60000
        highInterval = sharedPref.getInt(Constants.SHARED_PREF_ALARM_HIGH_INTERVAL, highInterval/60000)*60000
        veryHighInterval = sharedPref.getInt(Constants.SHARED_PREF_ALARM_VERY_HIGH_INTERVAL, veryHighInterval/60000)*60000
        obsoleteIntervalMin = sharedPref.getInt(Constants.SHARED_PREF_ALARM_OBSOLETE_INTERVAL, obsoleteIntervalMin)
        veryLowEnabled = sharedPref.getBoolean(Constants.SHARED_PREF_ALARM_VERY_LOW_ENABLED, veryLowEnabled)
        lowEnabled = sharedPref.getBoolean(Constants.SHARED_PREF_ALARM_LOW_ENABLED, lowEnabled)
        highEnabled = sharedPref.getBoolean(Constants.SHARED_PREF_ALARM_HIGH_ENABLED, highEnabled)
        veryHighEnabled = sharedPref.getBoolean(Constants.SHARED_PREF_ALARM_VERY_HIGH_ENABLED, veryHighEnabled)
        obsoleteEnabled = sharedPref.getBoolean(Constants.SHARED_PREF_ALARM_OBSOLETE_ENABLED, obsoleteEnabled)
        checkNotifier(context)
    }

    private fun checkNotifier(context: Context) {
        if(obsoleteEnabled != InternalNotifier.hasNotifier(this)) {
            if(obsoleteEnabled) {
                InternalNotifier.addNotifier(context, this, mutableSetOf(NotifySource.TIME_VALUE))
            } else {
                InternalNotifier.remNotifier(context, this)
            }
        }
    }

    fun getDefaultIntervalMin(alarmType: AlarmType): Int {
        return when(alarmType) {
            AlarmType.VERY_LOW -> veryLowInterval/60000
            AlarmType.LOW -> lowInterval/60000
            AlarmType.HIGH -> highInterval/60000
            AlarmType.VERY_HIGH -> veryHighInterval/60000
            AlarmType.OBSOLETE -> obsoleteIntervalMin
            else -> 0
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        try {
            Log.d(LOG_ID, "onSharedPreferenceChanged called for key " + key)
            if (GlucoDataService.context != null) {
                if(alarmPreferencesToSend.contains(key))
                    updateSettings(sharedPreferences, GlucoDataService.context!!)
                if(alarmStatePreferences.contains(key))
                    InternalNotifier.notify(GlucoDataService.context!!, NotifySource.ALARM_STATE_CHANGED, null)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString() + "\n" + exc.stackTraceToString() )
        }
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            Log.d(LOG_ID, "OnNotifyData called for $dataSource")
            if(dataSource == NotifySource.TIME_VALUE) {
                checkObsoleteAlarm(context)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString() + "\n" + exc.stackTraceToString() )
        }
    }

    private fun checkObsoleteAlarm(context: Context) {
        Log.d(LOG_ID, "checkObsoleteAlarm: enabled=$obsoleteEnabled - interval=$obsoleteIntervalMin - elapsed=${ReceiveData.getElapsedTimeMinute()}")
        if(obsoleteEnabled && obsoleteIntervalMin > 0 && ReceiveData.getElapsedTimeMinute() >= obsoleteIntervalMin) {
            if (ReceiveData.getElapsedTimeMinute().mod(obsoleteIntervalMin) == 0) {
                Log.i(LOG_ID, "Trigger obsolete alarm after ${ReceiveData.getElapsedTimeMinute()} minutes.")
                InternalNotifier.notify(context, NotifySource.OBSOLETE_ALARM_TRIGGER, null)
            }
        }
    }
}