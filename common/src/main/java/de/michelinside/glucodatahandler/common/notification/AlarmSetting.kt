package de.michelinside.glucodatahandler.common.notification

import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import android.os.Bundle
import android.util.Log
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.utils.Utils
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class AlarmSetting(val alarmPrefix: String, var intervalMin: Int) {
    private val LOG_ID = "GDH.AlarmSetting.$alarmPrefix"
    private var enabled = true
    private var inactiveEnabled = false
    private var inactiveStartTime = ""
    private var inactiveEndTime = ""
    var soundDelay = 0
    var retriggerTime = 0
    // not for sharing with watch!
    var soundLevel = -1
    var useCustomSound = false
    var customSoundPath = ""

    val intervalMS get() = intervalMin*60000

    init {
        Log.v(LOG_ID, "init called")
    }

    val isTempInactive: Boolean get() {
        if (enabled && inactiveEnabled) {
            val currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
            if (Utils.timeBetweenTimes(currentTime, inactiveStartTime, inactiveEndTime)) {
                Log.v(LOG_ID, "Alarm is inactive: $inactiveStartTime < $currentTime < $inactiveEndTime")
                return true
            }
        }
        return false
    }

    val isActive: Boolean get() {
        if (!enabled)
            return false
        return !isTempInactive
    }

    fun getSettingName(key: String): String {
        return alarmPrefix+key
    }

    private val alarmPreferencesToShare = mutableSetOf(
        getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_ENABLED),
        getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_INTERVAL),
        getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_SOUND_DELAY),
        getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_RETRIGGER),
        getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_INACTIVE_ENABLED),
        getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_INACTIVE_START_TIME),
        getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_INACTIVE_END_TIME),
    )

    private val alarmPreferencesLocalOnly = mutableSetOf(
        getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_SOUND_LEVEL),
        getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_USE_CUSTOM_SOUND),
        getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_CUSTOM_SOUND)
    )

    fun isAlarmSettingToShare(key: String): Boolean {
        return alarmPreferencesToShare.contains(key)
    }

    fun isAlarmSetting(key: String): Boolean {
        return isAlarmSettingToShare(key) || alarmPreferencesLocalOnly.contains(key)
    }

    fun getSettings(forAndroidAuto: Boolean): Bundle {
        Log.v(LOG_ID, "getSettings called for AndroidAuto: $forAndroidAuto")
        val bundle = Bundle()
        bundle.putBoolean(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_ENABLED), enabled)
        bundle.putInt(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_INTERVAL), intervalMin)
        if (!forAndroidAuto) {
            bundle.putBoolean(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_INACTIVE_ENABLED), inactiveEnabled)
            bundle.putString(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_INACTIVE_START_TIME), inactiveStartTime)
            bundle.putString(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_INACTIVE_END_TIME), inactiveEndTime)
            bundle.putInt(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_SOUND_DELAY), soundDelay)
            bundle.putInt(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_RETRIGGER), retriggerTime)
        }
        return bundle
    }

    fun saveSettings(bundle: Bundle, editor: Editor) {
        try {
            Log.v(LOG_ID, "saveSettings called")
            editor.putBoolean(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_ENABLED), bundle.getBoolean(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_ENABLED), enabled))
            editor.putInt(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_INTERVAL), bundle.getInt(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_INTERVAL), intervalMin))
            editor.putBoolean(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_INACTIVE_ENABLED), bundle.getBoolean(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_INACTIVE_ENABLED), inactiveEnabled))
            editor.putString(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_INACTIVE_START_TIME), bundle.getString(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_INACTIVE_START_TIME), inactiveStartTime))
            editor.putString(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_INACTIVE_END_TIME), bundle.getString(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_INACTIVE_END_TIME), inactiveEndTime))
            editor.putInt(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_SOUND_DELAY), bundle.getInt(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_SOUND_DELAY), soundDelay))
            editor.putInt(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_RETRIGGER), bundle.getInt(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_RETRIGGER), retriggerTime))
        } catch (exc: Exception) {
            Log.e(LOG_ID, "saveSettings exception: " + exc.toString() + ": " + exc.stackTraceToString() )
        }
    }

    fun updateSettings(sharedPref: SharedPreferences) {
        try {
            Log.v(LOG_ID, "updateSettings called")
            enabled = sharedPref.getBoolean(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_ENABLED), enabled)
            intervalMin = sharedPref.getInt(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_INTERVAL), intervalMin)
            inactiveEnabled = sharedPref.getBoolean(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_INACTIVE_ENABLED), inactiveEnabled)
            inactiveStartTime = sharedPref.getString(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_INACTIVE_START_TIME), inactiveStartTime) ?: ""
            inactiveEndTime = sharedPref.getString(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_INACTIVE_END_TIME), inactiveEndTime) ?: ""
            soundDelay = sharedPref.getInt(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_SOUND_DELAY), soundDelay)
            retriggerTime = sharedPref.getInt(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_RETRIGGER), retriggerTime)
            soundLevel = sharedPref.getInt(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_SOUND_LEVEL), soundLevel)
            useCustomSound = sharedPref.getBoolean(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_USE_CUSTOM_SOUND), useCustomSound)
            customSoundPath = sharedPref.getString(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_CUSTOM_SOUND), customSoundPath) ?: ""

            Log.d(LOG_ID, "updateSettings: " +
                    "enabled=$enabled, " +
                    "intervalMin=$intervalMin, " +
                    "inactiveEnabled=$inactiveEnabled, " +
                    "inactiveStartTime=$inactiveStartTime, " +
                    "inactiveEndTime=$inactiveEndTime, " +
                    "soundDelay=$soundDelay, " +
                    "retriggerTime=$retriggerTime, " +
                    "soundLevel=$soundLevel, " +
                    "useCustomSound=$useCustomSound, " +
                    "customSoundPath=$customSoundPath")

        } catch (exc: Exception) {
            Log.e(LOG_ID, "saveSettings exception: " + exc.toString() + ": " + exc.stackTraceToString() )
        }
    }
}