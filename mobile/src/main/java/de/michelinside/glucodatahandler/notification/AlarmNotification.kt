package de.michelinside.glucodatahandler.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Paint
import android.media.AudioAttributes
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import de.michelinside.glucodatahandler.MainActivity
import de.michelinside.glucodatahandler.android_auto.CarModeReceiver
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.R as CR
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.WearPhoneConnection
import de.michelinside.glucodatahandler.common.notification.AlarmNotificationBase
import de.michelinside.glucodatahandler.common.notification.AlarmType
import de.michelinside.glucodatahandler.common.notification.ChannelType
import de.michelinside.glucodatahandler.common.notification.Channels
import de.michelinside.glucodatahandler.common.utils.BitmapUtils
import de.michelinside.glucodatahandler.common.utils.Utils

object AlarmNotification : AlarmNotificationBase() {
    private var noAlarmOnWearConnected = false
    private var noAlarmOnAAConnected = false
    private var fullscreenEnabled = true

    override val active: Boolean get() {
        if(getEnabled()) {
            if(noAlarmOnWearConnected && WearPhoneConnection.nodesConnected) {
                return false
            }
            if(noAlarmOnAAConnected && CarModeReceiver.AA_connected) {
                return false
            }
            return true
        }
        return false
    }

    override fun onNotificationStopped(noticationId: Int, context: Context?, reset: Boolean) {
        LockscreenActivity.close()
    }

    override fun buildNotification(
        notificationBuilder: Notification.Builder,
        context: Context,
        alarmType: AlarmType
    ) {
        val resId = getAlarmTextRes(alarmType)
        val contentView = RemoteViews(GlucoDataService.context!!.packageName, R.layout.alarm_notification)
        contentView.setTextViewText(R.id.alarm, context.getString(resId!!))
        contentView.setTextViewText(R.id.snooze, context.getString(CR.string.snooze))
        if(alarmType == AlarmType.OBSOLETE) {
            contentView.setTextViewText(R.id.deltaText, "🕒 ${ReceiveData.getElapsedTimeMinuteAsString(context)}")
            contentView.setViewVisibility(R.id.glucose, View.GONE)
            contentView.setViewVisibility(R.id.trendImage, View.GONE)
        } else {
            contentView.setViewVisibility(R.id.glucose, View.VISIBLE)
            contentView.setViewVisibility(R.id.trendImage, View.VISIBLE)
            contentView.setTextViewText(R.id.glucose, ReceiveData.getClucoseAsString())
            contentView.setTextColor(R.id.glucose, ReceiveData.getClucoseColor())
            contentView.setImageViewBitmap(R.id.trendImage, BitmapUtils.getRateAsBitmap())
            contentView.setTextViewText(R.id.deltaText, "Δ " + ReceiveData.getDeltaAsString())
        }
        contentView.setOnClickPendingIntent(R.id.snooze_60, createSnoozeIntent(context, 60L, getNotificationId(alarmType)))
        contentView.setOnClickPendingIntent(R.id.snooze_90, createSnoozeIntent(context, 90L, getNotificationId(alarmType)))
        contentView.setOnClickPendingIntent(R.id.snooze_120, createSnoozeIntent(context, 120L, getNotificationId(alarmType)))
        if (ReceiveData.isObsolete(Constants.VALUE_OBSOLETE_SHORT_SEC)) {
            if (!ReceiveData.isObsolete())
                contentView.setInt(R.id.glucose, "setPaintFlags", Paint.STRIKE_THRU_TEXT_FLAG or Paint.ANTI_ALIAS_FLAG)
            contentView.setTextColor(R.id.deltaText, Color.GRAY )
        }

        notificationBuilder
            .setContentIntent(Utils.getAppIntent(context, MainActivity::class.java, 8, false))


        if (getAddSnooze()) {
            val bigContentView = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                RemoteViews(contentView)
            } else {
                @Suppress("DEPRECATION")
                contentView.clone()
            }
            notificationBuilder.setCustomBigContentView(bigContentView)
        } else {
            notificationBuilder.setCustomBigContentView(null)
        }
        contentView.setViewVisibility(R.id.snoozeLayout, View.GONE)
        notificationBuilder.setCustomContentView(contentView)

