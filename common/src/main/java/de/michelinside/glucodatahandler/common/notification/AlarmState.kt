package de.michelinside.glucodatahandler.common.notification

import android.content.Context
import de.michelinside.glucodatahandler.common.AppSource
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.R

enum class AlarmState(val icon: Int, val descr: Int) {
    DISABLED(R.drawable.notification_disabled, R.string.disabled),
    ACTIVE(R.drawable.notification_on, R.string.enabled),
    ALARM(R.drawable.notification_alarm, R.string.info_label_alarm),
    SNOOZE(R.drawable.notification_snooze, R.string.alarm_state_snooze),
    INACTIVE(R.drawable.notification_minus, R.string.alarm_state_inactive),
    TEMP_DISABLED(R.drawable.notification_clock_snooze, R.string.alarm_state_temp_disabled);

    companion object {
        fun currentState(context: Context): AlarmState {
            val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            var enabled = sharedPref.getBoolean(Constants.SHARED_PREF_ALARM_NOTIFICATION_ENABLED, false)
            if(!enabled && GlucoDataService.appSource == AppSource.WEAR_APP)  // on wear, vibrate only will also enable alarms without notification -> this works not on phone!
                enabled = sharedPref.getBoolean(Constants.SHARED_PREF_NOTIFICATION_VIBRATE, false)
            if(enabled) {
                if(AlarmHandler.isTempInactive)
                    return TEMP_DISABLED
                if(AlarmHandler.isSnoozeActive)
                    return SNOOZE
                return ACTIVE
            }
            // disabled
            return DISABLED
        }

        fun isActive(state: AlarmState?): Boolean {
            return state == ACTIVE || state == ALARM
        }
    }
}