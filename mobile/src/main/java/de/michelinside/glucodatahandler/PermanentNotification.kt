package de.michelinside.glucodatahandler

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Icon
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
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
    private const val SECOND_NOTIFICATION_ID = 124
    private lateinit var notificationMgr: NotificationManager
    private lateinit var notificationCompat: Notification.Builder
    private lateinit var sharedPref: SharedPreferences

    enum class StatusBarIcon(val pref: String) {
        APP("app"),
        GLUCOSE("glucose"),
        TREND("trend"),
        DELTA("delta")
    }

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
            removeNotifications()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "destroy exception: " + exc.toString() )
        }
    }

    override fun OnNotifyData(context: Context, dataSource: NotifyDataSource, extras: Bundle?) {
        try {
            Log.d(LOG_ID, "OnNotifyData called")
            showNotifications()
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

        notificationCompat = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(Utils.getAppIntent(context, MainActivity::class.java, 4, false))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setShowWhen(true)
            .setColorized(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
    }


    private fun removeNotifications() {
        notificationMgr.cancel(NOTIFICATION_ID)  // remove notification
        notificationMgr.cancel(SECOND_NOTIFICATION_ID)
    }

    private fun getStatusBarIcon(iconKey: String): Icon {
        val bigIcon = sharedPref.getBoolean(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_USE_BIG_ICON, false)
        return when(sharedPref.getString(iconKey, StatusBarIcon.APP.pref)) {
            StatusBarIcon.GLUCOSE.pref -> Utils.getGlucoseAsIcon(roundTarget=!bigIcon)
            StatusBarIcon.TREND.pref -> Utils.getRateAsIcon(roundTarget=true, resizeFactor = if (bigIcon) 1.5F else 1F)
            StatusBarIcon.DELTA.pref -> Utils.getDeltaAsIcon(roundTarget=!bigIcon)
            else -> Icon.createWithResource(GlucoDataService.context, R.mipmap.ic_launcher)
        }
    }

    private fun showNotification(notificaitonId: Int, withContent: Boolean, iconKey: String, colored: Boolean) {
        try {
            Log.d(LOG_ID, "showNotification called")
            var remoteViews: RemoteViews? = null
            if (withContent) {
                remoteViews = RemoteViews(GlucoDataService.context!!.packageName, R.layout.notification)
                remoteViews.setTextViewText(R.id.glucose, ReceiveData.getClucoseAsString())
                remoteViews.setTextColor(R.id.glucose, ReceiveData.getClucoseColor())
                remoteViews.setImageViewBitmap(R.id.trendImage, Utils.getRateAsBitmap())
                remoteViews.setTextViewText(R.id.deltaText, "Delta: " + ReceiveData.getDeltaAsString())
                if (ReceiveData.isObsolete(Constants.VALUE_OBSOLETE_SHORT_SEC)) {
                    if (!ReceiveData.isObsolete())
                        remoteViews.setInt(R.id.glucose, "setPaintFlags", Paint.STRIKE_THRU_TEXT_FLAG or Paint.ANTI_ALIAS_FLAG)
                    remoteViews.setTextColor(R.id.deltaText, Color.GRAY )
                }
            }

            val notification = notificationCompat
                .setSmallIcon(getStatusBarIcon(iconKey))
                .setWhen(ReceiveData.time)
                .setCustomContentView(remoteViews)
                .setCustomBigContentView(null)
                .setColor(ReceiveData.getClucoseColor(!colored))
                .setStyle(Notification.DecoratedCustomViewStyle())
                .setContentTitle(if (withContent) ReceiveData.getClucoseAsString() else "")
                .setContentText(if (withContent) "Delta: " + ReceiveData.getDeltaAsString() else "")
                .build()

            notification.visibility
            notification.flags = notification.flags or Notification.FLAG_NO_CLEAR
            notificationMgr.notify(
                notificaitonId,
                notification
            )
        } catch (exc: Exception) {
            Log.e(LOG_ID, "showNotification exception: " + exc.toString() )
        }
    }

    private fun showNotifications() {
        showNotification(NOTIFICATION_ID, !sharedPref.getBoolean(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_EMPTY, false), Constants.SHARED_PREF_PERMANENT_NOTIFICATION_ICON, true)
        if (sharedPref.getBoolean(Constants.SHARED_PREF_SECOND_PERMANENT_NOTIFICATION, false)) {
            showNotification(SECOND_NOTIFICATION_ID, false, Constants.SHARED_PREF_SECOND_PERMANENT_NOTIFICATION_ICON, false)
        } else {
            notificationMgr.cancel(SECOND_NOTIFICATION_ID)
        }
    }

    private fun updatePreferences() {
        try {
            if (sharedPref.getBoolean(Constants.SHARED_PREF_PERMANENT_NOTIFICATION, false)) {
                Log.i(LOG_ID, "activate permanent notification")
                val filter = mutableSetOf(
                    NotifyDataSource.BROADCAST,
                    NotifyDataSource.MESSAGECLIENT,
                    NotifyDataSource.SETTINGS,
                    NotifyDataSource.OBSOLETE_VALUE)   // to trigger re-start for the case of stopped by the system
                InternalNotifier.addNotifier(this, filter)
                showNotifications()
            }
            else {
                Log.i(LOG_ID, "deactivate permanent notification")
                InternalNotifier.remNotifier(this)
                removeNotifications()
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updatePreferences exception: " + exc.toString() )
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        try {
            Log.d(LOG_ID, "onSharedPreferenceChanged called for key " + key)
            when(key) {
                Constants.SHARED_PREF_PERMANENT_NOTIFICATION,
                Constants.SHARED_PREF_PERMANENT_NOTIFICATION_ICON,
                Constants.SHARED_PREF_SECOND_PERMANENT_NOTIFICATION,
                Constants.SHARED_PREF_SECOND_PERMANENT_NOTIFICATION_ICON,
                Constants.SHARED_PREF_PERMANENT_NOTIFICATION_USE_BIG_ICON,
                Constants.SHARED_PREF_PERMANENT_NOTIFICATION_EMPTY -> {
                    updatePreferences()
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString() )
        }
    }

}