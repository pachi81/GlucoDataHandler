package de.michelinside.glucodatahandler.common.notification

import android.app.Notification
import android.app.NotificationChannelGroup
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.Handler
import android.util.Log
import android.widget.Toast
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.R
import de.michelinside.glucodatahandler.common.ReceiveData
import java.io.FileOutputStream

object AlarmNotification {
    private const val LOG_ID = "GDH.AlarmNotification"
    private const val ALARM_GROUP_ID = "alarm_group"
    private const val VERY_LOW_NOTIFICATION_ID = 801
    fun initNotifications(context: Context) {
        createNotificationChannels(context)
    }

    fun stopNotifications(alarmType: AlarmType, context: Context? = null) {
        val notify_id = getNotificationId(alarmType)
        if (notify_id > 0)
            Channels.getNotificationManager(context).cancel(notify_id)
    }

    fun triggerNotification(alarmType: AlarmType, context: Context) {
        try {
            Log.v(LOG_ID, "showNotification called for $alarmType")
            Channels.getNotificationManager(context).notify(
                getNotificationId(alarmType),
                createNotification(context, alarmType)
            )
        } catch (exc: Exception) {
            Log.e(LOG_ID, "showNotification exception: " + exc.toString() )
        }
    }

    private fun createNotificationChannels(context: Context) {
        val groupName = context.resources.getString(R.string.alarm_notification_group_name)
        Channels.getNotificationManager(context).createNotificationChannelGroup(
            NotificationChannelGroup(ALARM_GROUP_ID, groupName))
        createNotificationChannel(context, AlarmType.VERY_LOW, true)
    }

    private fun createNotificationChannel(context: Context, alarmType: AlarmType, byPassDnd: Boolean) {
        val channelType = getChannelType(alarmType)
        if (channelType != null) {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val channel = Channels.getNotificationChannel(context, channelType, false)
            channel.group = ALARM_GROUP_ID
            channel.setSound(getDefaultAlarm(alarmType, context), audioAttributes)
            channel.vibrationPattern = getVibrationPattern(alarmType)
            channel.setBypassDnd(byPassDnd)
            Channels.getNotificationManager(context).createNotificationChannel(channel)
        }
    }

    private fun createNotification(context: Context, alarmType: AlarmType): Notification? {
        val channelId = getChannelId(alarmType)
        if (channelId.isNullOrEmpty())
            return null
        val notification = Notification.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOnlyAlertOnce(false)
            .setAutoCancel(true)
            .setShowWhen(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setWhen(ReceiveData.time)
            .setContentTitle(context.getString(R.string.info_label_alarm) + ": " + context.getString(
                alarmType.resId))
            .setContentText(ReceiveData.getClucoseAsString() + " (Δ " + ReceiveData.getDeltaAsString() + ")")
            .build()

        return notification
    }

    private fun getNotificationId(alarmType: AlarmType): Int {
        return when(alarmType) {
            AlarmType.VERY_LOW -> VERY_LOW_NOTIFICATION_ID
            else -> -1
        }
    }

    private fun getChannelType(alarmType: AlarmType): ChannelType? {
        return when(alarmType) {
            AlarmType.VERY_LOW -> ChannelType.VERY_LOW_ALARM
            else -> null
        }
    }

    fun getChannelId(alarmType: AlarmType): String? {
        return when(alarmType) {
            AlarmType.VERY_LOW -> ChannelType.VERY_LOW_ALARM.channelId
            else -> null
        }
    }

    fun getAlarmSoundRes(alarmType: AlarmType): Int? {
        return when(alarmType) {
            AlarmType.VERY_LOW -> R.raw.gdh_alarm_very_low
            else -> null
        }
    }

    private fun getDefaultAlarm(alarmType: AlarmType, context: Context): Uri? {
        val res = getAlarmSoundRes(alarmType)
        if (res != null) {
            return getUri(res, context)
        }
        return null
    }

    private fun getUri(resId: Int, context: Context): Uri {
        val uri = "android.resource://" + context.packageName + "/" + resId
        return Uri.parse(uri)
    }

    fun getVibrationPattern(alarmType: AlarmType): LongArray? {
        return when(alarmType) {
            AlarmType.VERY_LOW -> longArrayOf(0, 1000, 500, 1000, 500, 1000, 500, 1000, 500, 1000, 500, 1000)
            AlarmType.LOW -> longArrayOf(0, 700, 500, 700, 500, 700, 500, 700)
            AlarmType.HIGH -> longArrayOf(0, 500, 500, 500, 500, 500, 500, 500)
            AlarmType.VERY_HIGH -> longArrayOf(0, 800, 500, 800, 800, 600, 800, 800, 500, 800, 800, 600, 800)
            else -> null
        }
    }

    fun saveAlarm(context: Context, alarmType: AlarmType, uri: Uri) {
        try {
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
                    val text = context.resources.getText(R.string.alarm_saved)
                    Handler(GlucoDataService.context!!.mainLooper).post {
                        Toast.makeText(GlucoDataService.context!!, text, Toast.LENGTH_SHORT).show()
                    }
                }.start()
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Saving alarm to file exception: " + exc.message.toString() )
        }
    }
}