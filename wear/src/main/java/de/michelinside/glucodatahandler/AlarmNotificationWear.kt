package de.michelinside.glucodatahandler

import android.app.Notification
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.notification.AlarmNotificationBase
import de.michelinside.glucodatahandler.common.notification.AlarmType
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.R as CR


object AlarmNotificationWear : AlarmNotificationBase() {
    private var forceVibrateInstance = false

    private var forceVibrate: Boolean get() = forceVibrateInstance
        set(value) {
            if(forceVibrateInstance != value) {
                forceVibrateInstance = value
                Log.i(LOG_ID, "Set force vibration=$forceVibrateInstance")
            }
        }
    override val active: Boolean get() {
        return getEnabled() || forceVibrate
    }


    override fun buildNotification(
        notificationBuilder: Notification.Builder,
        context: Context,
        alarmType: AlarmType
    ) {
        if (getAddSnooze()) {
            notificationBuilder.extend(Notification.WearableExtender()
                .addAction(createAction(context, context.getString(CR.string.snooze) + ": 60", 60L, getNotificationId(alarmType)))
                .addAction(createAction(context, context.getString(CR.string.snooze) + ": 90", 90L, getNotificationId(alarmType)))
                .addAction(createAction(context, context.getString(CR.string.snooze) + ": 120", 120L, getNotificationId(alarmType)))
            )
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        try {
            super.onSharedPreferenceChanged(sharedPreferences, key)
            if (key == null) {
                onSharedPreferenceChanged(sharedPreferences, Constants.SHARED_PREF_NOTIFICATION_VIBRATE)
            } else {
                when(key) {
                    Constants.SHARED_PREF_NOTIFICATION_VIBRATE ->
                        forceVibrate = sharedPreferences.getBoolean(Constants.SHARED_PREF_NOTIFICATION_VIBRATE, forceVibrate)
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString())
        }
    }


    override fun executeTest(alarmType: AlarmType, context: Context) {
        Log.v(LOG_ID, "executeTest called for $alarmType")
        if(!forceVibrate)
            triggerNotification(alarmType, context, true)
        else
            vibrate(alarmType, context, false,true)
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            super.OnNotifyData(context, dataSource, extras)
            if (!active && forceVibrate && dataSource == NotifySource.ALARM_TRIGGER && ReceiveData.forceAlarm) {
                vibrate(ReceiveData.getAlarmType(), context, false,true)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: " + exc.toString())
        }
    }
}