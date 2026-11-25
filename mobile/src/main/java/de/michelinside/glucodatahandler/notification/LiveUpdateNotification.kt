package de.michelinside.glucodatahandler

import android.app.Notification
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.notification.ChannelType
import de.michelinside.glucodatahandler.common.notification.Channels
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.BitmapUtils
import de.michelinside.glucodatahandler.common.utils.GlucoDataUtils
import de.michelinside.glucodatahandler.common.utils.Log
import de.michelinside.glucodatahandler.common.utils.PackageUtils
import de.michelinside.glucodatahandler.common.R as CR

object LiveUpdateNotification: NotifierInterface, SharedPreferences.OnSharedPreferenceChangeListener {
    private const val LOG_ID = "GDH.LiveUpdate"
    private const val NOTIFICATION_ID = 345
    private lateinit var sharedPref: SharedPreferences

    fun create(context: Context) {
        try {
            sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            sharedPref.registerOnSharedPreferenceChangeListener(this)
            update(context)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "create exception: $exc")
        }
    }

    fun destroy() {
        try {
            if (!::sharedPref.isInitialized)
                return
            InternalNotifier.remNotifier(GlucoDataService.context!!, this)
            sharedPref.unregisterOnSharedPreferenceChangeListener(this)
            Channels.getNotificationManager().cancel(NOTIFICATION_ID)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "destroy exception: $exc")
        }
    }

    private fun update(context: Context) {
        val enabled = sharedPref.getBoolean(Constants.SHARED_PREF_LIVE_UPDATE_ENABLED, false)
        Log.i(LOG_ID, "update - enabled=$enabled sdk=${Build.VERSION.SDK_INT}")
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            Channels.createNotificationChannel(context, ChannelType.MOBILE_LIVE_UPDATE)
            val filter = mutableSetOf(
                NotifySource.BROADCAST,
                NotifySource.MESSAGECLIENT,
                NotifySource.OBSOLETE_VALUE,
                NotifySource.IOB_COB_CHANGE,
                NotifySource.SETTINGS
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN)
                filter.add(NotifySource.TIME_VALUE)  // age metric
            InternalNotifier.addNotifier(context, this, filter)
            show(context)
        } else {
            InternalNotifier.remNotifier(context, this)
            Channels.getNotificationManager(context).cancel(NOTIFICATION_ID)
        }
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        Log.d(LOG_ID, "OnNotifyData called for $dataSource")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA)
            show(context)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (key == Constants.SHARED_PREF_LIVE_UPDATE_ENABLED)
            update(GlucoDataService.context!!)
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun show(context: Context) {
        try {
            val notificationManager = Channels.getNotificationManager(context)
            val notification = buildNotification(context)
            notificationManager.notify(NOTIFICATION_ID, notification)
            Log.i(LOG_ID, "posted - canPostPromoted=${notificationManager.canPostPromotedNotifications()}" +
                    " promotable=${notification.hasPromotableCharacteristics()}")
        } catch (exc: Exception) {
            Log.e(LOG_ID, "show exception: $exc")
        }
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun buildNotification(context: Context): Notification {
        val glucose = ReceiveData.getGlucoseAsString()
        val trend = if (ReceiveData.isObsoleteShort()) "" else GlucoDataUtils.getRateSymbol(ReceiveData.rate).toString()
        val builder = Notification.Builder(context, ChannelType.MOBILE_LIVE_UPDATE.channelId)
            .setSmallIcon(BitmapUtils.getRateAsIcon("live_update_trend", color = Color.WHITE))
            .setContentTitle("$glucose ${ReceiveData.getUnit()} $trend".trim())
            .setContentText(getDetails())
            .setContentIntent(PackageUtils.getTapActionIntent(context, context.packageName, NOTIFICATION_ID))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(ReceiveData.time > 0)
            .setWhen(ReceiveData.time)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setShortCriticalText(glucose)  // chip: trend icon + value, fully shown below 7 chars

        // promotion request API only exists from 36.1 on
        if (Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1)
            builder.setRequestPromotedOngoing(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN)
            builder.setStyle(getMetricStyle(context, glucose, trend))
        return builder.build()
    }

    private fun getDetails(): String {
        var text = "Δ ${ReceiveData.getDeltaAsString()}"
        if (!ReceiveData.isIobCobObsolete()) {
            text += "  💉 ${ReceiveData.getIobAsString()}"
            if (!ReceiveData.cob.isNaN())
                text += "  🍔 ${ReceiveData.getCobAsString()}"
        }
        return text
    }

    @RequiresApi(Build.VERSION_CODES.CINNAMON_BUN)
    private fun getMetricStyle(context: Context, glucose: String, trend: String): Notification.MetricStyle {
        // value column is narrow: keep only the number there, unit and trend go into the label
        val style = Notification.MetricStyle()
            .addMetric(Notification.Metric(
                Notification.Metric.FixedText(glucose),
                "${ReceiveData.getUnit()} $trend".trim()))
            .addMetric(Notification.Metric(
                Notification.Metric.FixedText(ReceiveData.getDeltaAsString()),
                context.getString(CR.string.info_label_delta)))
        if (ReceiveData.time > 0) {
            // fixed text instead of a system stopwatch, which ticks seconds on the AOD
            style.addMetric(Notification.Metric(
                Notification.Metric.FixedText(getAge()),
                context.getString(CR.string.live_update_metric_age)))
        }
        return style
    }

    private fun getAge(): String {
        val minutes = ReceiveData.getElapsedTimeMinute()
        return when {
            minutes < 1 -> "<1m"
            minutes > 60 -> ">1h"
            else -> "${minutes}m"
        }
    }
}
