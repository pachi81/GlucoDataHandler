package de.michelinside.glucodatahandler

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Icon
import android.os.Bundle
import android.util.TypedValue
import de.michelinside.glucodatahandler.common.utils.Log
import android.view.View
import android.widget.RemoteViews
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.chart.ChartBitmap
import de.michelinside.glucodatahandler.common.notification.AlarmType
import de.michelinside.glucodatahandler.common.utils.BitmapUtils
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.notification.ChannelType
import de.michelinside.glucodatahandler.common.notification.Channels
import de.michelinside.glucodatahandler.common.utils.PackageUtils
import de.michelinside.glucodatahandler.common.utils.Utils
import de.michelinside.glucodatahandler.common.R as CR


object PermanentNotification: NotifierInterface, SharedPreferences.OnSharedPreferenceChangeListener {
    private const val LOG_ID = "GDH.PermanentNotification"
    private const val SECOND_NOTIFICATION_ID = 124
    private const val THIRD_NOTIFICATION_ID = 234
    private lateinit var secondNotificationCompat: Notification.Builder
    private lateinit var thirdNotificationCompat: Notification.Builder
    private lateinit var foregroundNotificationCompat: Notification.Builder
    private lateinit var sharedPref: SharedPreferences
    @SuppressLint("StaticFieldLeak")
    private var chartBitmap: ChartBitmap? = null

    enum class StatusBarIcon(val pref: String) {
        APP("app"),
        GLUCOSE("glucose"),
        TREND("trend"),
        DELTA("delta")
    }

