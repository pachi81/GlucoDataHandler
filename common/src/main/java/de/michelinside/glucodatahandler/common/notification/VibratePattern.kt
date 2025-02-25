package de.michelinside.glucodatahandler.common.notification

import de.michelinside.glucodatahandler.common.R

enum class VibratePattern(val key: String, val resId: Int, val pattern: LongArray?) {
    NO_VIBRATION("", R.string.no_vibration, null),
    VERY_LOW(AlarmType.VERY_LOW.setting!!.alarmPrefix, R.string.very_low_alarm_notification_name, longArrayOf(0, 1000, 500, 1000, 500, 1000, 500, 1000, 500, 1000, 500, 1000)),
    LOW(AlarmType.LOW.setting!!.alarmPrefix, R.string.low_alarm_notification_name, longArrayOf(0, 700, 500, 700, 500, 700, 500, 700)),
    HIGH(AlarmType.HIGH.setting!!.alarmPrefix, R.string.high_alarm_notification_name, longArrayOf(0, 500, 500, 500, 500, 500, 500, 500)),
    VERY_HIGH(AlarmType.VERY_HIGH.setting!!.alarmPrefix, R.string.very_high_alarm_notification_name, longArrayOf(0, 800, 500, 800, 800, 600, 800, 800, 500, 800, 800, 600, 800)),
    OBSOLETE(AlarmType.OBSOLETE.setting!!.alarmPrefix, R.string.obsolete_alarm_notification_name, longArrayOf(0, 600, 500, 500, 500, 600, 500, 500)),
    RISING_FAST(AlarmType.RISING_FAST.setting!!.alarmPrefix, R.string.rising_fast_alarm_notification_name, longArrayOf(0, 1000, 200, 200, 50, 200, 50, 200, 50, 200, 50, 200, 50, 200, 50, 1000, 200, 200, 50, 200, 50, 200, 50, 200, 50, 200, 50, 200, 50)),
    FALLING_FAST(AlarmType.FALLING_FAST.setting!!.alarmPrefix, R.string.falling_fast_alarm_notification_name, longArrayOf(0, 100, 50, 100, 50, 100, 50, 100, 50, 100, 50, 100, 50, 100, 50, 100, 50, 100, 50, 100, 50, 100, 50, 100, 50, 100, 50, 100, 50, 100, 50, 100, 50, 100, 50, 100, 50, 100, 50, 100, 50, 100, 50, 100, 50, 100, 50, 100, 50, 100, 50, 100, 50, 100, 50, 100, 50, 100, 50, 100, 50)),
    SUPER_MARIO("super_mario", R.string.vibrate_pattern_super_mario, longArrayOf(0,125,75,125,275,200,275,125,75,125,275,200,600,200,600)),
    TURTLES("turtles", R.string.vibrate_pattern_turtles, longArrayOf(0, 75,75,75,75,75,75,75,75,150,150,150,450,75,75,75,75,75,525)),
    FINAL_FANTASY("final_fantasy", R.string.vibrate_pattern_final_fantasy, longArrayOf(0,50,100,50,100,50,100,400,100,300,100,350,50,200,100,100,50,600)),
    STAR_WARS("star_wars", R.string.vibrate_pattern_star_wars, longArrayOf(0,500,110,500,110,450,110,200,110,170,40,450,110,200,110,170,40,500)),
    POWER_RANGERS("power_rangers", R.string.vibrate_pattern_power_rangers, longArrayOf(0,150,150,150,150,75,75,150,150,150,150,450)),
    JAMES_BOND("james_bond", R.string.vibrate_pattern_james_bond, longArrayOf(0,200,100,200,275,425,100,200,100,200,275,425,100,75,25,75,125,75,25,75,125,100,100)),
    MORTAL_KOMBAT("mortal_kombat", R.string.vibrate_pattern_mortal_kombat, longArrayOf(0,100,200,100,200,100,200,100,200,100,100,100,100,100,200,100,200,100,200,100,200,100,100,100,100,100,200,100,200,100,200,100,200,100,100,100,100,100,100,100,100,100,100,50,50,100,800)),
    SHORTY("shorty", R.string.vibrate_pattern_shorty, longArrayOf(0, 400, 200, 500))
    ;

    companion object {
        fun getByKey(pattern: String): VibratePattern {
            entries.forEach {
                if(it.key == pattern) {
                    return it
                }
            }
            throw IllegalArgumentException("Unknown vibrate pattern: $pattern")
        }
    }
}