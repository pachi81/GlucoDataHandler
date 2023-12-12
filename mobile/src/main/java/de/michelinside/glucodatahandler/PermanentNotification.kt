package de.michelinside.glucodatahandler

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
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
import de.michelinside.glucodatahandler.common.utils.Utils
import de.michelinside.glucodatahandler.common.utils.BitmapUtils
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource


object PermanentNotification: NotifierInterface, SharedPreferences.OnSharedPreferenceChangeListener {
    private const val LOG_ID = "GDH.PermanentNotification"
    private const val CHANNEL_ID = "GlucoDataNotify_permanent"
    private const val CHANNEL_NAME = "Permanent notification"
    private const val FOREGROUND_CHANNEL_ID = "GlucoDataNotify_foreground"
    private const val FOREGROUND_CHANNEL_NAME = "Foreground notification"
    private const val SECOND_NOTIFICATION_ID = 124
    private lateinit var notificationMgr: NotificationManager
    private lateinit var notificationCompat: Notification.Builder
    private lateinit var foregroundNotificationCompat: Notification.Builder
    private lateinit var sharedPref: SharedPreferences

    enum class StatusBarIcon(val pref: String) {
        APP("app"),
        GLUCOSE("glucose"),
        TREND("trend"),
        DELTA("delta")
    }

