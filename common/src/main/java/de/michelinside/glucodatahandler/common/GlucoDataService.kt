package de.michelinside.glucodatahandler.common

import android.annotation.SuppressLint
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import com.google.android.gms.wearable.WearableListenerService
import de.michelinside.glucodatahandler.common.database.dbAccess
import de.michelinside.glucodatahandler.common.notification.ChannelType
import de.michelinside.glucodatahandler.common.notification.Channels
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.receiver.BatteryReceiver
import de.michelinside.glucodatahandler.common.receiver.BroadcastServiceAPI
import de.michelinside.glucodatahandler.common.receiver.ScreenEventReceiver
import de.michelinside.glucodatahandler.common.tasks.BackgroundWorker
import de.michelinside.glucodatahandler.common.tasks.SourceTaskService
import de.michelinside.glucodatahandler.common.tasks.TimeTaskService
import de.michelinside.glucodatahandler.common.utils.GlucoDataUtils
import de.michelinside.glucodatahandler.common.utils.GlucoseStatistics
import de.michelinside.glucodatahandler.common.utils.Log
import de.michelinside.glucodatahandler.common.utils.PackageUtils
import de.michelinside.glucodatahandler.common.utils.TextToSpeechUtils
import de.michelinside.glucodatahandler.common.utils.Utils
import de.michelinside.glucodatahandler.common.service.ReceiverManager
import de.michelinside.glucodatahandler.common.service.StartupTrigger
import de.michelinside.glucodatahandler.common.service.WearPhoneManager
import androidx.core.content.edit


enum class AppSource {
    NOT_SET,
    PHONE_APP,
    WEAR_APP,
    AUTO_APP;
}

abstract class GlucoDataService(source: AppSource) : WearableListenerService(), SharedPreferences.OnSharedPreferenceChangeListener {
    protected var batteryReceiver: BatteryReceiver? = null
    protected var screenEventReceiver: ScreenEventReceiver? = null
    private lateinit var broadcastServiceAPI: BroadcastServiceAPI

    @SuppressLint("StaticFieldLeak")
    companion object {
        private val LOG_ID = "GDH.GlucoDataService"
        private var isForegroundService = false
        var startServiceReceiver: Class<*>? = null
        val foreground get() = isForegroundService
        const val NOTIFICATION_ID = 1234
        var appSource = AppSource.NOT_SET
        private var isRunning = false
        val running get() = isRunning
        private var created = false
        var patientName: String? = null

        @SuppressLint("StaticFieldLeak")
        var service: GlucoDataService? = null

        var context: Context? get() {
            if(service != null)
                return service!!.applicationContext
            return extContext
        } set(value) {
            extContext = value
        }

        val isServiceRunning: Boolean get() {
            return service != null || extContext != null
        }

        @SuppressLint("StaticFieldLeak")
        private var extContext: Context? = null
        val sharedPref: SharedPreferences? get() {
            if (context != null) {
                return context!!.getSharedPreferences(Constants.SHARED_PREF_TAG, MODE_PRIVATE)
            }
            return null
        }

        // used for login stuff (tokens, user-id, ...) or runtime values, which should not exported
        val sharedExtraPref: SharedPreferences? get() {
            if (context != null) {
                return context!!.getSharedPreferences(Constants.SHARED_PREF_EXTRAS_TAG, MODE_PRIVATE)
            }
            return null
        }

        fun start(source: AppSource, context: Context, cls: Class<*>) {
            Log.v(LOG_ID, "start called (running: $running - foreground: $foreground)")
            if (!running || !foreground) {
                try {
                    appSource = source
                    SettingsMigrator.migrateSettings(context)
                    val serviceIntent = Intent(
                        context,
                        cls
                    )
                    /*
                    val sharedPref = context.getSharedPreferences(
                        Constants.SHARED_PREF_TAG,
                        Context.MODE_PRIVATE
                    )
                    serviceIntent.putExtra(
                        Constants.SHARED_PREF_FOREGROUND_SERVICE,
                        // default on wear and phone
                        true//sharedPref.getBoolean(Constants.SHARED_PREF_FOREGROUND_SERVICE, true)
                    )*/
                    //if (foreground) {
                    context.startService(serviceIntent)
                    /*} else {
                        Log.i(LOG_ID, "start foreground service")
                        context.applicationContext.startForegroundService(serviceIntent)
                        stopTrigger()
                    }*/
                    isRunning = true
                    if (!foreground && startServiceReceiver != null) {
                        // trigger also foreground alarm
                        StartupTrigger.triggerStartService(context, startServiceReceiver!!)
                    }
                } catch (exc: Exception) {
                    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && exc is ForegroundServiceStartNotAllowedException) {
                        Log.e(LOG_ID,"start foreground exception: " + exc.message.toString())
                        // try to start service for the case that the alarm can not start it...
                        if(!isRunning) {
                            val serviceIntent = Intent(
                                context,
                                cls
                            )
                            context.startService(serviceIntent)
                        }
                        if(startServiceReceiver != null) {
                            StartupTrigger.triggerStartService(context, startServiceReceiver!!)
                        }
                    } else {
                        Log.e(LOG_ID,"start exception: " + exc.message.toString() + "\n" + exc.stackTraceToString())
                        isRunning = false
                    }
                }
            }
        }

