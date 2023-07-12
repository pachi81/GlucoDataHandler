package de.michelinside.glucodatahandler

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Icon
import android.os.Bundle
import android.util.Log
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.Utils
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifyDataSource



object PermanentNotification: NotifierInterface, SharedPreferences.OnSharedPreferenceChangeListener {
    private const val LOG_ID = "GlucoDataHandler.PermanentNotification"
    private const val CHANNEL_ID = "GlucoDataNotify_permanent"
    private const val CHANNEL_NAME = "Permanent notification"
    private const val NOTIFICATION_ID = 123
    private lateinit var notificationMgr: NotificationManager
    private lateinit var notificationCompat: Notification.Builder
    private lateinit var sharedPref: SharedPreferences

    enum class StatusBarIcon(val pref: String) {
        APP("app"),
        GLUCOSE("glucose"),
        TREND("trend")
    }

    private var statusBarIcon: StatusBarIcon = StatusBarIcon.APP

    fun create(context: Context) {
        try {
            Log.d(LOG_ID, "create called")
            createNofitication(context)
            sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            sharedPref.registerOnSharedPreferenceChangeListener(this)
            updatePreferences()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "create exception: " + exc.toString() )
        }
    }

    fun destroy() {
        try {
            Log.d(LOG_ID, "destroy called")
            InternalNotifier.remNotifier(this)
            sharedPref.unregisterOnSharedPreferenceChangeListener(this)
            removeNotification()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "destroy exception: " + exc.toString() )
        }
    }

    override fun OnNotifyData(context: Context, dataSource: NotifyDataSource, extras: Bundle?) {
        try {
            Log.d(LOG_ID, "OnNotifyData called")
            showNotification()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: " + exc.toString() )
        }
    }


    private fun createNotificationChannel(context: Context) {
        notificationMgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationChannel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationChannel.setSound(null, null)   // silent
        notificationMgr.createNotificationChannel(notificationChannel)
    }


    private fun createNofitication(context: Context) {
        createNotificationChannel(context)
        val launchIntent = Intent(context, MainActivity::class.java)
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            4,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        notificationCompat = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setShowWhen(true)
            .setColorized(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
    }


    private fun removeNotification() {
        notificationMgr.cancel(NOTIFICATION_ID)  // remove notification
    }

    private fun getStatusBarIcon(): Icon {
        return when(statusBarIcon) {
            StatusBarIcon.GLUCOSE -> Utils.getGlucoseAsIcon()
            StatusBarIcon.TREND -> Utils.getRateAsIcon()
            else -> Icon.createWithResource(GlucoDataService.context, R.mipmap.ic_launcher)
        }
    }

    private fun showNotification() {
        try {
            Log.d(LOG_ID, "showNotification called")
            val notification = notificationCompat
                .setSmallIcon(getStatusBarIcon())
                .setLargeIcon(Utils.getRateAsBitmap())
                .setWhen(ReceiveData.time)
                .setContentTitle(ReceiveData.getClucoseAsString())
                .setContentText("Delta: " + ReceiveData.getDeltaAsString())
                .setColor(ReceiveData.getClucoseColor())
                .build()
            notification.flags = notification.flags or Notification.FLAG_NO_CLEAR
            notificationMgr.notify(
                NOTIFICATION_ID,
                notification
            )
        } catch (exc: Exception) {
            Log.e(LOG_ID, "showNotification exception: " + exc.toString() )
        }
    }

    private fun updatePreferences() {
        try {
            statusBarIcon = when(sharedPref.getString(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_ICON, StatusBarIcon.APP.pref)) {
                StatusBarIcon.GLUCOSE.pref -> StatusBarIcon.GLUCOSE
                StatusBarIcon.TREND.pref -> StatusBarIcon.TREND
                else -> StatusBarIcon.APP
            }
            if (sharedPref.getBoolean(Constants.SHARED_PREF_PERMANENT_NOTIFICATION, false)) {
                Log.i(LOG_ID, "activate permanent notification")
                val filter = mutableSetOf(
                    NotifyDataSource.BROADCAST,
                    NotifyDataSource.MESSAGECLIENT,
                    NotifyDataSource.SETTINGS,
                    NotifyDataSource.OBSOLETE_VALUE)   // to trigger re-start for the case of stopped by the system
                InternalNotifier.addNotifier(this, filter)
                showNotification()
            }
            else {
                Log.i(LOG_ID, "deactivate permanent notification")
                InternalNotifier.remNotifier(this)
                removeNotification()
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updatePreferences exception: " + exc.toString() )
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        try {
            Log.d(LOG_ID, "onSharedPreferenceChanged called for key " + key)
            if (key == Constants.SHARED_PREF_PERMANENT_NOTIFICATION ||
                key == Constants.SHARED_PREF_PERMANENT_NOTIFICATION_ICON) {
                updatePreferences()
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString() )
        }
    }

}