package de.michelinside.glucodatahandler.common.notification

import android.content.Context
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.R

enum class AlarmState(val icon: Int) {
    DISABLED(R.drawable.notification_disabled),
    ACTIVE(R.drawable.notification_on),
    SNOOZE(R.drawable.notification_snooze),
    INACTIVE(R.drawable.notification_minus);

    companion object {
        fun currentState(context: Context): AlarmState {
            val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            val enabled = sharedPref.getBoolean(Constants.SHARED_PREF_ALARM_NOTIFICATION_ENABLED, false) || sharedPref.getBoolean(Constants.SHARED_PREF_NOTIFICATION_VIBRATE, false)
            if(enabled) {
                if(AlarmHandler.isSnoozeActive)
                    return SNOOZE
                return ACTIVE
            }
            // disabled
            return DISABLED
        }
    }
}