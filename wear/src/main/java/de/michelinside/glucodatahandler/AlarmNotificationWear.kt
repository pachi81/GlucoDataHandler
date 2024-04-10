package de.michelinside.glucodatahandler

import android.app.Notification
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.notification.AlarmNotificationBase
import de.michelinside.glucodatahandler.common.notification.AlarmType
import de.michelinside.glucodatahandler.common.notification.ChannelType
import de.michelinside.glucodatahandler.common.notification.Channels
import de.michelinside.glucodatahandler.common.notification.SoundMode
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.R as CR


object AlarmNotificationWear : AlarmNotificationBase() {
    private var forceVibrateInstance = false
    private var vibratorInstance: Vibrator? = null
    private var vibratorManagerInstance: VibratorManager? = null
    private var ringtone: Ringtone? = null

    private var forceVibrate: Boolean get() = forceVibrateInstance
        set(value) {
            if(forceVibrateInstance != value) {
                forceVibrateInstance = value
                Log.i(LOG_ID, "Set force vibration=$forceVibrateInstance")
                updateNotifier()
            }
        }
    override val active: Boolean get() {
        return getEnabled() || forceVibrate
    }

    private val vibratorManager: VibratorManager? get() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && vibratorManagerInstance == null) {
            vibratorManagerInstance = GlucoDataService.context!!.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        }
        return vibratorManagerInstance
    }

    private val vibrator: Vibrator get() {
        if(vibratorInstance == null) {
            vibratorInstance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && vibratorManager != null) {
                vibratorManager!!.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                GlucoDataService.context!!.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
        }
        return vibratorInstance!!
    }

    override fun buildNotification(
        notificationBuilder: Notification.Builder,
        context: Context,
        alarmType: AlarmType
    ) {
        if (getAddSnooze()) {
            notificationBuilder.extend(Notification.WearableExtender()
                .addAction(createAction(context, context.getString(CR.string.snooze) + ": 60", 60L, getNotificationId(alarmType)))
                .addAction(createAction(context, context.getString(CR.string.snooze) + ": 90", 90L, getNotificationId(alarmType)))
                .addAction(createAction(context, context.getString(CR.string.snooze) + ": 120", 120L, getNotificationId(alarmType)))
            )
        }
        notify(alarmType, context)
    }

    private fun notify(alarmType: AlarmType, context: Context) {
        if(!forceDnd() && getRingerMode() >= AudioManager.RINGER_MODE_VIBRATE) {
            vibrate(alarmType, context, false)
            if (getRingerMode() >= AudioManager.RINGER_MODE_NORMAL) {
                val soundUri = getDefaultAlarm(alarmType, context)
                if (soundUri != null) {
                    Log.d(LOG_ID, "Play ringtone $soundUri")
                    ringtone = RingtoneManager.getRingtone(context, soundUri)
                    ringtone!!.play()
                }
            }
        }
    }

    private fun vibrate(alarmType: AlarmType, context: Context, vibrateOnly: Boolean = false): Boolean {
        try {
            val vibratePattern = getVibrationPattern(alarmType) ?: return false
            Log.d(LOG_ID, "vibration for " + alarmType.toString())
            vibrator.cancel()
            Thread {
                Thread.sleep(1500)
                if(curNotification > 0 || vibrateOnly) {
                    Log.d(LOG_ID, "start vibration")
                    vibrator.vibrate(VibrationEffect.createWaveform(vibratePattern, -1))
                }
            }.start()
            return true
        } catch (ex: Exception) {
            Log.e(LOG_ID, "vibrate exception: " + ex)
        }
        return false
    }

    override fun getSoundMode(alarmType: AlarmType): SoundMode {
        if(forceVibrate)
            return SoundMode.VIBRATE
        return SoundMode.NORMAL
    }

    override fun onNotificationStopped(noticationId: Int, context: Context?, reset: Boolean) {
        try {
            Log.v(LOG_ID, "onNotificationStopped called")
            vibrator.cancel()
            if(ringtone!=null) {
                ringtone!!.stop()
                ringtone = null
            }
        } catch (ex: Exception) {
            Log.e(LOG_ID, "onNotificationStopped exception: " + ex)
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        try {
            super.onSharedPreferenceChanged(sharedPreferences, key)
            if (key == null) {
                onSharedPreferenceChanged(sharedPreferences, Constants.SHARED_PREF_NOTIFICATION_VIBRATE)
            } else {
                when(key) {
                    Constants.SHARED_PREF_NOTIFICATION_VIBRATE ->
                        forceVibrate = sharedPreferences.getBoolean(Constants.SHARED_PREF_NOTIFICATION_VIBRATE, forceVibrate)
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString())
        }
    }


    override fun executeTest(alarmType: AlarmType, context: Context) {
        Log.v(LOG_ID, "executeTest called for $alarmType")
        if(!forceVibrate)
            triggerNotification(alarmType, context, true)
        else
            vibrate(alarmType, context, true)
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            super.OnNotifyData(context, dataSource, extras)
            if (!active && forceVibrate && dataSource == NotifySource.ALARM_TRIGGER && ReceiveData.forceAlarm) {
                vibrate(ReceiveData.getAlarmType(), context, true)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: " + exc.toString())
        }
    }

    override fun getChannelType(alarmType: AlarmType): ChannelType? {
        return ChannelType.ALARM
    }

    override fun createNotificationChannel(context: Context, alarmType: AlarmType, byPassDnd: Boolean) {
        Log.v(LOG_ID, "createNotificationChannel called for $alarmType")
        val channelType = getChannelType(alarmType)
        if (channelType != null) {
            val channel = Channels.getNotificationChannel(context, channelType, true)
            channel.group = ALARM_GROUP_ID
            channel.enableVibration(true)
            channel.vibrationPattern = longArrayOf(0, 10)
            Channels.getNotificationManager(context).createNotificationChannel(channel)
        }
    }
}