package de.michelinside.glucodatahandler.common.notification

import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import android.os.Bundle
import android.util.Log
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.utils.Utils
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

open class AlarmSetting(val alarmPrefix: String, var intervalMin: Int) {
    companion object {
        const val defaultDelta = 5F
        const val defaultDeltaCount = 3
        const val defaultDeltaBorder = 145F
        val defaultWeekdays = DayOfWeek.entries.map { it.value.toString() }.toMutableSet()
    }

    protected val LOG_ID = "GDH.AlarmSetting.$alarmPrefix"
    var enabled = true
    private var inactiveEnabled = false
    private var inactiveStartTime = ""
    private var inactiveEndTime = ""
    private var inactiveWeekdays = defaultWeekdays
    var vibratePatternKey = alarmPrefix
    private var vibrateAmplitudePref = 15
    var soundDelay = 0
    var repeatUntilClose = false
    var repeatTime = 0
    // not for sharing with watch!
    var soundLevel = -1
    var useCustomSound = false
    var customSoundPath = ""
    var delta = 0F
    var deltaCount = 0
    var deltaBorder = 0F

    val intervalMS get() = intervalMin*60000

    init {
        Log.v(LOG_ID, "init called")
    }

    val isTempInactive: Boolean get() {
        if (enabled && inactiveEnabled) {
            val now = LocalDateTime.now()
            val currentTime = now.format(DateTimeFormatter.ofPattern("HH:mm"))
            if (Utils.timeBetweenTimes(now, inactiveStartTime, inactiveEndTime, inactiveWeekdays)) {
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

    val vibratePattern: LongArray? get() {
        return VibratePattern.getByKey(vibratePatternKey).pattern
    }

    val vibrateAmplitude: Int get() {
        return vibrateAmplitudePref * 17
    }

    fun getSettingName(key: String): String {
        return alarmPrefix+key
    }

    private val alarmPreferencesToShare = mutableSetOf(
        getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_ENABLED),
        getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_INTERVAL),
        getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_SOUND_DELAY),
        getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_INACTIVE_ENABLED),
        getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_INACTIVE_START_TIME),
        getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_INACTIVE_END_TIME),
        getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_INACTIVE_WEEKDAYS),
        getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_REPEAT),
        getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_REPEAT_UNTIL_CLOSE)
    )

    open fun getPreferencesToShare(): MutableSet<String> {
        return alarmPreferencesToShare
    }

    private val alarmPreferencesLocalOnly = mutableSetOf(
        getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_SOUND_LEVEL),
        getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_USE_CUSTOM_SOUND),
        getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_CUSTOM_SOUND),
        getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_VIBRATE_PATTERN),
        getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_VIBRATE_AMPLITUDE),
    )

    fun isAlarmSettingToShare(key: String): Boolean {
        return getPreferencesToShare().contains(key)
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
            bundle.putStringArray(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_INACTIVE_WEEKDAYS), inactiveWeekdays.toTypedArray())
            bundle.putInt(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_SOUND_DELAY), soundDelay)
            bundle.putBoolean(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_REPEAT_UNTIL_CLOSE), repeatUntilClose)
            bundle.putInt(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_REPEAT), repeatTime)
        }
        if (hasDelta()) {
            bundle.putFloat(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_DELTA), delta)
            bundle.putInt(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_OCCURRENCE_COUNT), deltaCount)
            bundle.putFloat(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_BORDER), deltaBorder)
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
            editor.putStringSet(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_INACTIVE_WEEKDAYS), bundle.getStringArray(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_INACTIVE_WEEKDAYS))?.toMutableSet() ?: defaultWeekdays)
            editor.putInt(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_SOUND_DELAY), bundle.getInt(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_SOUND_DELAY), soundDelay))
            editor.putBoolean(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_REPEAT_UNTIL_CLOSE), bundle.getBoolean(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_REPEAT_UNTIL_CLOSE), repeatUntilClose))
            editor.putInt(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_REPEAT), bundle.getInt(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_REPEAT), repeatTime))
            if (hasDelta()) {
                editor.putFloat(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_DELTA), bundle.getFloat(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_DELTA), 5F))
                editor.putInt(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_OCCURRENCE_COUNT), bundle.getInt(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_OCCURRENCE_COUNT), 1))
                editor.putFloat(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_BORDER), bundle.getFloat(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_BORDER), 145F))
            }
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
            inactiveStartTime = sharedPref.getString(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_INACTIVE_START_TIME), null) ?: ""
            inactiveEndTime = sharedPref.getString(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_INACTIVE_END_TIME), null) ?: ""
            inactiveWeekdays = sharedPref.getStringSet(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_INACTIVE_WEEKDAYS), defaultWeekdays) ?: defaultWeekdays
            soundDelay = sharedPref.getInt(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_SOUND_DELAY), soundDelay)
            soundLevel = sharedPref.getInt(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_SOUND_LEVEL), soundLevel)
            useCustomSound = sharedPref.getBoolean(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_USE_CUSTOM_SOUND), useCustomSound)
            customSoundPath = sharedPref.getString(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_CUSTOM_SOUND), null) ?: ""
            repeatUntilClose = sharedPref.getBoolean(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_REPEAT_UNTIL_CLOSE), repeatUntilClose)
            repeatTime = sharedPref.getInt(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_REPEAT), repeatTime)
            vibratePatternKey = sharedPref.getString(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_VIBRATE_PATTERN), alarmPrefix) ?: alarmPrefix
            vibrateAmplitudePref = sharedPref.getInt(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_VIBRATE_AMPLITUDE), vibrateAmplitudePref)
            if(hasDelta()) {
                delta = abs(sharedPref.getFloat(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_DELTA), defaultDelta))
                deltaCount = sharedPref.getInt(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_OCCURRENCE_COUNT), defaultDeltaCount)
                deltaBorder = sharedPref.getFloat(getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_BORDER), defaultDeltaBorder)
            }
            Log.d(LOG_ID, "updateSettings: " +
                    "enabled=$enabled, " +
                    "intervalMin=$intervalMin, " +
                    "inactiveEnabled=$inactiveEnabled, " +
                    "inactiveStartTime=$inactiveStartTime, " +
                    "inactiveEndTime=$inactiveEndTime, " +
                    "soundDelay=$soundDelay, " +
                    "repeatUntilClose=$repeatUntilClose, " +
                    "repeatTime=$repeatTime, " +
                    "soundLevel=$soundLevel, " +
                    "useCustomSound=$useCustomSound, " +
                    "customSoundPath=$customSoundPath, " +
                    "vibratePattern=$vibratePatternKey, " +
                    "vibrateAmplitudePref=$vibrateAmplitudePref, " +
                    "delta=$delta, " +
                    "deltaCount=$deltaCount, " +
                    "border=$deltaBorder"
            )

        } catch (exc: Exception) {
            Log.e(LOG_ID, "saveSettings exception: " + exc.toString() + ": " + exc.stackTraceToString() )
        }
    }

    open fun hasDelta(): Boolean = false
}

class DeltaAlarmSetting(alarmPrefix: String, intervalMin: Int) : AlarmSetting(alarmPrefix, intervalMin) {
    init {
        enabled = false
    }

    private val deltaPreferencesToShare = mutableSetOf(
        getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_DELTA),
        getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_OCCURRENCE_COUNT),
        getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_BORDER)
    )

    override fun hasDelta(): Boolean  = true

    override fun getPreferencesToShare(): MutableSet<String> {
        return (super.getPreferencesToShare() + deltaPreferencesToShare).toMutableSet()
    }
}