    fun create(context: Context) {
        try {
            Log.v(LOG_ID, "create called")
            createNotification(context)
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
            removeBitmap()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "destroy exception: " + exc.toString() )
        }
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            if(Log.isLoggable(LOG_ID, android.util.Log.DEBUG))
                Log.d(LOG_ID, "OnNotifyData called for source $dataSource with extras ${Utils.dumpBundle(extras)} - graph-id ${chartBitmap?.chartId}")
            if (dataSource == NotifySource.GRAPH_CHANGED && chartBitmap != null && extras?.getInt(Constants.GRAPH_ID) != chartBitmap!!.chartId) {
                Log.v(LOG_ID, "Ignore graph changed as it is not for this chart")
                return  // ignore as it is not for this graph
            }
            if(dataSource == NotifySource.TIME_VALUE && chartBitmap != null && ReceiveData.getElapsedTimeMinute().mod(2) == 0) {
                Log.d(LOG_ID, "Ignore time value and wait for chart update")
                return
            }
            showNotifications()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: " + exc.toString() )
        }
    }

    private fun createNotificationChannel(context: Context) {
        Channels.createNotificationChannel(context, ChannelType.MOBILE_FOREGROUND)
        Channels.createNotificationChannel(context, ChannelType.MOBILE_SECOND)
        Channels.createNotificationChannel(context, ChannelType.MOBILE_THIRD)
    }

    private fun createNotification(context: Context) {
        Log.d(LOG_ID, "createNotification called")
        createNotificationChannel(context)

        Channels.getNotificationManager().cancel(GlucoDataService.NOTIFICATION_ID)
        Channels.getNotificationManager().cancel(SECOND_NOTIFICATION_ID)
        Channels.getNotificationManager().cancel(THIRD_NOTIFICATION_ID)

        secondNotificationCompat = Notification.Builder(context, ChannelType.MOBILE_SECOND.channelId)
            .setSmallIcon(CR.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setShowWhen(true)
            .setColorized(true)
            .setGroup(ChannelType.MOBILE_SECOND.channelId)
            .setGroupSummary(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)

        thirdNotificationCompat = Notification.Builder(context, ChannelType.MOBILE_THIRD.channelId)
            .setSmallIcon(CR.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setShowWhen(true)
            .setColorized(true)
            .setGroup(ChannelType.MOBILE_THIRD.channelId)
            .setGroupSummary(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)

        foregroundNotificationCompat = Notification.Builder(context, ChannelType.MOBILE_FOREGROUND.channelId)
            .setSmallIcon(CR.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setShowWhen(true)
            .setColorized(true)
            .setGroup(ChannelType.MOBILE_FOREGROUND.channelId)
            .setGroupSummary(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
    }

    private fun removeNotifications(onlySecond: Boolean = false) {
        Log.i(LOG_ID, "Remove notifications - onlySecond=$onlySecond")
        if(!onlySecond)
            showPrimaryNotification(false)
        Channels.getNotificationManager().cancel(SECOND_NOTIFICATION_ID)
        Channels.getNotificationManager().cancel(THIRD_NOTIFICATION_ID)
    }



    private fun getDefaultIcon(iconKey: String): StatusBarIcon {
        return when(iconKey) {
            Constants.SHARED_PREF_PERMANENT_NOTIFICATION_ICON -> StatusBarIcon.GLUCOSE
            Constants.SHARED_PREF_SECOND_PERMANENT_NOTIFICATION_ICON -> StatusBarIcon.TREND
            Constants.SHARED_PREF_THIRD_PERMANENT_NOTIFICATION_ICON -> StatusBarIcon.DELTA
            else -> StatusBarIcon.APP
        }
    }

    private fun getStatusBarIcon(iconKey: String): Icon {
        val bigIcon = sharedPref.getBoolean(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_USE_BIG_ICON, false)
        val coloredIcon = sharedPref.getBoolean(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_COLORED_ICON, true)
        return when(sharedPref.getString(iconKey, getDefaultIcon(iconKey).pref)) {
            StatusBarIcon.GLUCOSE.pref -> BitmapUtils.getGlucoseAsIcon(
                iconKey,
                roundTarget=!bigIcon,
                color = if(coloredIcon) ReceiveData.getGlucoseColor() else Color.WHITE,
                withShadow = coloredIcon,
                useTallFont = true
            )
            StatusBarIcon.TREND.pref -> BitmapUtils.getRateAsIcon(
                iconKey,
                color = if(coloredIcon) ReceiveData.getGlucoseColor() else Color.WHITE,
                resizeFactor = if (bigIcon) 1.5F else 1F,
                withShadow = coloredIcon
            )
            StatusBarIcon.DELTA.pref -> BitmapUtils.getDeltaAsIcon(
                iconKey,
                roundTarget=!bigIcon,
                color = if(coloredIcon) ReceiveData.getGlucoseColor(true) else Color.WHITE,
                useTallFont = true)
            else -> Icon.createWithResource(GlucoDataService.context, CR.mipmap.ic_launcher)
        }
    }

    private fun getNotificationBuilder(channel: ChannelType): Notification.Builder? {
        when(channel) {
            ChannelType.MOBILE_FOREGROUND -> return foregroundNotificationCompat
            ChannelType.MOBILE_SECOND -> return secondNotificationCompat
            ChannelType.MOBILE_THIRD -> return thirdNotificationCompat
            else -> return null
        }
    }

    private fun getTapActionIntent(channel: ChannelType): PendingIntent? {
        val tapAction = when(channel) {
            ChannelType.MOBILE_FOREGROUND -> sharedPref.getString(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_TAP_ACTION, GlucoDataService.context!!.packageName)
            ChannelType.MOBILE_SECOND -> sharedPref.getString(Constants.SHARED_PREF_SECOND_PERMANENT_NOTIFICATION_TAP_ACTION, GlucoDataService.context!!.packageName)
            ChannelType.MOBILE_THIRD -> sharedPref.getString(Constants.SHARED_PREF_THIRD_PERMANENT_NOTIFICATION_TAP_ACTION, GlucoDataService.context!!.packageName)
            else -> null
        }
        if(tapAction.isNullOrEmpty())
            return null
        val requestCode = channel.ordinal
        return PackageUtils.getTapActionIntent(GlucoDataService.context!!, tapAction, requestCode)
    }

    private fun createNotificationRemoteView(context: Context, withGraph: Boolean): RemoteViews {
        val remoteViews = RemoteViews(GlucoDataService.context!!.packageName, R.layout.notification_layout)
        if(GlucoDataService.patientName.isNullOrEmpty()) {
            remoteViews.setViewVisibility(R.id.patient_name, View.GONE)
            remoteViews.setViewLayoutWidth(R.id.glucose, if(ReceiveData.getGlucoseAsString().length <= 2) 50F else 55F, TypedValue.COMPLEX_UNIT_DIP)
        } else {
            remoteViews.setViewVisibility(R.id.patient_name, View.VISIBLE)
            remoteViews.setTextViewText(R.id.patient_name, GlucoDataService.patientName)
            remoteViews.setViewLayoutWidth(R.id.glucose, 40F, TypedValue.COMPLEX_UNIT_DIP)
        }
        remoteViews.setTextViewText(R.id.glucose, ReceiveData.getGlucoseAsString())
        remoteViews.setTextColor(R.id.glucose, ReceiveData.getGlucoseColor())
        remoteViews.setImageViewIcon(R.id.trendImage, BitmapUtils.getRateAsIcon("notification_trend_$withGraph", withShadow = true))
        remoteViews.setContentDescription(R.id.trendImage, ReceiveData.getRateAsText(context))
        remoteViews.setTextViewText(R.id.deltaText, "Δ ${ReceiveData.getDeltaAsString()}")
        if (ReceiveData.isObsoleteShort()) {
            if (!ReceiveData.isObsoleteLong())
                remoteViews.setInt(R.id.glucose, "setPaintFlags", Paint.STRIKE_THRU_TEXT_FLAG or Paint.ANTI_ALIAS_FLAG)
            remoteViews.setTextColor(R.id.deltaText, ReceiveData.getAlarmTypeColor(AlarmType.OBSOLETE) )
        }
        if(ReceiveData.isIobCobObsolete()) {
            remoteViews.setViewVisibility(R.id.iobText, View.GONE)
            remoteViews.setViewVisibility(R.id.cobText, View.GONE)
        } else {
            remoteViews.setTextViewText(R.id.iobText, "💉 ${ReceiveData.getIobAsString()}")
            remoteViews.setTextViewText(R.id.cobText, "🍔 ${ReceiveData.getCobAsString()}")
            remoteViews.setViewVisibility(R.id.iobText, View.VISIBLE)
            if (ReceiveData.cob.isNaN())
                remoteViews.setViewVisibility(R.id.cobText, View.GONE)
            else
                remoteViews.setViewVisibility(R.id.cobText, View.VISIBLE)
        }

        if(withGraph) {
            val chart = getChartBitmap()
            if(chart!=null) {
                remoteViews.setViewVisibility(R.id.graphImageLayout, View.VISIBLE)
                remoteViews.setImageViewBitmap(R.id.graphImage, chart)
            }
        }
        return remoteViews
    }

    fun getNotification(withContent: Boolean, iconKey: String, channel: ChannelType, customLayout: Boolean) : Notification {
        Log.d(LOG_ID, "getNotification withContent=$withContent - channel=${channel} - customLayout=$customLayout")
        val notificationBuilder = getNotificationBuilder(channel)
        val notificationBuild = notificationBuilder!!
            .setSmallIcon(getStatusBarIcon(iconKey))
            .setContentIntent(getTapActionIntent(channel))
            .setWhen(ReceiveData.time)
            .setColorized(false)

        if (customLayout) {
            Log.v(LOG_ID, "Use custom layout")
            var remoteView: RemoteViews? = null
            var remoteBigView: RemoteViews? = null
            if (withContent) {
                remoteView = createNotificationRemoteView(GlucoDataService.context!!, false)
                remoteBigView = if(hasChart())
                    createNotificationRemoteView(GlucoDataService.context!!, true)
                else
                    remoteView
            }
            notificationBuild.setCustomContentView(remoteView)
            notificationBuild.setCustomBigContentView(remoteBigView)
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
                notificationBuild.setLargeIcon(BitmapUtils.getRateAsIcon("notificationBuild", withShadow = true))
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

    private fun showNotification(id: Int, withContent: Boolean, iconKey: String, channel: ChannelType, customLayout: Boolean) {
        try {
            Log.v(LOG_ID, "showNotification called for id " + id)
            Channels.getNotificationManager().notify(
                id,
                getNotification(withContent, iconKey, channel, customLayout)
            )
        } catch (exc: Exception) {
            Log.e(LOG_ID, "showNotification exception: " + exc.toString() )
        }
    }

    fun showNotifications(onlySecond: Boolean = false) {
        Log.d(LOG_ID, "showNotifications service running: ${GlucoDataService.foreground} - onlySecond=$onlySecond")
        try {
            if(sharedPref.getBoolean("notification_recreate", false))
                removeNotifications(true)
            if (GlucoDataService.foreground) {
                if (!onlySecond)
                    showPrimaryNotification(true)
                if (sharedPref.getBoolean(Constants.SHARED_PREF_SECOND_PERMANENT_NOTIFICATION, false)) {
                    Log.d(LOG_ID, "show second notification")
                    showNotification(
                        SECOND_NOTIFICATION_ID,
                        false,
                        Constants.SHARED_PREF_SECOND_PERMANENT_NOTIFICATION_ICON,
                        ChannelType.MOBILE_SECOND,
                        false
                    )
                } else {
                    Channels.getNotificationManager().cancel(SECOND_NOTIFICATION_ID)
                }
                if (sharedPref.getBoolean(Constants.SHARED_PREF_THIRD_PERMANENT_NOTIFICATION, false)) {
                    Log.d(LOG_ID, "show third notification")
                    showNotification(
                        THIRD_NOTIFICATION_ID,
                        false,
                        Constants.SHARED_PREF_THIRD_PERMANENT_NOTIFICATION_ICON,
                        ChannelType.MOBILE_THIRD,
                        false
                    )
                } else {
                    Channels.getNotificationManager().cancel(THIRD_NOTIFICATION_ID)
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "showNotifications exception: " + exc.toString() )
        }
    }

    private fun showPrimaryNotification(show: Boolean) {
        Log.d(LOG_ID, "showPrimaryNotification " + show)
        showNotification(
            GlucoDataService.NOTIFICATION_ID,
            !sharedPref.getBoolean(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_EMPTY, false),
            Constants.SHARED_PREF_PERMANENT_NOTIFICATION_ICON,
            ChannelType.MOBILE_FOREGROUND,
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

        if (sharedPref.getBoolean(Constants.SHARED_PREF_THIRD_PERMANENT_NOTIFICATION, false)) {
            if (sharedPref.getString(Constants.SHARED_PREF_THIRD_PERMANENT_NOTIFICATION_ICON, StatusBarIcon.APP.pref) != StatusBarIcon.APP.pref) {
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
                        NotifySource.SETTINGS,
                        NotifySource.OBSOLETE_VALUE
                    )   // to trigger re-start for the case of stopped by the system
                    if(sharedPref.getBoolean(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_SHOW_GRAPH, false)) {
                        filter.add(NotifySource.GRAPH_CHANGED)
                        createBitmap()
                    } else {
                        filter.add(NotifySource.BROADCAST)
                        filter.add(NotifySource.MESSAGECLIENT)
                        removeBitmap()
                    }

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
                Constants.SHARED_PREF_THIRD_PERMANENT_NOTIFICATION,
                Constants.SHARED_PREF_THIRD_PERMANENT_NOTIFICATION_ICON,
                Constants.SHARED_PREF_THIRD_PERMANENT_NOTIFICATION_TAP_ACTION,
                Constants.SHARED_PREF_PERMANENT_NOTIFICATION_USE_BIG_ICON,
                Constants.SHARED_PREF_PERMANENT_NOTIFICATION_COLORED_ICON,
                Constants.SHARED_PREF_PERMANENT_NOTIFICATION_TAP_ACTION,
                Constants.SHARED_PREF_PERMANENT_NOTIFICATION_SHOW_GRAPH -> {
                    updatePreferences()
                }
                Constants.SHARED_PREF_PERMANENT_NOTIFICATION_EMPTY -> {
                    if(!sharedPreferences.getBoolean(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_CUSTOM_LAYOUT, true))
                        createNotification(GlucoDataService.context!!) // reset notification to remove large icon
                    updatePreferences()
                }
                Constants.SHARED_PREF_PERMANENT_NOTIFICATION_CUSTOM_LAYOUT-> {
                    createNotification(GlucoDataService.context!!) // reset notification to remove large icon
                    updatePreferences()
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString() )
        }
    }

    private fun hasChart(): Boolean {
        return chartBitmap != null
    }

    private fun getChartBitmap(): Bitmap? {
        return chartBitmap?.getBitmap()
    }

    private fun createBitmap() {
        if(chartBitmap == null && GlucoDataService.isServiceRunning) {
            Log.i(LOG_ID, "Create bitmap")
            chartBitmap = ChartBitmap(GlucoDataService.context!!, 1000)
        }
    }

    fun recreateBitmap(): Boolean {
        if(chartBitmap != null) {
            Log.i(LOG_ID, "Recreate bitmap")
            chartBitmap!!.recreate()
            return true
        }
        return false
    }

    private fun removeBitmap() {
        if(chartBitmap != null) {
            Log.i(LOG_ID, "Remove bitmap")
            chartBitmap!!.close()
            chartBitmap = null
        }
    }
}