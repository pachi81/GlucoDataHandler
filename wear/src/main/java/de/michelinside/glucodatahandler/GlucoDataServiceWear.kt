package de.michelinside.glucodatahandler

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import de.michelinside.glucodatahandler.common.*
import de.michelinside.glucodatahandler.common.R as CR
import de.michelinside.glucodatahandler.common.notifier.*
import de.michelinside.glucodatahandler.common.utils.Utils


class GlucoDataServiceWear: GlucoDataService(AppSource.WEAR_APP), NotifierInterface {
    private val LOG_ID = "GDH.GlucoDataServiceWear"
    init {
        Log.d(LOG_ID, "init called")
        InternalNotifier.addNotifier( this,
            ActiveComplicationHandler, mutableSetOf(
                NotifySource.MESSAGECLIENT,
                NotifySource.BROADCAST,
                NotifySource.SETTINGS,
                NotifySource.TIME_VALUE
            )
        )
        InternalNotifier.addNotifier( this,
            BatteryLevelComplicationUpdater,
            mutableSetOf(
                NotifySource.CAPILITY_INFO,
                NotifySource.BATTERY_LEVEL,
                NotifySource.NODE_BATTERY_LEVEL
            )
        )
    }

    companion object {
        fun start(context: Context, force: Boolean = false) {
            start(AppSource.WEAR_APP, context, GlucoDataServiceWear::class.java, force)
        }
    }

    override fun onCreate() {
        Log.d(LOG_ID, "onCreate called")
        super.onCreate()
        val filter = mutableSetOf(
            NotifySource.BROADCAST,
            NotifySource.MESSAGECLIENT,
            NotifySource.OBSOLETE_VALUE) // to trigger re-start for the case of stopped by the system
        InternalNotifier.addNotifier(this, this, filter)
        ActiveComplicationHandler.OnNotifyData(this, NotifySource.CAPILITY_INFO, null)
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            Log.d(LOG_ID, "OnNotifyData called for source " + dataSource.toString())
            start(context)
            if (dataSource == NotifySource.MESSAGECLIENT || dataSource == NotifySource.BROADCAST) {
                if (sharedPref!!.getBoolean(Constants.SHARED_PREF_NOTIFICATION, false) && ReceiveData.forceAlarm) {
                    Log.d(LOG_ID, "Alarm vibration for alarm=" + ReceiveData.alarm.toString())
                    vibrate(ReceiveData.getAlarmType())
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: " + exc.message.toString())
        }
    }

    override fun getNotification(): Notification {
        val channelId = "glucodatahandler_service_01"
        val channel = NotificationChannel(
            channelId,
            "Foregorund GlucoDataService",
            NotificationManager.IMPORTANCE_HIGH
        )
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            channel
        )

        val pendingIntent = Utils.getAppIntent(this, WaerActivity::class.java, 11, false)

        return Notification.Builder(this, channelId)
            .setContentTitle(getString(CR.string.forground_notification_descr))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
    }

    fun getVibrationPattern(alarmType: ReceiveData.AlarmType): LongArray? {
        return when(alarmType) {
            ReceiveData.AlarmType.VERY_LOW -> longArrayOf(0, 1000, 500, 1000, 500, 1000, 500, 1000, 500, 1000, 500, 1000)
            ReceiveData.AlarmType.LOW -> longArrayOf(0, 700, 500, 700, 5000, 700, 500, 700)
            ReceiveData.AlarmType.HIGH -> longArrayOf(0, 500, 500, 500, 500, 500, 500, 500)
            ReceiveData.AlarmType.VERY_HIGH -> longArrayOf(0, 800, 500, 800, 800, 600, 800, 800, 500, 800, 800, 600, 800)
            else -> null
        }
    }

    fun vibrate(alarmType: ReceiveData.AlarmType): Boolean {
        val vibratePattern = getVibrationPattern(alarmType) ?: return false
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        Log.i(LOG_ID, "vibration for " + alarmType.toString())
        vibrator.vibrate(VibrationEffect.createWaveform(vibratePattern, -1))
        return true
    }
}