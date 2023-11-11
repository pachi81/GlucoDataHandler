package de.michelinside.glucodatahandler

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import de.michelinside.glucodatahandler.common.*
import de.michelinside.glucodatahandler.common.R as CR
import de.michelinside.glucodatahandler.common.notifier.*


class GlucoDataServiceWear: GlucoDataService(AppSource.WEAR_APP) {
    private val LOG_ID = "GlucoDataHandler.GlucoDataServiceWear"
    init {
        Log.d(LOG_ID, "init called")
        InternalNotifier.addNotifier(
            ActiveComplicationHandler, mutableSetOf(
                NotifySource.MESSAGECLIENT,
                NotifySource.BROADCAST,
                NotifySource.SETTINGS,
                NotifySource.TIME_VALUE
            )
        )
        InternalNotifier.addNotifier(
            BatteryLevelComplicationUpdater,
            mutableSetOf(
                NotifySource.CAPILITY_INFO,
                NotifySource.BATTERY_LEVEL,
                NotifySource.NODE_BATTERY_LEVEL
            )
        )
    }

    companion object {
        fun start(context: Context) {
            start(context, GlucoDataServiceWear::class.java)
        }
    }

    override fun onCreate() {
        Log.d(LOG_ID, "onCreate called")
        super.onCreate()
        ActiveComplicationHandler.OnNotifyData(this, NotifySource.CAPILITY_INFO, null)
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            Log.d(LOG_ID, "OnNotifyData called for source " + dataSource.toString())
            start(context)
            super.OnNotifyData(context, dataSource, extras)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: " + exc.message.toString())
        }
    }

    override fun getNotification(): Notification {
        val channelId = "glucodatahandler_service_01"
        val channel = NotificationChannel(
            channelId,
            "Foregorund GlucoDataService",
            NotificationManager.IMPORTANCE_MIN
        )
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            channel
        )
        val notificationIntent = Intent(this, WaerActivity::class.java)

        val pendingIntent = PendingIntent.getActivity(
            this, 1,
            notificationIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return Notification.Builder(this, channelId)
            .setContentTitle(getString(CR.string.forground_notification_descr))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
    }
}