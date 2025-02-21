package de.michelinside.glucodatahandler.common.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import de.michelinside.glucodatahandler.common.AppSource
import de.michelinside.glucodatahandler.common.Command
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.notification.AlarmSetting.Companion.defaultWeekdays
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.receiver.ScreenEventReceiver
import de.michelinside.glucodatahandler.common.utils.Utils
import java.text.DateFormat
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date

object AlarmHandler: SharedPreferences.OnSharedPreferenceChangeListener, NotifierInterface {
    private const val LOG_ID = "GDH.AlarmHandler"

    private const val LAST_ALARM_INDEX = "last_alarm_index"
    private const val LAST_ALARM_TIME = "last_alarm_time"
    private const val LAST_FALLING_ALARM_TIME = "last_falling_alarm_time"
    private const val LAST_RISING_ALARM_TIME = "last_rising_alarm_time"
    const val SNOOZE_TIME = "snooze_time"

    private var lastAlarmTime = 0L
    private var lastFallingAlarmTime = 0L
    private var lastRisingAlarmTime = 0L
    private var lastAlarmType = AlarmType.OK
    private var initialized = false
    private var snoozeTime = 0L
    private var inactiveEnabled = false
    private var inactiveStartTime = ""
    private var inactiveEndTime = ""
    private var inactiveWeekdays = defaultWeekdays
    private var forceVeryLow = false
    private lateinit var sharedExtraPref: SharedPreferences

    private var alarmManager: AlarmManager? = null
    private var snoozeEndPendingIntent: PendingIntent? = null

    val isSnoozeActive: Boolean get() {
        return snoozeTime >= System.currentTimeMillis()
    }

    val snoozeTimestamp: String get() {
        return DateFormat.getTimeInstance(DateFormat.DEFAULT).format(Date(snoozeTime))
    }

