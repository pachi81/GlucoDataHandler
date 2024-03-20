package de.michelinside.glucodatahandler.common.notification

import de.michelinside.glucodatahandler.common.R

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