        if (fullscreenEnabled && hasFullscreenPermission()) {
            val fullScreenIntent = Intent(context, LockscreenActivity::class.java)
            fullScreenIntent.flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_NO_USER_ACTION
            fullScreenIntent.putExtra(Constants.ALARM_SNOOZE_EXTRA_NOTIFY_ID, getNotificationId(alarmType))
            fullScreenIntent.putExtra(Constants.ALARM_NOTIFICATION_EXTRA_ALARM_TYPE, alarmType.ordinal)
            val fullScreenPendingIntent = PendingIntent.getActivity(context, 800, fullScreenIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            notificationBuilder.setFullScreenIntent(fullScreenPendingIntent, true)
        }
    }

    override fun stopNotificationForRetrigger(): Boolean {
        return LockscreenActivity.isActive()
    }

    fun setFullscreenEnabled(fsEnable: Boolean) {
        try {
            Log.v(LOG_ID, "setFullscreenEnabled called: current=$fullscreenEnabled - new=$fsEnable")
            fullscreenEnabled = fsEnable
        } catch (exc: Exception) {
            Log.e(LOG_ID, "setFullscreenEnabled exception: " + exc.toString() )
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        try {
            super.onSharedPreferenceChanged(sharedPreferences, key)
            if (key == null) {
                onSharedPreferenceChanged(sharedPreferences, Constants.SHARED_PREF_ALARM_FULLSCREEN_NOTIFICATION_ENABLED)
                onSharedPreferenceChanged(sharedPreferences, Constants.SHARED_PREF_NO_ALARM_NOTIFICATION_WEAR_CONNECTED)
                onSharedPreferenceChanged(sharedPreferences, Constants.SHARED_PREF_NO_ALARM_NOTIFICATION_AUTO_CONNECTED)
            } else {
                when(key) {
                    Constants.SHARED_PREF_ALARM_FULLSCREEN_NOTIFICATION_ENABLED -> setFullscreenEnabled(sharedPreferences.getBoolean(Constants.SHARED_PREF_ALARM_FULLSCREEN_NOTIFICATION_ENABLED, fullscreenEnabled))
                    Constants.SHARED_PREF_NO_ALARM_NOTIFICATION_WEAR_CONNECTED -> noAlarmOnWearConnected = sharedPreferences.getBoolean(Constants.SHARED_PREF_NO_ALARM_NOTIFICATION_WEAR_CONNECTED, noAlarmOnWearConnected)
                    Constants.SHARED_PREF_NO_ALARM_NOTIFICATION_AUTO_CONNECTED -> noAlarmOnAAConnected = sharedPreferences.getBoolean(Constants.SHARED_PREF_NO_ALARM_NOTIFICATION_AUTO_CONNECTED, noAlarmOnAAConnected)
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString())
        }
    }

    override fun createNotificationChannel(context: Context, alarmType: AlarmType, byPassDnd: Boolean) {
        Log.v(LOG_ID, "createNotificationChannel called for $alarmType")
        val channelType = getChannelType(alarmType)
        if (channelType != null) {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val channel = Channels.getNotificationChannel(context, channelType, false)
            channel.group = ALARM_GROUP_ID
            channel.setSound(getDefaultAlarm(alarmType, context), audioAttributes)
            channel.enableVibration(true)
            channel.vibrationPattern = getVibrationPattern(alarmType)
            channel.enableLights(true)
            channel.lightColor = ReceiveData.getAlarmTypeColor(alarmType)
            channel.setBypassDnd(byPassDnd)
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            Channels.getNotificationManager(context).createNotificationChannel(channel)
        }
    }

    override fun getChannelType(alarmType: AlarmType): ChannelType? {
        return when(alarmType) {
            AlarmType.VERY_LOW -> ChannelType.VERY_LOW_ALARM
            AlarmType.LOW -> ChannelType.LOW_ALARM
            AlarmType.HIGH -> ChannelType.HIGH_ALARM
            AlarmType.VERY_HIGH -> ChannelType.VERY_HIGH_ALARM
            AlarmType.OBSOLETE -> ChannelType.OBSOLETE_ALARM
            else -> null
        }
    }

}