    val snoozeShortTimestamp: String get() {
        return DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(snoozeTime))
    }

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val isTempInactive: Boolean get() {
        if (inactiveEnabled) {
            val now = LocalDateTime.now()
            val currentTime = now.format(timeFormatter)
            if (Utils.timeBetweenTimes(now, inactiveStartTime, inactiveEndTime, inactiveWeekdays)) {
                Log.v(LOG_ID, "Alarm is inactive: $inactiveStartTime < $currentTime < $inactiveEndTime")
                return true
            }
        }
        return false
    }

    val inactiveEndTimestamp: String get() {
        return LocalTime.parse(inactiveEndTime, timeFormatter).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    }

    fun isInactive(alarmType: AlarmType) : Boolean {
        if(forceVeryLow && alarmType == AlarmType.VERY_LOW)
            return false  // force very low alarm
        return isTempInactive || isSnoozeActive
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
        if (newAlarmType.setting == null)
            return false
        Log.d(
            LOG_ID, "Check force alarm:" +
                    " - newAlarmType=" + newAlarmType.toString() +
                    " - lastAlarmType=" + lastAlarmType.toString() +
                    " - lastAlarmTime=" +  DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(lastAlarmTime)) +
                    " - snoozeTime=" + (if(isSnoozeActive)snoozeTimestamp else "off") +
                    " - tempInactive=" +  (if(isTempInactive) inactiveEndTime else "off") +
                    " - forceVeryLow=" + forceVeryLow.toString() +
                    " - time=" + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ReceiveData.time)) +
                    " - delta=" + ReceiveData.delta.toString() +
                    " - rate=" + ReceiveData.rate.toString() +
                    " - diff=" + (ReceiveData.time - lastAlarmTime).toString() +
                    " - veryLow=>" + (if(AlarmType.VERY_LOW.setting!!.isActive) DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(lastAlarmTime+AlarmType.VERY_LOW.setting.intervalMS)) else "off") +
                    " - low=>" + (if(AlarmType.LOW.setting!!.isActive) DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(lastAlarmTime+AlarmType.LOW.setting.intervalMS)) else "off") +
                    " - high=>" + (if(AlarmType.HIGH.setting!!.isActive) DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(lastAlarmTime+AlarmType.HIGH.setting.intervalMS)) else "off") +
                    " - veryHigh=>" + (if(AlarmType.VERY_HIGH.setting!!.isActive) DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(lastAlarmTime+AlarmType.VERY_HIGH.setting.intervalMS)) else "off")
        )
        if (isInactive(newAlarmType))
            return false

        val triggerAlarm = when(newAlarmType) {
            AlarmType.VERY_LOW,
            AlarmType.LOW -> newAlarmType.setting.isActive && checkLowAlarm(newAlarmType, newAlarmType.setting.intervalMS)
            AlarmType.HIGH,
            AlarmType.VERY_HIGH -> newAlarmType.setting.isActive && checkHighAlarm(newAlarmType, newAlarmType.setting.intervalMS)
            else -> false
        }
        if (triggerAlarm) {
            setLastAlarm(newAlarmType)
            Log.i(LOG_ID, "Trigger alarm for type $newAlarmType")
        }
        return triggerAlarm
    }

    fun setLastAlarm(alarmType: AlarmType) {
        if (alarmType == AlarmType.NONE || alarmType == AlarmType.OK)
            return
        Log.i(LOG_ID, "Set last alarm type to $alarmType - time=${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ReceiveData.time))}")
        if (alarmType < AlarmType.OBSOLETE) {
            lastAlarmTime = ReceiveData.time
            lastAlarmType = alarmType
        } else if (alarmType == AlarmType.FALLING_FAST) {
            lastFallingAlarmTime = ReceiveData.time
        } else if (alarmType == AlarmType.RISING_FAST) {
            lastRisingAlarmTime = ReceiveData.time
        }
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
        if(snoozeTime != time) {
            snoozeTime = time
            Log.i(LOG_ID, "New snooze-time: $snoozeTimestamp")
            saveExtras()
            if(GlucoDataService.context != null) {
                InternalNotifier.notify(GlucoDataService.context!!, NotifySource.ALARM_STATE_CHANGED, null)
                triggerSnoozeEnd(GlucoDataService.context!!)
            }
            if(!fromClient) {
                val bundle = Bundle()
                bundle.putLong(SNOOZE_TIME, snoozeTime)
                GlucoDataService.sendCommand(Command.SNOOZE_ALARM, bundle)
            }
        }
    }

    fun disableInactiveTime(fromClient: Boolean = false) {
        Log.i(LOG_ID, "Disable inactive time called - fromClient=$fromClient")
        if (GlucoDataService.sharedPref!=null) {
            inactiveEnabled = false
            with(GlucoDataService.sharedPref!!.edit()) {
                putBoolean(Constants.SHARED_PREF_ALARM_INACTIVE_ENABLED, false)
                apply()
            }
            if(!fromClient) {
                GlucoDataService.sendCommand(Command.DISABLE_INACTIVE_TIME)
            }
        }
    }

    private fun checkHighAlarm(newAlarmType: AlarmType, alarmInterval: Int): Boolean {
        if (newAlarmType > lastAlarmType || ReceiveData.time - lastAlarmTime >= alarmInterval) {
            if(ReceiveData.delta.isNaN() || ReceiveData.delta == 0F)
                return ReceiveData.calculatedRate > 0F || ReceiveData.sourceRate > 0F
            return ReceiveData.delta > 0F
        }
        return false
    }

    private fun checkLowAlarm(newAlarmType: AlarmType, alarmInterval: Int): Boolean {
        if (newAlarmType < lastAlarmType || ReceiveData.time - lastAlarmTime >= alarmInterval) {
            if(ReceiveData.delta.isNaN() || ReceiveData.delta == 0F)
                return ReceiveData.calculatedRate < 0F || ReceiveData.sourceRate < 0F
            return ReceiveData.delta < 0F
        }
        return false
    }

    fun checkDeltaAlarmTrigger(deltaFallingCount: Int, deltaRisingCount: Int): AlarmType {
        var result = AlarmType.NONE
        Log.d(LOG_ID, "Check delta alarm trigger: deltaFallingCount=$deltaFallingCount - deltaRisingCount=$deltaRisingCount" +
                " - time=" + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ReceiveData.time)) +
                " - snoozeTime=" + (if(isSnoozeActive)snoozeTimestamp else "off") +
                " - tempInactive=" +  (if(isTempInactive) inactiveEndTime else "off") +
                " - lastFallingAlarmTime=" +  DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(lastFallingAlarmTime)) +
                " - lastRisingAlarmTime=" +  DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(lastRisingAlarmTime)) +
                " - falling=>" + (if(AlarmType.FALLING_FAST.setting!!.isActive) DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(lastFallingAlarmTime+AlarmType.FALLING_FAST.setting.intervalMS)) else "off") +
                " - rising=>" + (if(AlarmType.RISING_FAST.setting!!.isActive) DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(lastRisingAlarmTime+AlarmType.RISING_FAST.setting.intervalMS)) else "off")
        )

        if(isInactive(AlarmType.NONE))
            return result

        if(AlarmType.FALLING_FAST.setting.isActive && deltaFallingCount >= AlarmType.FALLING_FAST.setting.deltaCount && ReceiveData.rawValue <= AlarmType.FALLING_FAST.setting.deltaBorder) {
            if (ReceiveData.time - lastFallingAlarmTime >= AlarmType.FALLING_FAST.setting.intervalMS) {
                Log.i(LOG_ID, "Trigger falling fast alarm")
                result = AlarmType.FALLING_FAST
            }
        } else if (AlarmType.RISING_FAST.setting.isActive && deltaRisingCount >= AlarmType.RISING_FAST.setting.deltaCount && ReceiveData.rawValue >= AlarmType.RISING_FAST.setting.deltaBorder) {
            if (ReceiveData.time - lastRisingAlarmTime >= AlarmType.RISING_FAST.setting.intervalMS) {
                Log.i(LOG_ID, "Trigger rising fast alarm")
                result = AlarmType.RISING_FAST
            }
        }
        if(result != AlarmType.NONE)
            setLastAlarm(result)
        return result
    }

    private fun saveExtras() {
        try {
            Log.d(LOG_ID, "Saving extras")
            if(!initialized) {
                Log.w(LOG_ID, "saveExtras called before initData")
                return
            }
            Log.i(LOG_ID, "Saving extras for lastAlarmType $lastAlarmType " +
                    " - lastAlarmTime=${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(lastAlarmTime))}" +
                    " - lastFallingAlarmTime=${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(lastFallingAlarmTime))}" +
                    " - lastRisingAlarmTime=${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(lastRisingAlarmTime))}" +
                    " - snoozeTime=${snoozeTimestamp}"
            )
            with(sharedExtraPref.edit()) {
                putLong(LAST_ALARM_TIME, lastAlarmTime)
                putLong(LAST_FALLING_ALARM_TIME, lastFallingAlarmTime)
                putLong(LAST_RISING_ALARM_TIME, lastRisingAlarmTime)
                putInt(LAST_ALARM_INDEX, lastAlarmType.ordinal)
                putLong(SNOOZE_TIME, snoozeTime)
                apply()
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "saveExtras exception: " + exc.toString() + " " + exc.stackTraceToString() )
        }
    }

    private fun loadExtras() {
        try {
            Log.i(LOG_ID, "Reading saved values...")
            lastAlarmType = AlarmType.fromIndex(sharedExtraPref.getInt(LAST_ALARM_INDEX, AlarmType.NONE.ordinal))
            lastAlarmTime = sharedExtraPref.getLong(LAST_ALARM_TIME, 0L)
            lastFallingAlarmTime = sharedExtraPref.getLong(LAST_FALLING_ALARM_TIME, 0L)
            lastRisingAlarmTime = sharedExtraPref.getLong(LAST_RISING_ALARM_TIME, 0L)
            snoozeTime = sharedExtraPref.getLong(SNOOZE_TIME, 0L)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Loading receivers exception: " + exc.toString() + "\n" + exc.stackTraceToString() )
        }
    }

    fun isAlarmSettingToShare(key: String?): Boolean {
        when(key) {
            null -> return false
            Constants.SHARED_PREF_ALARM_SNOOZE_NOTIFICATION_BUTTONS,
            Constants.SHARED_PREF_NO_ALARM_NOTIFICATION_AUTO_CONNECTED,
            Constants.SHARED_PREF_ALARM_INACTIVE_ENABLED,
            Constants.SHARED_PREF_ALARM_INACTIVE_START_TIME,
            Constants.SHARED_PREF_ALARM_INACTIVE_END_TIME,
            Constants.SHARED_PREF_ALARM_INACTIVE_WEEKDAYS,
            Constants.SHARED_PREF_ALARM_FORCE_VERY_LOW -> return true
            else -> {
                AlarmType.entries.forEach {
                    if (it.setting != null && it.setting.isAlarmSettingToShare(key))
                        return true
                }
                return false
            }
        }
    }

    fun getExtras(): Bundle {
        val bundle = Bundle()
        bundle.putLong(LAST_ALARM_TIME, lastAlarmTime)
        bundle.putLong(LAST_FALLING_ALARM_TIME, lastFallingAlarmTime)
        bundle.putLong(LAST_RISING_ALARM_TIME, lastRisingAlarmTime)
        bundle.putInt(LAST_ALARM_INDEX, lastAlarmType.ordinal)
        return bundle
    }

    fun setExtras(context: Context, bundle: Bundle) {
        Log.i(LOG_ID, "set extras: ${Utils.dumpBundle(bundle)}")
        val lastType = AlarmType.fromIndex(bundle.getInt(LAST_ALARM_INDEX, lastAlarmType.ordinal))
        var extrasChanged = false
        if(lastType!=AlarmType.NONE && lastType!=AlarmType.OK) {
            val lastTime = bundle.getLong(LAST_ALARM_TIME, lastAlarmTime)
            if(lastTime > lastAlarmTime) {
                lastAlarmType = lastType
                lastAlarmTime = bundle.getLong(LAST_ALARM_TIME, lastAlarmTime)
                extrasChanged = true
            }
            if(bundle.getLong(LAST_FALLING_ALARM_TIME, lastFallingAlarmTime) > lastFallingAlarmTime) {
                lastFallingAlarmTime = bundle.getLong(LAST_FALLING_ALARM_TIME, lastFallingAlarmTime)
                extrasChanged = true
            }
            if(bundle.getLong(LAST_RISING_ALARM_TIME, lastRisingAlarmTime) > lastRisingAlarmTime) {
                lastRisingAlarmTime = bundle.getLong(LAST_RISING_ALARM_TIME, lastRisingAlarmTime)
                extrasChanged = true
            }
        }
        if(extrasChanged) {
            Log.i(LOG_ID, "Newer extras received")
            saveExtras()
        }
    }

    fun getSettings(forAndroidAuto: Boolean = false): Bundle {
        val bundle = Bundle()
        AlarmType.entries.forEach {
            if (it.setting != null)
                bundle.putAll(it.setting.getSettings(forAndroidAuto))
        }

        if(!forAndroidAuto && AlarmNotificationBase.instance != null) {
            bundle.putAll(AlarmNotificationBase.instance!!.getSettings())
        }
        bundle.putLong(SNOOZE_TIME, snoozeTime)
        bundle.putBoolean(Constants.SHARED_PREF_ALARM_INACTIVE_ENABLED, inactiveEnabled)
        bundle.putString(Constants.SHARED_PREF_ALARM_INACTIVE_START_TIME, inactiveStartTime)
        bundle.putString(Constants.SHARED_PREF_ALARM_INACTIVE_END_TIME, inactiveEndTime)
        bundle.putStringArray(Constants.SHARED_PREF_ALARM_INACTIVE_WEEKDAYS, inactiveWeekdays.toTypedArray())
        bundle.putBoolean(Constants.SHARED_PREF_ALARM_FORCE_VERY_LOW, forceVeryLow)
        return bundle
    }

    fun setSettings(context: Context, bundle: Bundle) {
        val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
        with(sharedPref.edit()) {

            AlarmType.entries.forEach {
                if (it.setting != null)
                    it.setting.saveSettings(bundle, this)
            }
            if(AlarmNotificationBase.instance != null) {
                AlarmNotificationBase.instance!!.saveSettings(bundle, this)
            }
            if(bundle.containsKey(Constants.SHARED_PREF_ALARM_INACTIVE_ENABLED)) {
                putBoolean(Constants.SHARED_PREF_ALARM_INACTIVE_ENABLED, bundle.getBoolean(Constants.SHARED_PREF_ALARM_INACTIVE_ENABLED, inactiveEnabled))
                putString(Constants.SHARED_PREF_ALARM_INACTIVE_START_TIME, bundle.getString(Constants.SHARED_PREF_ALARM_INACTIVE_START_TIME, inactiveStartTime))
                putString(Constants.SHARED_PREF_ALARM_INACTIVE_END_TIME, bundle.getString(Constants.SHARED_PREF_ALARM_INACTIVE_END_TIME, inactiveEndTime))
                putStringSet(Constants.SHARED_PREF_ALARM_INACTIVE_WEEKDAYS, bundle.getStringArray(Constants.SHARED_PREF_ALARM_INACTIVE_WEEKDAYS)?.toMutableSet() ?: defaultWeekdays)
                putBoolean(Constants.SHARED_PREF_ALARM_FORCE_VERY_LOW, bundle.getBoolean(Constants.SHARED_PREF_ALARM_FORCE_VERY_LOW, forceVeryLow))
            }
            apply()
        }
        setSnoozeTime(bundle.getLong(SNOOZE_TIME, snoozeTime), true)
        updateSettings(sharedPref, context)
        InternalNotifier.notify(context, NotifySource.ALARM_SETTINGS, null)
    }

    private fun readSettings(sharedPref: SharedPreferences, key: String?) {
        if (key == null) {
            readSettings(sharedPref, Constants.SHARED_PREF_ALARM_INACTIVE_ENABLED)
            readSettings(sharedPref, Constants.SHARED_PREF_ALARM_INACTIVE_START_TIME)
            readSettings(sharedPref, Constants.SHARED_PREF_ALARM_INACTIVE_END_TIME)
            readSettings(sharedPref, Constants.SHARED_PREF_ALARM_INACTIVE_WEEKDAYS)
            readSettings(sharedPref, Constants.SHARED_PREF_ALARM_FORCE_VERY_LOW)
        } else {
            when(key) {
                Constants.SHARED_PREF_ALARM_INACTIVE_ENABLED -> inactiveEnabled = sharedPref.getBoolean(Constants.SHARED_PREF_ALARM_INACTIVE_ENABLED, inactiveEnabled)
                Constants.SHARED_PREF_ALARM_INACTIVE_START_TIME -> inactiveStartTime = sharedPref.getString(Constants.SHARED_PREF_ALARM_INACTIVE_START_TIME, inactiveStartTime) ?: ""
                Constants.SHARED_PREF_ALARM_INACTIVE_END_TIME -> inactiveEndTime = sharedPref.getString(Constants.SHARED_PREF_ALARM_INACTIVE_END_TIME, inactiveEndTime) ?: ""
                Constants.SHARED_PREF_ALARM_INACTIVE_WEEKDAYS -> inactiveWeekdays = sharedPref.getStringSet(Constants.SHARED_PREF_ALARM_INACTIVE_WEEKDAYS, defaultWeekdays) ?: defaultWeekdays
                Constants.SHARED_PREF_ALARM_FORCE_VERY_LOW -> forceVeryLow = sharedPref.getBoolean(Constants.SHARED_PREF_ALARM_FORCE_VERY_LOW, forceVeryLow)
            }
        }
    }

    private fun updateSettings(sharedPref: SharedPreferences, context: Context, key: String? = null) {
        Log.d(LOG_ID, "updateSettings called for key $key")
        AlarmType.entries.forEach {
            if(it.setting != null && (key == null || it.setting.isAlarmSetting(key))) {
                it.setting.updateSettings(sharedPref)
            }
        }
        readSettings(sharedPref, key)
        if (key == null || key == AlarmType.OBSOLETE.setting!!.getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_ENABLED))
            checkNotifier(context)
    }

    private fun isObsoleteAlarmActive(): Boolean {
        if(GlucoDataService.appSource != AppSource.WEAR_APP)
            return AlarmType.OBSOLETE.setting!!.isActive
        if(AlarmType.OBSOLETE.setting!!.isActive) {  // on wear: only notification is used for alarms -> check to save battery for timer thread
            if(AlarmNotificationBase.instance?.getAlarmState(GlucoDataService.context!!) == AlarmState.ACTIVE) {
                if(ScreenEventReceiver.isDisplayOff()) {
                    // if display is off and the phone does not send new data to wear, the obsolete alarm should not trigger!
                    if(GlucoDataService.sharedPref != null)
                        return GlucoDataService.sharedPref!!.getBoolean(Constants.SHARED_PREF_PHONE_WEAR_SCREEN_OFF_UPDATE, true)
                }
                return true
            }
        }
        return false
    }

    fun checkNotifier(context: Context) {
        val obsoleteActive = isObsoleteAlarmActive()
        Log.v(LOG_ID, "checkNotifier called - obsoleteActive=$obsoleteActive")
        if(obsoleteActive != InternalNotifier.hasNotifier(this)) {
            if(obsoleteActive) {
                InternalNotifier.addNotifier(context, this, mutableSetOf(NotifySource.TIME_VALUE))
            } else {
                InternalNotifier.remNotifier(context, this)
            }
        }
    }

    fun getDefaultIntervalMin(alarmType: AlarmType): Int {
        return alarmType.setting?.intervalMin ?: 0
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        try {
            Log.d(LOG_ID, "onSharedPreferenceChanged called for key $key")
            if (GlucoDataService.context != null) {
                updateSettings(sharedPreferences, GlucoDataService.context!!, key)
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
        Log.d(LOG_ID, "checkObsoleteAlarm: enabled=${AlarmType.OBSOLETE.setting!!.isActive} - interval=${AlarmType.OBSOLETE.setting.intervalMin} - elapsed=${ReceiveData.getElapsedTimeMinute()}")
        if(AlarmType.OBSOLETE.setting.isActive && AlarmType.OBSOLETE.setting.intervalMin > 0 && ReceiveData.getElapsedTimeMinute() >= AlarmType.OBSOLETE.setting.intervalMin) {
            if (ReceiveData.getElapsedTimeMinute().mod(AlarmType.OBSOLETE.setting.intervalMin) == 0) {
                Log.i(LOG_ID, "Trigger obsolete alarm after ${ReceiveData.getElapsedTimeMinute()} minutes.")
                InternalNotifier.notify(context, NotifySource.OBSOLETE_ALARM_TRIGGER, null)
            }
        }
    }

    private fun triggerSnoozeEnd(context: Context) {
        stopSnoozeEnd()
        if(isSnoozeActive) {
            alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            var hasExactAlarmPermission = true
            if (!Utils.canScheduleExactAlarms(context)) {
                Log.d(LOG_ID, "Need permission to set exact alarm!")
                hasExactAlarmPermission = false
            }
            val intent = Intent(context, AlarmSnoozeEndReceiver::class.java)
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            snoozeEndPendingIntent = PendingIntent.getBroadcast(
                context,
                800,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
            )
            Log.i(LOG_ID, "Trigger SnoozeEnd at $snoozeTimestamp - exact-alarm=$hasExactAlarmPermission")
            if (hasExactAlarmPermission) {
                alarmManager!!.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    snoozeTime,
                    snoozeEndPendingIntent!!
                )
            } else {
                alarmManager!!.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    snoozeTime,
                    snoozeEndPendingIntent!!
                )
            }
        }
    }

    fun stopSnoozeEnd() {
        if(alarmManager != null && snoozeEndPendingIntent != null) {
            Log.i(LOG_ID, "Stop SnoozeEnd triggered")
            alarmManager!!.cancel(snoozeEndPendingIntent!!)
            alarmManager = null
            snoozeEndPendingIntent = null
        }
    }
}


class AlarmSnoozeEndReceiver: BroadcastReceiver() {
    private val LOG_ID = "GDH.AlarmSnoozeEndReceiver"
    override fun onReceive(context: Context, intent: Intent) {
        try {
            Log.d(LOG_ID, "onReceive called snoozeActive:${AlarmHandler.isSnoozeActive}")
            if(!AlarmHandler.isSnoozeActive) {
                Log.i(LOG_ID, "End of snooze reached")
                InternalNotifier.notify(context, NotifySource.ALARM_STATE_CHANGED, null)
                AlarmHandler.stopSnoozeEnd()
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onReceive exception: " + exc.toString())
        }
    }
}