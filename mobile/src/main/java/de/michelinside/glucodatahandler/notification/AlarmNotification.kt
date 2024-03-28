package de.michelinside.glucodatahandler.notification

import android.app.Notification
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Paint
import android.media.AudioAttributes
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import de.michelinside.glucodatahandler.MainActivity
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.notification.AlarmType
import de.michelinside.glucodatahandler.common.notification.ChannelType
import de.michelinside.glucodatahandler.common.notification.Channels
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.BitmapUtils
import de.michelinside.glucodatahandler.common.utils.Utils
import java.io.FileOutputStream
import de.michelinside.glucodatahandler.common.R as CR


object AlarmNotification: NotifierInterface, SharedPreferences.OnSharedPreferenceChangeListener {
    private const val LOG_ID = "GDH.AlarmNotification"
    private const val ALARM_GROUP_ID = "alarm_group"
    private const val VERY_LOW_NOTIFICATION_ID = 801
    private const val LOW_NOTIFICATION_ID = 802
    private const val HIGH_NOTIFICATION_ID = 803
    private const val VERY_HIGH_NOTIFICATION_ID = 804
    private lateinit var audioManager:AudioManager
    private var enabled: Boolean = false
    private var fullscreenEnabled: Boolean = true
    private var addSnooze: Boolean = false
    private var curNotification = 0
    private var forceSound = false
    private var forceVibration = false
    private var lastRingerMode = -1
    //private var soundLevel = -1
    //private var lastSoundLevel = -1