    fun create(context: Context) {
        try {
            Log.v(LOG_ID, "create called")
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
            Log.v(LOG_ID, "destroy called")
            InternalNotifier.remNotifier(GlucoDataService.context!!, this)
            sharedPref.unregisterOnSharedPreferenceChangeListener(this)
            removeNotifications()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "destroy exception: " + exc.toString() )
        }
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            Log.v(LOG_ID, "OnNotifyData called")
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
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationChannel.setSound(null, null)   // silent
        notificationMgr.createNotificationChannel(notificationChannel)
        notificationMgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val foregroundChannel = NotificationChannel(
            FOREGROUND_CHANNEL_ID,
            FOREGROUND_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        )
        foregroundChannel.setSound(null, null)   // silent
        notificationMgr.createNotificationChannel(foregroundChannel)
    }

    private fun createNofitication(context: Context) {
        createNotificationChannel(context)

        notificationCompat = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(Utils.getAppIntent(context, MainActivity::class.java, 5, false))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setShowWhen(true)
            .setColorized(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)

        foregroundNotificationCompat = Notification.Builder(context, FOREGROUND_CHANNEL_ID)
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
        //notificationMgr.cancel(NOTIFICATION_ID)  // remove notification
        showPrimaryNotification(false)
        notificationMgr.cancel(SECOND_NOTIFICATION_ID)
    }

    private fun getStatusBarIcon(iconKey: String): Icon {
        val bigIcon = sharedPref.getBoolean(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_USE_BIG_ICON, false)
        return when(sharedPref.getString(iconKey, StatusBarIcon.APP.pref)) {
            StatusBarIcon.GLUCOSE.pref -> BitmapUtils.getGlucoseAsIcon(roundTarget=!bigIcon)
            StatusBarIcon.TREND.pref -> BitmapUtils.getRateAsIcon(roundTarget=true, resizeFactor = if (bigIcon) 1.5F else 1F)
            StatusBarIcon.DELTA.pref -> BitmapUtils.getDeltaAsIcon(roundTarget=!bigIcon)
            else -> Icon.createWithResource(GlucoDataService.context, R.mipmap.ic_launcher)
        }
    }

    fun getNotification(withContent: Boolean, iconKey: String, foreground: Boolean) : Notification {
        var remoteViews: RemoteViews? = null
        if (withContent) {
            remoteViews = RemoteViews(GlucoDataService.context!!.packageName, R.layout.notification)
            remoteViews.setTextViewText(R.id.glucose, ReceiveData.getClucoseAsString())
            remoteViews.setTextColor(R.id.glucose, ReceiveData.getClucoseColor())
            remoteViews.setImageViewBitmap(R.id.trendImage, BitmapUtils.getRateAsBitmap())
            remoteViews.setTextViewText(R.id.deltaText, "Delta: " + ReceiveData.getDeltaAsString())
            if (ReceiveData.isObsolete(Constants.VALUE_OBSOLETE_SHORT_SEC)) {
                if (!ReceiveData.isObsolete())
                    remoteViews.setInt(R.id.glucose, "setPaintFlags", Paint.STRIKE_THRU_TEXT_FLAG or Paint.ANTI_ALIAS_FLAG)
                remoteViews.setTextColor(R.id.deltaText, Color.GRAY )
            }
        }

        val notificationBuilder = if(foreground) foregroundNotificationCompat else notificationCompat
        val notification = notificationBuilder
            .setSmallIcon(getStatusBarIcon(iconKey))
            .setWhen(ReceiveData.time)
            .setCustomContentView(remoteViews)
            .setCustomBigContentView(null)
            .setColorized(false)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setContentTitle(if (withContent) ReceiveData.getClucoseAsString() else "")
            .setContentText(if (withContent) "Delta: " + ReceiveData.getDeltaAsString() else "")
            .build()

        notification.visibility
        notification.flags = notification.flags or Notification.FLAG_NO_CLEAR
        return notification
    }

    private fun showNotification(id: Int, withContent: Boolean, iconKey: String, foreground: Boolean) {
        try {
            Log.v(LOG_ID, "showNotification called for id " + id)
            notificationMgr.notify(
                id,
                getNotification(withContent, iconKey, foreground)
            )
        } catch (exc: Exception) {
            Log.e(LOG_ID, "showNotification exception: " + exc.toString() )
        }
    }

    private fun showNotifications() {
        showPrimaryNotification(true)
        if (sharedPref.getBoolean(Constants.SHARED_PREF_SECOND_PERMANENT_NOTIFICATION, false)) {
            Log.d(LOG_ID, "show second notification")
            showNotification(SECOND_NOTIFICATION_ID, false, Constants.SHARED_PREF_SECOND_PERMANENT_NOTIFICATION_ICON, false)
        } else {
            notificationMgr.cancel(SECOND_NOTIFICATION_ID)
        }
    }

    private fun showPrimaryNotification(show: Boolean) {
        Log.d(LOG_ID, "showPrimaryNotification " + show)
        if (show)
            showNotification(GlucoDataService.NOTIFICATION_ID, !sharedPref.getBoolean(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_EMPTY, false), Constants.SHARED_PREF_PERMANENT_NOTIFICATION_ICON, true)
        if (show != GlucoDataService.foreground) {
            Log.d(LOG_ID, "change foreground notification mode")
            with(sharedPref.edit()) {
                putBoolean(Constants.SHARED_PREF_FOREGROUND_SERVICE, show)
                apply()
            }
            val serviceIntent =
                Intent(GlucoDataService.context!!, GlucoDataServiceMobile::class.java)
            if (show)
                serviceIntent.putExtra(Constants.SHARED_PREF_FOREGROUND_SERVICE, true)
            else
                serviceIntent.putExtra(Constants.ACTION_STOP_FOREGROUND, true)
            GlucoDataService.context!!.startService(serviceIntent)
        }
    }

    private fun updatePreferences() {
        try {
            if (sharedPref.getBoolean(Constants.SHARED_PREF_PERMANENT_NOTIFICATION, true)) {
                Log.i(LOG_ID, "activate permanent notification")
                val filter = mutableSetOf(
                    NotifySource.BROADCAST,
                    NotifySource.MESSAGECLIENT,
                    NotifySource.SETTINGS,
                    NotifySource.OBSOLETE_VALUE)   // to trigger re-start for the case of stopped by the system
                InternalNotifier.addNotifier(GlucoDataService.context!!, this, filter)
                showNotifications()
            }
            else {
                Log.i(LOG_ID, "deactivate permanent notification")
                InternalNotifier.remNotifier(GlucoDataService.context!!, this)
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