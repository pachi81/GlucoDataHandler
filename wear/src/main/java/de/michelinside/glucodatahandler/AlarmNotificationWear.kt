package de.michelinside.glucodatahandler

import android.app.Notification
import android.content.Context
import de.michelinside.glucodatahandler.common.notification.AlarmNotificationBase
import de.michelinside.glucodatahandler.common.notification.AlarmType
import de.michelinside.glucodatahandler.common.R as CR


object AlarmNotificationWear : AlarmNotificationBase() {
    override val active: Boolean get() {
        return getEnabled() || vibrateOnly
    }


    override fun buildNotification(
        notificationBuilder: Notification.Builder,
        context: Context,
        alarmType: AlarmType
    ) {
        val extender = Notification.WearableExtender()
        extender.addAction(createStopAction(context, context.resources.getString(CR.string.btn_dismiss), getNotificationId(alarmType)))
        if (getAddSnooze()) {
            extender
                .addAction(createSnoozeAction(context, context.getString(CR.string.snooze) + ": 60", 60L, getNotificationId(alarmType)))
                .addAction(createSnoozeAction(context, context.getString(CR.string.snooze) + ": 90", 90L, getNotificationId(alarmType)))
                .addAction(createSnoozeAction(context, context.getString(CR.string.snooze) + ": 120", 120L, getNotificationId(alarmType)))
        }
        notificationBuilder.extend(extender)
    }

}