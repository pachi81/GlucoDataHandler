package de.michelinside.glucodatahandler.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Paint
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import de.michelinside.glucodatahandler.android_auto.CarModeReceiver
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.Command
import de.michelinside.glucodatahandler.common.R as CR
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.WearPhoneConnection
import de.michelinside.glucodatahandler.common.notification.AlarmNotificationBase
import de.michelinside.glucodatahandler.common.notification.AlarmType
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.BitmapUtils
import de.michelinside.glucodatahandler.watch.WatchDrip
import java.io.FileOutputStream

object AlarmNotification : AlarmNotificationBase() {
    private var noAlarmOnWearConnected = false
    private var noAlarmOnAAConnected = false
    private var fullscreenEnabled = true

    override val active: Boolean get() {
        if(getEnabled()) {
            if(noAlarmOnWearConnected && WearPhoneConnection.nodesConnected) {
                Log.d(LOG_ID, "No alarm as wear is connected")
                return false
            }
            if(noAlarmOnAAConnected && CarModeReceiver.AA_connected) {
                Log.d(LOG_ID, "No alarm as Android Auto is connected")
                return false
            }
            return true
        }
        Log.d(LOG_ID, "No alarm as enabled is ${getEnabled()}")
        return false
    }

    override fun adjustNoticiationChannel(context: Context, channel: NotificationChannel) {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_ALARM)
            .build()
        channel.setSound(null, audioAttributes)
        channel.enableVibration(true)
        channel.vibrationPattern = longArrayOf(0)
        channel.enableLights(true)
    }

    override fun onNotificationStopped(noticationId: Int, context: Context?) {
        LockscreenActivity.close()
    }

    override fun canReshowNotification(): Boolean {
        return !LockscreenActivity.isActive()
    }

    override fun buildNotification(
        notificationBuilder: Notification.Builder,
        context: Context,
        alarmType: AlarmType
    ) {
        @Suppress("DEPRECATION")
        notificationBuilder.setLights(ReceiveData.getAlarmTypeColor(alarmType), 300, 100)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            @Suppress("DEPRECATION")
            notificationBuilder.setFlag(Notification.FLAG_SHOW_LIGHTS, true)
        }
        val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
        if (sharedPref.getBoolean(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_CUSTOM_LAYOUT, true)) {
            val resId = getAlarmTextRes(alarmType)
            val contentView = RemoteViews(GlucoDataService.context!!.packageName, R.layout.alarm_notification)
            contentView.setTextViewText(R.id.alarm, context.getString(resId!!))
            contentView.setTextViewText(R.id.snoozeText, context.getString(CR.string.snooze))
            if(alarmType == AlarmType.OBSOLETE) {
                contentView.setTextViewText(R.id.deltaText, "🕒 ${ReceiveData.getElapsedTimeMinuteAsString(context)}")
                contentView.setContentDescription(R.id.deltaText, ReceiveData.getElapsedTimeMinuteAsString(context))
                contentView.setViewVisibility(R.id.glucose, View.GONE)
                contentView.setViewVisibility(R.id.trendImage, View.GONE)
            } else {
                contentView.setViewVisibility(R.id.glucose, View.VISIBLE)
                contentView.setViewVisibility(R.id.trendImage, View.VISIBLE)
                contentView.setTextViewText(R.id.glucose, ReceiveData.getGlucoseAsString())
                contentView.setTextColor(R.id.glucose, ReceiveData.getGlucoseColor())
                contentView.setImageViewBitmap(R.id.trendImage, BitmapUtils.getRateAsBitmap(withShadow = true))
                contentView.setContentDescription(R.id.trendImage, ReceiveData.getRateAsText(context))
                contentView.setTextViewText(R.id.deltaText, "Δ " + ReceiveData.getDeltaAsString())
            }
            if (ReceiveData.isObsoleteShort()) {
                if (!ReceiveData.isObsoleteLong())
                    contentView.setInt(R.id.glucose, "setPaintFlags", Paint.STRIKE_THRU_TEXT_FLAG or Paint.ANTI_ALIAS_FLAG)
                contentView.setTextColor(R.id.deltaText, ReceiveData.getAlarmTypeColor(AlarmType.OBSOLETE) )
            }

            if (getAddSnooze()) {
                val snoozeButtons = getSnoozeValues()

                if(snoozeButtons.size>0) {
                    createSnoozeButton(contentView, context, R.id.snooze_60, snoozeButtons.elementAt(0), getNotificationId(alarmType))
                } else {
                    contentView.setViewVisibility(R.id.snooze_60, View.GONE)
                }
                if(snoozeButtons.size>1) {
                    createSnoozeButton(contentView, context, R.id.snooze_90, snoozeButtons.elementAt(1), getNotificationId(alarmType))
                } else {
                    contentView.setViewVisibility(R.id.snooze_90, View.GONE)
                }
                if(snoozeButtons.size>2) {
                    createSnoozeButton(contentView, context, R.id.snooze_120, snoozeButtons.elementAt(2), getNotificationId(alarmType))
                } else {
                    contentView.setViewVisibility(R.id.snooze_120, View.GONE)
                }

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
            contentView.setViewVisibility(R.id.snoozeText, View.GONE)
            notificationBuilder.setCustomContentView(contentView)
        } else if (getAddSnooze()) {
            var first = true
            getSnoozeValues().forEach {
                if(first) {
                    notificationBuilder.addAction(createSnoozeAction(context, context.getString(CR.string.snooze) + ": $it", it, getNotificationId(alarmType)))
                    first = false
                } else {
                    notificationBuilder.addAction(createSnoozeAction(context, it.toString(), it, getNotificationId(alarmType)))
                }
            }
        }

        if (fullscreenEnabled && hasFullscreenPermission(context)) {
            val fullScreenIntent = Intent(context, LockscreenActivity::class.java)
            fullScreenIntent.flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_NO_USER_ACTION
            fullScreenIntent.putExtra(Constants.ALARM_SNOOZE_EXTRA_NOTIFY_ID, getNotificationId(alarmType))
            fullScreenIntent.putExtra(Constants.ALARM_TYPE_EXTRA, alarmType.ordinal)
            val fullScreenPendingIntent = PendingIntent.getActivity(context, 800, fullScreenIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            notificationBuilder.setFullScreenIntent(fullScreenPendingIntent, true)
        }
    }


    private fun createSnoozeButton(contentView: RemoteViews, context: Context, resId: Int, snooze: Long, noticationId: Int) {
        contentView.setOnClickPendingIntent(resId, createSnoozeIntent(context, snooze, noticationId))
        contentView.setContentDescription(resId, context.resources.getString(CR.string.snooze) + " " + snooze)
        contentView.setTextViewText(resId, snooze.toString())
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

    override fun getStartDelayMs(context: Context): Int {
        val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
        return sharedPref.getInt(Constants.SHARED_PREF_ALARM_START_DELAY, 1000)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        try {
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
            super.onSharedPreferenceChanged(sharedPreferences, key)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString())
        }
    }

    fun saveAlarm(context: Context, alarmType: AlarmType, uri: Uri) {
        try {
            Log.v(LOG_ID, "saveAlarm called for $alarmType to $uri")
            val resId = getAlarmSoundRes(alarmType)
            if (resId != null) {
                Thread {
                    context.contentResolver.openFileDescriptor(uri, "w")?.use {
                        FileOutputStream(it.fileDescriptor).use { outputStream ->
                            val inputStream = context.resources.openRawResource(resId)
                            val buffer = ByteArray(4 * 1024) // or other buffer size
                            var read: Int
                            while (inputStream.read(buffer).also { rb -> read = rb } != -1) {
                                outputStream.write(buffer, 0, read)
                            }
                            Log.v(LOG_ID, "flush")
                            outputStream.flush()
                            outputStream.close()
                        }
                    }
                    val text = context.resources.getText(CR.string.alarm_saved)
                    Handler(GlucoDataService.context!!.mainLooper).post {
                        Toast.makeText(GlucoDataService.context!!, text, Toast.LENGTH_SHORT).show()
                    }
                }.start()
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Saving alarm to file exception: " + exc.message.toString() )
        }
    }

    override fun getNotifierFilter(): MutableSet<NotifySource> {
        return mutableSetOf(NotifySource.CAR_CONNECTION, NotifySource.CAPILITY_INFO)
    }

    override fun executeTest(alarmType: AlarmType, context: Context, fromIntern: Boolean) {
        super.executeTest(alarmType, context, fromIntern)
        WatchDrip.sendTestAlert(context, alarmType)
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            when(dataSource) {
                NotifySource.CAR_CONNECTION,
                NotifySource.CAPILITY_INFO -> {
                    if(WearPhoneConnection.nodesConnected) {
                        // update AA state on wear to prevent alarms, if they should not been executed while AA connected
                        Log.i(LOG_ID, "Car connection has changed: ${CarModeReceiver.AA_connected}")
                        val bundle = Bundle()
                        bundle.putBoolean(
                            Constants.EXTRA_AA_CONNECTED,
                            CarModeReceiver.AA_connected
                        )
                        GlucoDataService.sendCommand(Command.AA_CONNECTION_STATE, bundle)
                    }
                }
                else -> super.OnNotifyData(context, dataSource, extras)

            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: " + exc.toString())
        }
    }

}