package de.michelinside.glucodatahandler.common.notification

import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.R

enum class AlarmType(val resId: Int, val setting: AlarmSetting? = null) {
    NONE(R.string.alarm_none),
    VERY_LOW(R.string.alarm_very_low, AlarmSetting(Constants.SHARED_PREF_ALARM_VERY_LOW, 15)),
    LOW(R.string.alarm_low, AlarmSetting(Constants.SHARED_PREF_ALARM_LOW, 25)),
    OK(R.string.alarm_none),
    HIGH(R.string.alarm_high, AlarmSetting(Constants.SHARED_PREF_ALARM_HIGH, 30)),
    VERY_HIGH(R.string.alarm_very_high, AlarmSetting(Constants.SHARED_PREF_ALARM_VERY_HIGH, 25)),
    OBSOLETE(R.string.alarm_obsolete, AlarmSetting(Constants.SHARED_PREF_ALARM_OBSOLETE, 20)),
    FALLING_FAST(R.string.alarm_falling_fast, DeltaAlarmSetting(Constants.SHARED_PREF_ALARM_FALLING_FAST, 20)),
    RISING_FAST(R.string.alarm_rising_fast, DeltaAlarmSetting(Constants.SHARED_PREF_ALARM_RISING_FAST, 20));

    companion object {
        fun fromIndex(idx: Int): AlarmType {
            entries.forEach {
                if(it.ordinal == idx) {
                    return it
                }
            }
            return NONE
        }
    }
}