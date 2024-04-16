package de.michelinside.glucodatahandler

import android.app.Notification
import android.content.Context
import android.os.Bundle
import android.util.Log
import de.michelinside.glucodatahandler.common.*
import de.michelinside.glucodatahandler.common.notification.ChannelType
import de.michelinside.glucodatahandler.common.notification.Channels
import de.michelinside.glucodatahandler.common.R as CR
import de.michelinside.glucodatahandler.common.notifier.*
import de.michelinside.glucodatahandler.common.utils.Utils


class GlucoDataServiceWear: GlucoDataService(AppSource.WEAR_APP), NotifierInterface {
    private val LOG_ID = "GDH.GlucoDataServiceWear"
    init {
        Log.d(LOG_ID, "init called")
        InternalNotifier.addNotifier( this,
            ActiveComplicationHandler, mutableSetOf(
                NotifySource.MESSAGECLIENT,
                NotifySource.BROADCAST,
                NotifySource.SETTINGS,
                NotifySource.TIME_VALUE
            )
        )
        InternalNotifier.addNotifier( this,
            BatteryLevelComplicationUpdater,
            mutableSetOf(
                NotifySource.CAPILITY_INFO,
                NotifySource.BATTERY_LEVEL,
                NotifySource.NODE_BATTERY_LEVEL
            )
        )
    }

    companion object {
        fun start(context: Context, force: Boolean = false) {
            start(AppSource.WEAR_APP, context, GlucoDataServiceWear::class.java, force)
        }
    }

    override fun onCreate() {
        try {
            Log.d(LOG_ID, "onCreate called")
            super.onCreate()
            AlarmNotificationWear.initNotifications(this)
            val filter = mutableSetOf(
                NotifySource.BROADCAST,
                NotifySource.MESSAGECLIENT,
                NotifySource.OBSOLETE_VALUE) // to trigger re-start for the case of stopped by the system
            InternalNotifier.addNotifier(this, this, filter)
            ActiveComplicationHandler.OnNotifyData(this, NotifySource.CAPILITY_INFO, null)
        } catch (ex: Exception) {
            Log.e(LOG_ID, "onCreate exception: " + ex)
        }
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            Log.d(LOG_ID, "OnNotifyData called for source " + dataSource.toString())
            start(context)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: " + exc.message.toString())
        }
    }

    override fun getNotification(): Notification {
        Channels.createNotificationChannel(this, ChannelType.WEAR_FOREGROUND)

        val pendingIntent = Utils.getAppIntent(this, WearActivity::class.java, 11, false)

        return Notification.Builder(this, ChannelType.WEAR_FOREGROUND.channelId)
            .setContentTitle(getString(CR.string.forground_notification_descr))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
    }
}