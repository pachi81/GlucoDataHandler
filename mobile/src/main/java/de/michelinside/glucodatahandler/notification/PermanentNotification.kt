package de.michelinside.glucodatahandler

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Icon
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.notification.AlarmType
import de.michelinside.glucodatahandler.common.utils.BitmapUtils
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.notification.ChannelType
import de.michelinside.glucodatahandler.common.notification.Channels
import de.michelinside.glucodatahandler.common.utils.PackageUtils


object PermanentNotification: NotifierInterface, SharedPreferences.OnSharedPreferenceChangeListener {
    private const val LOG_ID = "GDH.PermanentNotification"
    private const val SECOND_NOTIFICATION_ID = 124
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
        Channels.createNotificationChannel(context, ChannelType.MOBILE_FOREGROUND)
        Channels.createNotificationChannel(context, ChannelType.MOBILE_SECOND)
    }

    private fun createNofitication(context: Context) {
        Log.d(LOG_ID, "createNofitication called")
        createNotificationChannel(context)

        Channels.getNotificationManager().cancel(GlucoDataService.NOTIFICATION_ID)
        Channels.getNotificationManager().cancel(SECOND_NOTIFICATION_ID)

        notificationCompat = Notification.Builder(context, ChannelType.MOBILE_SECOND.channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setShowWhen(true)
            .setColorized(true)
            .setGroup(ChannelType.MOBILE_SECOND.channelId)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)

        foregroundNotificationCompat = Notification.Builder(context, ChannelType.MOBILE_FOREGROUND.channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setShowWhen(true)
            .setColorized(true)
            .setGroup(ChannelType.MOBILE_FOREGROUND.channelId)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
    }

    private fun removeNotifications() {
        //notificationMgr.cancel(NOTIFICATION_ID)  // remove notification
        showPrimaryNotification(false)
        Channels.getNotificationManager().cancel(SECOND_NOTIFICATION_ID)
    }

    private fun getStatusBarIcon(iconKey: String): Icon {
        val bigIcon = sharedPref.getBoolean(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_USE_BIG_ICON, false)
        val coloredIcon = sharedPref.getBoolean(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_COLORED_ICON, true)
        return when(sharedPref.getString(iconKey, StatusBarIcon.APP.pref)) {
            StatusBarIcon.GLUCOSE.pref -> BitmapUtils.getGlucoseAsIcon(roundTarget=!bigIcon, color = if(coloredIcon) ReceiveData.getGlucoseColor() else Color.WHITE, withShadow = if(coloredIcon) true else false)
            StatusBarIcon.TREND.pref -> BitmapUtils.getRateAsIcon(
                color = if(coloredIcon) ReceiveData.getGlucoseColor() else Color.WHITE,
                resizeFactor = if (bigIcon) 1.5F else 1F,
                withShadow = if(coloredIcon) true else false
            )
            StatusBarIcon.DELTA.pref -> BitmapUtils.getDeltaAsIcon(roundTarget=!bigIcon, color = if(coloredIcon) ReceiveData.getGlucoseColor(true) else Color.WHITE)
            else -> Icon.createWithResource(GlucoDataService.context, R.mipmap.ic_launcher)
        }
    }

    private fun getTapActionIntent(foreground: Boolean): PendingIntent? {
        val tapAction = if(foreground)
            sharedPref.getString(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_TAP_ACTION, "")
        else
            sharedPref.getString(Constants.SHARED_PREF_SECOND_PERMANENT_NOTIFICATION_TAP_ACTION, "")
        if(tapAction.isNullOrEmpty())
            return null
        val requestCode = if(foreground) 4 else 5
        return PackageUtils.getTapActionIntent(GlucoDataService.context!!, tapAction, requestCode)
    }

    @SuppressLint("SetTextI18n")
    fun createNotificationView(context: Context): Bitmap? {
        try {
            val notificationView = View.inflate(context, R.layout.notification_layout, null)
            val textGlucose: TextView = notificationView.findViewById(R.id.glucose)
            val trendIcon: ImageView = notificationView.findViewById(R.id.trendImage)
            val textDelta: TextView = notificationView.findViewById(R.id.deltaText)
            val textIob: TextView = notificationView.findViewById(R.id.iobText)
            val textCob: TextView = notificationView.findViewById(R.id.cobText)


            textGlucose.text = ReceiveData.getGlucoseAsString()
            textGlucose.setTextColor(ReceiveData.getGlucoseColor())

            if (ReceiveData.isObsoleteShort() && !ReceiveData.isObsoleteLong()) {
                textGlucose.paintFlags = Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                textGlucose.paintFlags = 0
            }

            textDelta.text = "Δ ${ReceiveData.getDeltaAsString()}"
            if (ReceiveData.isObsoleteShort()) {
                textDelta.setTextColor(ReceiveData.getAlarmTypeColor(AlarmType.OBSOLETE))
            }

            trendIcon.setImageIcon(BitmapUtils.getRateAsIcon(withShadow = true))

            textIob.text = "💉 ${ReceiveData.getIobAsString()}"
            textCob.text = "🍔 ${ReceiveData.getCobAsString()}"
            textIob.visibility = if (ReceiveData.isIobCobObsolete()) View.GONE else View.VISIBLE
            textCob.visibility = textIob.visibility

            notificationView.setDrawingCacheEnabled(true)
            notificationView.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            notificationView.layout(0, 0, notificationView.measuredWidth, notificationView.measuredHeight)

            val bitmap = Bitmap.createBitmap(notificationView.width, notificationView.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            notificationView.draw(canvas)
            return bitmap

        } catch (exc: Exception) {
            Log.e(LOG_ID, "createImage exception: " + exc.message.toString() )
        }
        return null
    }

    fun getNotification(withContent: Boolean, iconKey: String, foreground: Boolean, customLayout: Boolean) : Notification {
        Log.d(LOG_ID, "getNotification withContent=$withContent - foreground=$foreground - customLayout=$customLayout")
        val notificationBuilder = if(foreground) foregroundNotificationCompat else notificationCompat
        val notificationBuild = notificationBuilder
            .setSmallIcon(getStatusBarIcon(iconKey))
            .setContentIntent(getTapActionIntent(foreground))
            .setWhen(ReceiveData.time)
            .setColorized(false)

        if (customLayout) {
            Log.v(LOG_ID, "Use custom layout")
            var remoteViews: RemoteViews? = null
            if (withContent) {
                val bitmap = createNotificationView(GlucoDataService.context!!)
                if (bitmap != null) {
                    remoteViews =
                        RemoteViews(GlucoDataService.context!!.packageName, R.layout.image_view)
                    remoteViews.setImageViewBitmap(R.id.imageLayout, bitmap)
                }
            }
            notificationBuild.setCustomContentView(remoteViews)
            notificationBuild.setCustomBigContentView(remoteViews)
            notificationBuild.setStyle(Notification.DecoratedCustomViewStyle())
        } else {
            Log.v(LOG_ID, "Use default layout")
            if (withContent) {
                notificationBuild.setContentTitle(ReceiveData.getGlucoseAsString() + "   Δ " + ReceiveData.getDeltaAsString())
                if (!ReceiveData.isIobCobObsolete()) {
                    notificationBuild.setContentText("💉 " + ReceiveData.getIobAsString() + if (!ReceiveData.cob.isNaN()) ("  " + "🍔 " + ReceiveData.getCobAsString()) else "")
                } else {
                    notificationBuild.setContentText(null)
                }
                notificationBuild.setLargeIcon(BitmapUtils.getRateAsBitmap(withShadow = true))
            } else {
                notificationBuild.setContentTitle(null)
                notificationBuild.setContentText(null)
            }
            notificationBuild.setStyle(null)
        }
        val notification = notificationBuild.build()

        notification.visibility = Notification.VISIBILITY_PUBLIC
        notification.flags = notification.flags or Notification.FLAG_NO_CLEAR
        return notification
    }

    private fun showNotification(id: Int, withContent: Boolean, iconKey: String, foreground: Boolean, customLayout: Boolean) {
        try {
            Log.v(LOG_ID, "showNotification called for id " + id)
            Channels.getNotificationManager().notify(
                id,
                getNotification(withContent, iconKey, foreground, customLayout)
            )
        } catch (exc: Exception) {
            Log.e(LOG_ID, "showNotification exception: " + exc.toString() )
        }
    }

    fun showNotifications(onlySecond: Boolean = false) {
        Log.d(LOG_ID, "showNotifications service running: ${GlucoDataService.foreground} - onlySecond=$onlySecond")
        if (GlucoDataService.foreground) {
            if (!onlySecond)
                showPrimaryNotification(true)
            if (sharedPref.getBoolean(Constants.SHARED_PREF_SECOND_PERMANENT_NOTIFICATION, false)) {
                Log.d(LOG_ID, "show second notification")
                showNotification(
                    SECOND_NOTIFICATION_ID,
                    false,
                    Constants.SHARED_PREF_SECOND_PERMANENT_NOTIFICATION_ICON,
                    false,
                    false
                )
            } else {
                Channels.getNotificationManager().cancel(SECOND_NOTIFICATION_ID)
            }
        }
    }

    private fun showPrimaryNotification(show: Boolean) {
        Log.d(LOG_ID, "showPrimaryNotification " + show)
        showNotification(
            GlucoDataService.NOTIFICATION_ID,
            !sharedPref.getBoolean(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_EMPTY, false),
            Constants.SHARED_PREF_PERMANENT_NOTIFICATION_ICON,
            true,
            sharedPref.getBoolean(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_CUSTOM_LAYOUT, true)
        )
    }

    private fun hasContent(): Boolean {
        if (!sharedPref.getBoolean(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_EMPTY, false))
            return true

        if (sharedPref.getString(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_ICON, StatusBarIcon.APP.pref) != StatusBarIcon.APP.pref) {
            return true
        }

        if (sharedPref.getBoolean(Constants.SHARED_PREF_SECOND_PERMANENT_NOTIFICATION, false)) {
            if (sharedPref.getString(Constants.SHARED_PREF_SECOND_PERMANENT_NOTIFICATION_ICON, StatusBarIcon.APP.pref) != StatusBarIcon.APP.pref) {
                return true
            }
        }
        return false
    }

    private fun updatePreferences() {
        try {
            //if (sharedPref.getBoolean(Constants.SHARED_PREF_PERMANENT_NOTIFICATION, true)) {
                val content = hasContent()
                Log.i(LOG_ID, "update permanent notifications having content: " + content)
                if (content) {
                    val filter = mutableSetOf(
                        NotifySource.BROADCAST,
                        NotifySource.MESSAGECLIENT,
                        NotifySource.SETTINGS,
                        NotifySource.OBSOLETE_VALUE
                    )   // to trigger re-start for the case of stopped by the system
                    if (!sharedPref.getBoolean(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_EMPTY, false))
                        filter.add(NotifySource.IOB_COB_CHANGE)
                    InternalNotifier.addNotifier(GlucoDataService.context!!, this, filter)
                } else {
                    InternalNotifier.remNotifier(GlucoDataService.context!!, this)
                }
                showNotifications()
            /*}
            else {
                Log.i(LOG_ID, "deactivate permanent notification")
                InternalNotifier.remNotifier(GlucoDataService.context!!, this)
                removeNotifications()
            }*/
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updatePreferences exception: " + exc.toString() )
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        try {
            Log.d(LOG_ID, "onSharedPreferenceChanged called for key " + key)
            when(key) {
                //Constants.SHARED_PREF_PERMANENT_NOTIFICATION,
                Constants.SHARED_PREF_PERMANENT_NOTIFICATION_ICON,
                Constants.SHARED_PREF_SECOND_PERMANENT_NOTIFICATION,
                Constants.SHARED_PREF_SECOND_PERMANENT_NOTIFICATION_ICON,
                Constants.SHARED_PREF_SECOND_PERMANENT_NOTIFICATION_TAP_ACTION,
                Constants.SHARED_PREF_PERMANENT_NOTIFICATION_USE_BIG_ICON,
                Constants.SHARED_PREF_PERMANENT_NOTIFICATION_COLORED_ICON,
                Constants.SHARED_PREF_PERMANENT_NOTIFICATION_TAP_ACTION -> {
                    updatePreferences()
                }
                Constants.SHARED_PREF_PERMANENT_NOTIFICATION_EMPTY -> {
                    if(!sharedPreferences.getBoolean(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_CUSTOM_LAYOUT, true))
                        createNofitication(GlucoDataService.context!!) // reset notification to remove large icon
                    updatePreferences()
                }
                Constants.SHARED_PREF_PERMANENT_NOTIFICATION_CUSTOM_LAYOUT-> {
                    createNofitication(GlucoDataService.context!!) // reset notification to remove large icon
                    updatePreferences()
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString() )
        }
    }

}