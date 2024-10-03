package de.michelinside.glucodatahandler.common.notification

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.R
import de.michelinside.glucodatahandler.common.utils.Utils

enum class ChannelType(val channelId: String, val nameResId: Int, val descrResId: Int, val importance: Int = NotificationManager.IMPORTANCE_DEFAULT) {
    MOBILE_FOREGROUND("GDH_foreground", R.string.mobile_foreground_notification_name, R.string.mobile_foreground_notification_descr ),
    MOBILE_SECOND("GlucoDataNotify_permanent", R.string.mobile_second_notification_name, R.string.mobile_second_notification_descr ),
    WORKER("worker_notification_01", R.string.worker_notification_name, R.string.worker_notification_descr, NotificationManager.IMPORTANCE_LOW ),
    WEAR_FOREGROUND("glucodatahandler_service_01", R.string.wear_foreground_notification_name, R.string.wear_foreground_notification_descr, NotificationManager.IMPORTANCE_LOW),
    ANDROID_AUTO("GlucoDataNotify_Car", R.string.android_auto_notification_name, R.string.android_auto_notification_descr ),
    ANDROID_AUTO_FOREGROUND("GlucoDataAuto_foreground", R.string.mobile_foreground_notification_name, R.string.mobile_foreground_notification_descr, NotificationManager.IMPORTANCE_LOW ),
    ALARM("gdh_alarm_notification_channel_66", R.string.alarm_notification_name, R.string.alarm_notification_descr, NotificationManager.IMPORTANCE_MAX );
}
object Channels {
    private val LOG_ID = "GDH.Channels"
    private var notificationMgr: NotificationManager? = null

    private val obsoleteNotifications = mutableSetOf(
        "GlucoDataNotify_foreground"
    )

    private fun cleanUpNotifications(notificationManager: NotificationManager) {
        obsoleteNotifications.forEach {
            notificationManager.deleteNotificationChannel(it)
        }
    }

    fun getNotificationManager(context: Context? = null): NotificationManager {
        if (notificationMgr == null) {
            notificationMgr = if (context != null)
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            else
                GlucoDataService.context!!.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            cleanUpNotifications(notificationMgr!!)
        }
        return notificationMgr!!
    }

    fun getNotificationChannel(context: Context, type: ChannelType, silent: Boolean = true): NotificationChannel {
        val notificationChannel = NotificationChannel(
            type.channelId,
            context.getString(type.nameResId),
            type.importance
        )
        notificationChannel.description = context.getString(type.descrResId)
        notificationChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        if (silent)
            notificationChannel.setSound(null, null)
        return notificationChannel
    }

    fun createNotificationChannel(context: Context, type: ChannelType, silent: Boolean = true) {
        getNotificationManager(context).createNotificationChannel(getNotificationChannel(context, type, silent))
    }

    fun deleteNotificationChannel(context: Context, type: ChannelType) {
        getNotificationManager(context).deleteNotificationChannel(type.channelId)
    }

    @SuppressLint("InlinedApi")
    fun notificationActive(context: Context): Boolean {
        return Utils.checkPermission(context, android.Manifest.permission.POST_NOTIFICATIONS, Build.VERSION_CODES.TIRAMISU)
    }

    fun notificationChannelActive(context: Context, type: ChannelType): Boolean {
        if(notificationActive(context)) {
            val channel = getNotificationManager(context).getNotificationChannel(type.channelId)
            if (channel != null) {
                Log.d(LOG_ID, "Channel: prio=${channel.importance}")
                return (channel.importance > NotificationManager.IMPORTANCE_NONE)
            } else {
                Log.w(LOG_ID, "Notification channel $type still not exists!")
            }
        }
        return false
    }
}