        fun checkServices(context: Context) {
            try {
                if(created)
                    BackgroundWorker.checkServiceRunning(context)
            } catch (exc: Exception) {
                Log.e(LOG_ID, "checkServices exception: " + exc.message.toString())
            }
        }

        fun resetDB() {
            try {
                Log.w(LOG_ID, "reset database called!")
                dbAccess.deleteAllValues()
                SourceStateData.reset()
                GlucoseStatistics.reset()
                ReceiveData.reset(context!!)
                if (appSource == AppSource.PHONE_APP)
                    WearPhoneManager.sendCommand(Command.CLEAN_UP_DB)
            } catch (exc: Exception) {
                Log.e(LOG_ID, "resetDB exception: " + exc.message.toString())
            }
        }

        fun getSettings(): Bundle? {
            return service?.getSettings()
        }

        fun setSettings(context: Context, bundle: Bundle) {
            service?.setSettings(context, bundle)
        }
    }

    init {
        appSource = source
        Log.i(LOG_ID, "Init for $appSource")
    }

    abstract fun getNotification() : Notification

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            Log.i(LOG_ID, "onStartCommand called foregroundService: $isForegroundService")
            GdhUncaughtExecptionHandler.init()
            if (!isForegroundService) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    Log.i(LOG_ID, "Starting service in foreground with type ${ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE}!")
                    startForeground(
                        NOTIFICATION_ID,
                        getNotification(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else {
                    Log.i(LOG_ID, "Starting service in foreground!")
                    startForeground(NOTIFICATION_ID, getNotification())
                }
                isForegroundService = true
                Log.i(LOG_ID, "Service in foreground started!")
                StartupTrigger.stopTrigger()
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onStartCommand exception: " + exc.toString())
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && exc is ForegroundServiceStartNotAllowedException) {
                if(startServiceReceiver != null) {
                    StartupTrigger.triggerStartService(this, startServiceReceiver!!)
                }
            }
        }
        return START_STICKY  // keep alive
    }


    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        try {
            super.onCreate()
            Log.i(LOG_ID, "onCreate called foreground: $foreground")
            service = this
            isRunning = true

            Log.init(this)

            ReceiveData.initData(this)
            SourceTaskService.run(this)
            PackageUtils.updatePackages(this)

            WearPhoneManager.init(this, appSource == AppSource.PHONE_APP)

            ReceiverManager.updateSourceReceiver(this)
            broadcastServiceAPI = BroadcastServiceAPI()
            broadcastServiceAPI.init()
            updateBatteryReceiver()
            updateScreenReceiver()

            sharedPref!!.registerOnSharedPreferenceChangeListener(this)

            patientName = sharedPref!!.getString(Constants.PATIENT_NAME, "")

            TextToSpeechUtils.initTextToSpeech(this)
            created = true
            InternalNotifier.notify(this, NotifySource.SERVICE_STARTED, null)

            if (BuildConfig.DEBUG && sharedPref!!.getBoolean(Constants.SHARED_PREF_DUMMY_VALUES, false)) {
                Thread {
                    try {
                        ReceiveData.time = 0L
                        while (true) {
                            // create Thread which send dummy intents
                            this.sendBroadcast(GlucoDataUtils.getDummyGlucodataIntent(false))
                            Thread.sleep(1000)
                        }
                    } catch (exc: Exception) {
                        Log.e(LOG_ID, "Send dummy glucodata exception: " + exc.toString())
                    }
                }.start()
            }
            // remove obsolete notification channels
            Channels.deleteNotificationChannel(this, ChannelType.ANDROID_AUTO)  // only available in GlucoDataAuto!
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreate exception: " + exc.toString())
        }
    }

    override fun onDestroy() {
        try {
            Log.w(LOG_ID, "onDestroy called")
            sharedPref!!.unregisterOnSharedPreferenceChangeListener(this)
            ReceiverManager.unregisterSourceReceiver(this)
            broadcastServiceAPI.close(this)
            if(batteryReceiver != null) {
                unregisterReceiver(batteryReceiver)
                batteryReceiver = null
            }
            if(screenEventReceiver != null) {
                unregisterReceiver(screenEventReceiver)
                screenEventReceiver = null
            }
            TimeTaskService.stop()
            SourceTaskService.stop()
            WearPhoneManager.close()
            super.onDestroy()
            service = null
            created = false
            isRunning = false
            isForegroundService = false
            TextToSpeechUtils.destroyTextToSpeech(this)

            Log.close(this)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onDestroy exception: " + exc.toString())
        }
    }

    open fun updateBatteryReceiver() {
        try {
            if (sharedPref!!.getBoolean(Constants.SHARED_PREF_BATTERY_RECEIVER_ENABLED, true)) {
                if(batteryReceiver == null) {
                    Log.i(LOG_ID, "register batteryReceiver")
                    batteryReceiver = BatteryReceiver()
                    registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                }
            } else if(batteryReceiver != null) {
                Log.i(LOG_ID, "unregister batteryReceiver")
                unregisterReceiver(batteryReceiver)
                batteryReceiver = null
                // notify new battery level to update UI
                BatteryReceiver.batteryPercentage = 0
                InternalNotifier.notify(this, NotifySource.BATTERY_LEVEL, BatteryReceiver.batteryBundle)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateBatteryReceiver exception: " + exc.toString())
        }
    }

    open fun updateScreenReceiver() {
        // do nothing here, only so sub-classes
    }


    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        try {
            Log.d(LOG_ID, "onSharedPreferenceChanged called with key $key")
            var shareSettings = false
            when(key) {
                Constants.SHARED_PREF_SOURCE_JUGGLUCO_ENABLED,
                Constants.SHARED_PREF_SOURCE_XDRIP_ENABLED,
                Constants.SHARED_PREF_SOURCE_AAPS_ENABLED,
                Constants.SHARED_PREF_SOURCE_BYODA_ENABLED,
                Constants.SHARED_PREF_SOURCE_EVERSENSE_ENABLED,
                Constants.SHARED_PREF_SOURCE_DIABOX_ENABLED,
                Constants.SHARED_PREF_SOURCE_AIDEX_ENABLED,
                Constants.SHARED_PREF_SOURCE_NOTIFICATION_ENABLED -> {
                    ReceiverManager.updateSourceReceiver(this, key)
                    shareSettings = true
                }
                Constants.SHARED_PREF_SHOW_OTHER_UNIT,
                Constants.SHARED_PREF_SENSOR_RUNTIME -> {
                    shareSettings = true
                }
                Constants.SHARED_PREF_BATTERY_RECEIVER_ENABLED -> {
                    updateBatteryReceiver()
                }
                Constants.SHARED_PREF_PHONE_WEAR_SCREEN_OFF_UPDATE -> {
                    updateScreenReceiver()
                    shareSettings = true
                }
                Constants.PATIENT_NAME -> {
                    patientName = sharedPreferences.getString(Constants.PATIENT_NAME, "")
                    shareSettings = true
                }
            }
            if (shareSettings) {
                InternalNotifier.notify(this, NotifySource.SETTINGS, getSettings())
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString())
        }
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        super.onTimeout(startId, fgsType)
        Log.e(LOG_ID, "onTimeout called with startId $startId and fgsType $fgsType")
        //stopSelf() better force crash to see, what happens...
    }

    abstract fun getSettings(): Bundle?
    abstract fun setSettings(context: Context, bundle: Bundle)
}