    fun initNotifications(context: Context) {
        try {
            Log.v(LOG_ID, "initNotifications called")
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            createNotificationChannels(context)
            val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            sharedPref.registerOnSharedPreferenceChangeListener(this)
            onSharedPreferenceChanged(sharedPref, null)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "initNotifications exception: " + exc.toString() )
        }
    }

    fun setEnabled(newEnabled: Boolean) {
        try {
            Log.v(LOG_ID, "setEnabled called: current=$enabled - new=$newEnabled")
            if (enabled != newEnabled) {
                enabled = newEnabled
                if(enabled) {
                    InternalNotifier.addNotifier(GlucoDataService.context!!, this, mutableSetOf(NotifySource.ALARM_TRIGGER) )
                } else {
                    stopNotifications()
                    InternalNotifier.remNotifier(GlucoDataService.context!!, this)
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "setEnabled exception: " + exc.toString() )
        }
    }

    fun hasFullscreenPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            return Channels.getNotificationManager().canUseFullScreenIntent()
        return true
    }

    fun setFullscreenEnabled(fsEnable: Boolean) {
        try {
            Log.v(LOG_ID, "setFullscreenEnabled called: current=$fullscreenEnabled - new=$fsEnable")
            fullscreenEnabled = enabled
        } catch (exc: Exception) {
            Log.e(LOG_ID, "setFullscreenEnabled exception: " + exc.toString() )
        }
    }
    fun setAddSnooze(snooze: Boolean) {
        try {
            Log.v(LOG_ID, "setAddSnooze called: current=$addSnooze - new=$snooze")
            addSnooze = snooze
        } catch (exc: Exception) {
            Log.e(LOG_ID, "setAddSnooze exception: " + exc.toString() )
        }
    }

    fun destroy(context: Context) {
        try {
            Log.v(LOG_ID, "destroy called")
            stopNotifications(context)
            val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            sharedPref.unregisterOnSharedPreferenceChangeListener(this)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "destroy exception: " + exc.toString() )
        }
    }

    fun stopNotifications(context: Context? = null) {
        Log.v(LOG_ID, "stopNotifications called")
        stopNotification(AlarmType.VERY_LOW, context)
        stopNotification(AlarmType.LOW, context)
        stopNotification(AlarmType.HIGH, context)
        stopNotification(AlarmType.VERY_HIGH, context)
        curNotification = 0
    }

    fun stopCurrentNotification(context: Context? = null) {
        if (curNotification > 0) {
            stopNotification(curNotification, context)
            curNotification = 0
        }
    }

    private fun stopNotification(alarmType: AlarmType, context: Context? = null) {
        try {
            Log.v(LOG_ID, "stopNotification called for $alarmType")
            stopNotification(getNotificationId(alarmType))
        } catch (exc: Exception) {
            Log.e(LOG_ID, "stopNotifications exception: " + exc.toString() )
        }
    }
    fun stopNotification(noticationId: Int, context: Context? = null) {
        try {
            Log.v(LOG_ID, "stopNotification called for $noticationId")
            checkRecreateSound()
            if (noticationId > 0) {
                Channels.getNotificationManager(context).cancel(noticationId)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "stopNotifications exception: " + exc.toString() )
        }
    }

    fun triggerNotification(alarmType: AlarmType, context: Context, forTest: Boolean = false) {
        try {
            Log.v(LOG_ID, "triggerNotification called for $alarmType - enabled=$enabled - forTest=$forTest")
            if (enabled || forTest) {
                curNotification = getNotificationId(alarmType)
                checkCreateSound(alarmType)
                Channels.getNotificationManager(context).notify(
                    curNotification,
                    createNotification(context, alarmType)
                )
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "showNotification exception: " + exc.toString() )
        }
    }

    private fun createNotificationChannels(context: Context) {
        Log.v(LOG_ID, "createNotificationChannels called")
        val groupName = context.resources.getString(CR.string.alarm_notification_group_name)
        Channels.getNotificationManager(context).createNotificationChannelGroup(
            NotificationChannelGroup(ALARM_GROUP_ID, groupName))
        createNotificationChannel(context, AlarmType.VERY_LOW, true)
        createNotificationChannel(context, AlarmType.LOW, false)
        createNotificationChannel(context, AlarmType.HIGH, false)
        createNotificationChannel(context, AlarmType.VERY_HIGH, true)
    }

    private fun createNotificationChannel(context: Context, alarmType: AlarmType, byPassDnd: Boolean) {
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

    private fun createSnoozeIntent(context: Context, snoozeTime: Long, noticationId: Int): PendingIntent {
        val intent = Intent(Constants.ALARM_SNOOZE_ACTION)
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        intent.putExtra(Constants.ALARM_SNOOZE_EXTRA_TIME, snoozeTime)
        intent.putExtra(Constants.ALARM_SNOOZE_EXTRA_NOTIFY_ID, noticationId)
        intent.setPackage(context.packageName)
        return PendingIntent.getBroadcast(context, snoozeTime.toInt(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun createStopIntent(context: Context, noticationId: Int): PendingIntent {
        val intent = Intent(Constants.ALARM_STOP_NOTIFICATION_ACTION)
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        intent.putExtra(Constants.ALARM_SNOOZE_EXTRA_NOTIFY_ID, noticationId)
        intent.setPackage(context.packageName)
        return PendingIntent.getBroadcast(context, 888, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
    private fun createAction(context: Context, title: String, snoozeTime: Long, noticationId: Int): Notification.Action {
        return Notification.Action.Builder(null, title, createSnoozeIntent(context, snoozeTime, noticationId)).build()

    }

    private fun createNotification(context: Context, alarmType: AlarmType): Notification? {
        Log.v(LOG_ID, "createNotification called for $alarmType")
        val channelId = getChannelId(alarmType)
        val resId = getAlarmTextRes(alarmType)
        if (channelId.isNullOrEmpty() || resId == null)
            return null

        val contentView = RemoteViews(GlucoDataService.context!!.packageName, R.layout.alarm_notification)
        contentView.setTextViewText(R.id.alarm, context.getString(resId))
        contentView.setTextViewText(R.id.snooze, context.getString(CR.string.snooze))
        contentView.setTextViewText(R.id.glucose, ReceiveData.getClucoseAsString())
        contentView.setTextColor(R.id.glucose, ReceiveData.getClucoseColor())
        contentView.setImageViewBitmap(R.id.trendImage, BitmapUtils.getRateAsBitmap())
        contentView.setTextViewText(R.id.deltaText, "Δ " + ReceiveData.getDeltaAsString())
        contentView.setOnClickPendingIntent(R.id.snooze_60, createSnoozeIntent(context, 60L, getNotificationId(alarmType)))
        contentView.setOnClickPendingIntent(R.id.snooze_90, createSnoozeIntent(context, 90L, getNotificationId(alarmType)))
        contentView.setOnClickPendingIntent(R.id.snooze_120, createSnoozeIntent(context, 120L, getNotificationId(alarmType)))
        if (ReceiveData.isObsolete(Constants.VALUE_OBSOLETE_SHORT_SEC)) {
            if (!ReceiveData.isObsolete())
                contentView.setInt(R.id.glucose, "setPaintFlags", Paint.STRIKE_THRU_TEXT_FLAG or Paint.ANTI_ALIAS_FLAG)
            contentView.setTextColor(R.id.deltaText, Color.GRAY )
        }


        val notificationBuilder = Notification.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(Utils.getAppIntent(context, MainActivity::class.java, 8, false))
            .setDeleteIntent(createStopIntent(context, getNotificationId(alarmType)))
            .setOnlyAlertOnce(false)
            .setAutoCancel(true)
            .setShowWhen(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setWhen(ReceiveData.time)
            .setColorized(false)
            .setGroup("alarm")
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setContentTitle(context.getString(resId))
            .setContentText(ReceiveData.getClucoseAsString()  + " (Δ " + ReceiveData.getDeltaAsString() + ")")

            /*.setLargeIcon(BitmapUtils.getRateAsIcon())
            .addAction(createAction(context, context.getString(CR.string.snooze) + ": 60", 60L, getNotificationId(alarmType)))
            .addAction(createAction(context, "90", 90L, getNotificationId(alarmType)))
            .addAction(createAction(context, "120", 120L, getNotificationId(alarmType)))*/

        if (addSnooze) {
            val bigContentView = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                RemoteViews(contentView)
            } else {
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
        return notificationBuilder.build()
    }

    fun getMaxSoundLevel(): Int {
        return audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)
    }
    private fun checkCreateSound(alarmType: AlarmType) {
        try {
            Log.v(LOG_ID, "checkCreateSound called for force sound=$forceSound - vibration=$forceVibration")
            lastRingerMode = -1
            //lastSoundLevel = -1
            if (forceSound || forceVibration) {
                if (Channels.getNotificationManager().isNotificationPolicyAccessGranted && audioManager.ringerMode < AudioManager.RINGER_MODE_NORMAL) {
                    val channelId = getChannelId(alarmType)
                    val channel = Channels.getNotificationManager().getNotificationChannel(channelId)
                    Log.d(LOG_ID, "Channel: sound=${channel.sound} - vibration=${channel.shouldVibrate()} - prio=${channel.importance}")
                    if(channel.importance >= NotificationManager.IMPORTANCE_DEFAULT) { // notification supports sound
                        var targetRingerMode = AudioManager.RINGER_MODE_SILENT
                        if(!forceSound && forceVibration && audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT) {
                            targetRingerMode = AudioManager.RINGER_MODE_VIBRATE
                        } else if(forceSound) {
                            if (channel.sound != null) {
                                targetRingerMode = AudioManager.RINGER_MODE_NORMAL
                            } else if(channel.shouldVibrate()) {
                                targetRingerMode = AudioManager.RINGER_MODE_VIBRATE
                            }
                        }

                        if (targetRingerMode > audioManager.ringerMode ) {
                            lastRingerMode = audioManager.ringerMode
                            Log.d(LOG_ID, "Set cur ringer mode $lastRingerMode to $targetRingerMode")
                            audioManager.ringerMode = targetRingerMode
                        }
                    }
                }
            }
            /*
            if (soundLevel >= 0) {
                lastSoundLevel = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                val level = minOf(soundLevel, getMaxSoundLevel())
                Log.d(LOG_ID, "Set cur sound level $lastSoundLevel to $level")
                audioManager.setStreamVolume(
                    AudioManager.STREAM_ALARM,
                    level,
                    0
                )
            }*/
        } catch (exc: Exception) {
            Log.e(LOG_ID, "checkCreateSound exception: " + exc.message.toString() )
        }
    }

    private fun checkRecreateSound() {
        try {
            if(lastRingerMode >= 0 ) {
                Log.d(LOG_ID, "Reset ringer mode to $lastRingerMode")
                audioManager.ringerMode = lastRingerMode
                lastRingerMode = -1
            }
            /*
            if(lastSoundLevel >= 0) {
                Log.d(LOG_ID, "Reset sound level to $lastSoundLevel")
                audioManager.setStreamVolume(
                    AudioManager.STREAM_ALARM,
                    lastSoundLevel,
                    0
                )
                lastSoundLevel = -1
            }*/
        } catch (exc: Exception) {
            Log.e(LOG_ID, "checkCreateSound exception: " + exc.message.toString() )
        }
    }

    private fun getNotificationId(alarmType: AlarmType): Int {
        return when(alarmType) {
            AlarmType.VERY_LOW -> VERY_LOW_NOTIFICATION_ID
            AlarmType.LOW -> LOW_NOTIFICATION_ID
            AlarmType.HIGH -> HIGH_NOTIFICATION_ID
            AlarmType.VERY_HIGH -> VERY_HIGH_NOTIFICATION_ID
            else -> -1
        }
    }

    private fun getChannelType(alarmType: AlarmType): ChannelType? {
        return when(alarmType) {
            AlarmType.VERY_LOW -> ChannelType.VERY_LOW_ALARM
            AlarmType.LOW -> ChannelType.LOW_ALARM
            AlarmType.HIGH -> ChannelType.HIGH_ALARM
            AlarmType.VERY_HIGH -> ChannelType.VERY_HIGH_ALARM
            else -> null
        }
    }

    fun getChannelId(alarmType: AlarmType): String? {
        val channel = getChannelType(alarmType)
        if (channel != null)
            return channel.channelId
        return null
    }

    fun getAlarmSoundRes(alarmType: AlarmType): Int? {
        return when(alarmType) {
            AlarmType.VERY_LOW -> CR.raw.gdh_very_low_alarm
            AlarmType.LOW -> CR.raw.gdh_low_alarm
            AlarmType.HIGH -> CR.raw.gdh_high_alarm
            AlarmType.VERY_HIGH -> CR.raw.gdh_very_high_alarm
            else -> null
        }
    }

    fun getAlarmTextRes(alarmType: AlarmType): Int? {
        return when(alarmType) {
            AlarmType.VERY_LOW -> CR.string.very_low_alarm_text
            AlarmType.LOW -> CR.string.very_low_text
            AlarmType.HIGH -> CR.string.very_high_text
            AlarmType.VERY_HIGH -> CR.string.very_high_alarm_text
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

    private fun getVibrationPattern(alarmType: AlarmType): LongArray? {
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

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        try {
            Log.d(LOG_ID, "onSharedPreferenceChanged called for " + key)
            if (key == null) {
                onSharedPreferenceChanged(sharedPreferences, Constants.SHARED_PREF_ALARM_NOTIFICATION_ENABLED)
                onSharedPreferenceChanged(sharedPreferences, Constants.SHARED_PREF_ALARM_SNOOZE_ON_NOTIFICATION)
                onSharedPreferenceChanged(sharedPreferences, Constants.SHARED_PREF_ALARM_FULLSCREEN_NOTIFICATION_ENABLED)
                onSharedPreferenceChanged(sharedPreferences, Constants.SHARED_PREF_ALARM_FORCE_SOUND)
                onSharedPreferenceChanged(sharedPreferences, Constants.SHARED_PREF_ALARM_FORCE_VIBRATION)
                //onSharedPreferenceChanged(sharedPreferences, Constants.SHARED_PREF_ALARM_SOUND_LEVEL)
            } else {
                when(key) {
                    Constants.SHARED_PREF_ALARM_NOTIFICATION_ENABLED -> setEnabled(sharedPreferences.getBoolean(Constants.SHARED_PREF_ALARM_NOTIFICATION_ENABLED, enabled))
                    Constants.SHARED_PREF_ALARM_SNOOZE_ON_NOTIFICATION -> setAddSnooze(sharedPreferences.getBoolean(Constants.SHARED_PREF_ALARM_SNOOZE_ON_NOTIFICATION, addSnooze))
                    Constants.SHARED_PREF_ALARM_FULLSCREEN_NOTIFICATION_ENABLED -> setFullscreenEnabled(sharedPreferences.getBoolean(Constants.SHARED_PREF_ALARM_FULLSCREEN_NOTIFICATION_ENABLED, fullscreenEnabled))
                    Constants.SHARED_PREF_ALARM_FORCE_SOUND -> forceSound = sharedPreferences.getBoolean(Constants.SHARED_PREF_ALARM_FORCE_SOUND, forceSound)
                    Constants.SHARED_PREF_ALARM_FORCE_VIBRATION -> forceVibration = sharedPreferences.getBoolean(Constants.SHARED_PREF_ALARM_FORCE_VIBRATION, forceVibration)
                    //Constants.SHARED_PREF_ALARM_SOUND_LEVEL -> soundLevel = sharedPreferences.getInt(Constants.SHARED_PREF_ALARM_SOUND_LEVEL, soundLevel)
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString())
        }
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            Log.d(LOG_ID, "OnNotifyData called for $dataSource")
            if (dataSource == NotifySource.ALARM_TRIGGER && ReceiveData.forceAlarm) {
                triggerNotification(ReceiveData.getAlarmType(), context)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: " + exc.toString())
        }
    }
}