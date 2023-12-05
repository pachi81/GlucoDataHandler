package de.michelinside.glucodatahandler.common

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.*
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.android.gms.wearable.*
import de.michelinside.glucodatahandler.common.notifier.*
import de.michelinside.glucodatahandler.common.receiver.*
import de.michelinside.glucodatahandler.common.tasks.SourceTaskService
import de.michelinside.glucodatahandler.common.tasks.TimeTaskService

enum class AppSource {
    NOT_SET,
    PHONE_APP,
    WEAR_APP;
}

abstract class GlucoDataService(source: AppSource) : WearableListenerService(), NotifierInterface {
    private lateinit var receiver: GlucoseDataReceiver
    private lateinit var batteryReceiver: BatteryReceiver
    private lateinit var xDripReceiver: XDripBroadcastReceiver
    private val connection = WearPhoneConnection()

    companion object {
        private val LOG_ID = "GDH.GlucoDataService"
        private var isForegroundService = false
        val foreground get() = isForegroundService
        const val NOTIFICATION_ID = 123
        var appSource = AppSource.NOT_SET
        private var isRunning = false
        val running get() = isRunning
        var service: GlucoDataService? = null
        val context: Context? get() {
            if(service != null)
                return service!!.applicationContext
            return null
        }
        val sharedPref: SharedPreferences? get() {
            if (context != null) {
                return context!!.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            }
            return null
        }

        fun start(source: AppSource, context: Context, cls: Class<*>, force: Boolean = false) {
            if (!running || force) {
                try {
                    appSource = source
                    val serviceIntent = Intent(
                        context,
                        cls
                    )
                    val sharedPref = context.getSharedPreferences(
                        Constants.SHARED_PREF_TAG,
                        Context.MODE_PRIVATE
                    )
                    serviceIntent.putExtra(
                        Constants.SHARED_PREF_FOREGROUND_SERVICE,
                        // on wear foreground is true as default: on phone it is set by notification
                        sharedPref.getBoolean(Constants.SHARED_PREF_FOREGROUND_SERVICE, true)
                    )
                    context.startService(serviceIntent)
                } catch (exc: Exception) {
                    Log.e(
                        LOG_ID,
                        "start exception: " + exc.message.toString()
                    )
                }
            }
        }
    }

    init {
        appSource = source
    }

    abstract fun getNotification() : Notification

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            Log.d(LOG_ID, "onStartCommand called")
            super.onStartCommand(intent, flags, startId)
            val isForeground = intent?.getBooleanExtra(Constants.SHARED_PREF_FOREGROUND_SERVICE, true)
            if (isForeground == true && !isForegroundService && Utils.checkPermission(this, android.Manifest.permission.POST_NOTIFICATIONS, Build.VERSION_CODES.TIRAMISU)) {
                Log.i(LOG_ID, "Starting service in foreground!")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    startForeground(NOTIFICATION_ID, getNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                else
                    startForeground(NOTIFICATION_ID, getNotification())
                isForegroundService = true
            } else if ( isForegroundService && intent?.getBooleanExtra(Constants.ACTION_STOP_FOREGROUND, false) == true ) {
                isForegroundService = false
                Log.i(LOG_ID, "Stopping service in foreground!")
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onStartCommand exception: " + exc.toString())
        }
        return START_STICKY  // keep alive
    }

    override fun onCreate() {
        try {
            super.onCreate()
            Log.i(LOG_ID, "onCreate called")
            service = this
            isRunning = true

            ReceiveData.initData(this)
            SourceTaskService.run(this)

            connection.open(this)

            val filter = mutableSetOf(
                NotifySource.BROADCAST,
                NotifySource.MESSAGECLIENT,
                NotifySource.CAPILITY_INFO,
                NotifySource.BATTERY_LEVEL,
                NotifySource.OBSOLETE_VALUE)   // to trigger re-start for the case of stopped by the system
            if (appSource == AppSource.PHONE_APP) {
                filter.add(NotifySource.SETTINGS)   // only send setting changes from phone to wear!
                filter.add(NotifySource.SOURCE_SETTINGS)
            }
            InternalNotifier.addNotifier(this, filter)

            Log.d(LOG_ID, "Register Receiver")
            receiver = GlucoseDataReceiver()
            registerReceiver(receiver, IntentFilter("glucodata.Minute"))
            batteryReceiver = BatteryReceiver()
            registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            xDripReceiver = XDripBroadcastReceiver()
            registerReceiver(xDripReceiver,IntentFilter("com.eveningoutpost.dexdrip.BgEstimate"))

            if (BuildConfig.DEBUG && sharedPref!!.getBoolean(Constants.SHARED_PREF_DUMMY_VALUES, false)) {
                Thread {
                    try {
                        ReceiveData.time = 0L
                        while (true) {
                            // create Thread which send dummy intents
                            this.sendBroadcast(Utils.getDummyGlucodataIntent(false))
                            Thread.sleep(1000)
                        }
                    } catch (exc: Exception) {
                        Log.e(LOG_ID, "Send dummy glucodata exception: " + exc.toString())
                    }
                }.start()
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreate exception: " + exc.toString())
        }
    }

    override fun onDestroy() {
        try {
            Log.w(LOG_ID, "onDestroy called")
            unregisterReceiver(receiver)
            unregisterReceiver(batteryReceiver)
            unregisterReceiver(xDripReceiver)
            TimeTaskService.stop()
            SourceTaskService.stop()
            connection.close()
            super.onDestroy()
            service = null
            isRunning = false
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onDestroy exception: " + exc.toString())
        }
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

    fun sendToConnectedDevices(dataSource: NotifySource, extras: Bundle) {
        Thread {
            try {
                connection.sendMessage(dataSource, extras, null)
            } catch (exc: Exception) {
                Log.e(LOG_ID, "SendMessage exception: " + exc.toString())
            }
        }.start()
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            Log.d(LOG_ID, "OnNotifyData for source " + dataSource.toString() + " and extras " + extras.toString())
            if (dataSource != NotifySource.MESSAGECLIENT && dataSource != NotifySource.NODE_BATTERY_LEVEL && (dataSource != NotifySource.SETTINGS || extras != null) && (dataSource != NotifySource.CAR_CONNECTION) ) {
                sendToConnectedDevices(dataSource, extras!!)
            }
            if (dataSource == NotifySource.MESSAGECLIENT || dataSource == NotifySource.BROADCAST) {
                if (sharedPref!!.getBoolean(Constants.SHARED_PREF_NOTIFICATION, false) && ReceiveData.forceAlarm) {
                    Log.d(LOG_ID, "Alarm vibration for alarm=" + ReceiveData.alarm.toString())
                    vibrate(ReceiveData.getAlarmType())
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: " + exc.toString())
        }
    }
}
