package de.michelinside.glucodatahandler.common

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.android.gms.wearable.WearableListenerService
import de.michelinside.glucodatahandler.common.notification.ChannelType
import de.michelinside.glucodatahandler.common.notification.Channels
import de.michelinside.glucodatahandler.common.notifier.DataSource
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.receiver.AAPSReceiver
import de.michelinside.glucodatahandler.common.receiver.BatteryReceiver
import de.michelinside.glucodatahandler.common.receiver.BroadcastServiceAPI
import de.michelinside.glucodatahandler.common.receiver.DexcomBroadcastReceiver
import de.michelinside.glucodatahandler.common.receiver.DiaboxReceiver
import de.michelinside.glucodatahandler.common.receiver.GlucoseDataReceiver
import de.michelinside.glucodatahandler.common.receiver.NamedBroadcastReceiver
import de.michelinside.glucodatahandler.common.receiver.NamedReceiver
import de.michelinside.glucodatahandler.common.receiver.NotificationReceiver
import de.michelinside.glucodatahandler.common.receiver.NsEmulatorReceiver
import de.michelinside.glucodatahandler.common.receiver.ScreenEventReceiver
import de.michelinside.glucodatahandler.common.receiver.XDripBroadcastReceiver
import de.michelinside.glucodatahandler.common.tasks.BackgroundWorker
import de.michelinside.glucodatahandler.common.tasks.SourceTaskService
import de.michelinside.glucodatahandler.common.tasks.TimeTaskService
import de.michelinside.glucodatahandler.common.utils.GlucoDataUtils
import de.michelinside.glucodatahandler.common.utils.PackageUtils
import de.michelinside.glucodatahandler.common.utils.TextToSpeechUtils
import de.michelinside.glucodatahandler.common.utils.Utils
import java.util.Locale


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
        @JvmStatic
        @SuppressLint("StaticFieldLeak")
        protected var connection: WearPhoneConnection? = null
        var startServiceReceiver: Class<*>? = null
        val foreground get() = isForegroundService
        const val NOTIFICATION_ID = 1234
        var appSource = AppSource.NOT_SET
        private var isRunning = false
        val running get() = isRunning
        private var created = false

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
            return service != null
        }

        @SuppressLint("StaticFieldLeak")
        private var extContext: Context? = null
        val sharedPref: SharedPreferences? get() {
            if (context != null) {
                return context!!.getSharedPreferences(Constants.SHARED_PREF_TAG, MODE_PRIVATE)
            }
            return null
        }

        fun start(source: AppSource, context: Context, cls: Class<*>) {
            Log.v(LOG_ID, "start called (running: $running - foreground: $foreground)")
            if (!running || !foreground) {
                try {
                    appSource = source
                    migrateSettings(context)
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
                    if(!foreground && startServiceReceiver != null) {
                        // trigger also foreground alarm
                        triggerStartService(context, startServiceReceiver!!)
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
                            triggerStartService(context, startServiceReceiver!!)
                        }
                    } else {
                        Log.e(LOG_ID,"start exception: " + exc.message.toString())
                        isRunning = false
                    }
                }
            }
        }

        private var alarmManager: AlarmManager? = null
        private var alarmPendingIntent: PendingIntent? = null

        private fun stopTrigger() {
            try {
                if(alarmManager != null && alarmPendingIntent != null) {
                    Log.i(LOG_ID, "Stop trigger")
                    alarmManager!!.cancel(alarmPendingIntent!!)
                    alarmManager = null
                    alarmPendingIntent = null
                }
            } catch (exc: Exception) {
                Log.e(LOG_ID, "stopTrigger exception: " + exc.message.toString())
            }
        }

        private fun triggerStartService(context: Context, receiver: Class<*>) {
            try {
                Log.i(LOG_ID, "Trigger start service - foreground: $foreground - alarm active: ${alarmManager != null && alarmPendingIntent != null}")
                if(foreground || (alarmManager != null && alarmPendingIntent != null))
                    return
                alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                var hasExactAlarmPermission = true
                if (!Utils.canScheduleExactAlarms(context)) {
                    Log.d(LOG_ID, "Need permission to set exact alarm!")
                    hasExactAlarmPermission = false
                }
                val intent = Intent(context, receiver)
                intent.action = Constants.ACTION_START_FOREGROUND
                intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                alarmPendingIntent = PendingIntent.getBroadcast(
                    context,
                    911,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
                )
                val alarmTime = System.currentTimeMillis() + 1000
                Log.i(LOG_ID, "Trigger alarm at ${Utils.getUiTimeStamp(alarmTime)} - exactAlarm: $hasExactAlarmPermission")
                if (hasExactAlarmPermission) {
                    alarmManager!!.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        alarmTime,
                        alarmPendingIntent!!
                    )
                } else {
                    alarmManager!!.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        alarmTime,
                        alarmPendingIntent!!
                    )
                }
            } catch (exc: Exception) {
                Log.e(LOG_ID, "triggerStartService exception: " + exc.message.toString())
                stopTrigger()
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

        fun checkForConnectedNodes(refreshDataOnly: Boolean = false) {
            try {
                Log.d(LOG_ID, "checkForConnectedNodes called for dataOnly=$refreshDataOnly - connection: ${connection!=null}")
                if (connection!=null) {
                    if(!refreshDataOnly)
                        connection!!.checkForConnectedNodes(true)
                    else
                        connection!!.checkForNodesWithoutData()
                }
            } catch (exc: Exception) {
                Log.e(
                    LOG_ID,
                    "checkForConnectedNodes exception: " + exc.message.toString()
                )
            }
        }

        fun resetWearPhoneConnection() {
            try {
                if (connection != null) {
                    connection!!.resetConnection()
                }
            } catch (exc: Exception) {
                Log.e(LOG_ID,"resetWearPhoneConnection exception: " + exc.message.toString())
            }
        }

        fun sendCommand(command: Command, extras: Bundle? = null) {
            try {
                if (connection!=null)
                    connection!!.sendCommand(command, extras)
            } catch (exc: Exception) {
                Log.e(
                    LOG_ID,
                    "sendCommand exception: " + exc.message.toString()
                )
            }
        }

        fun getWearPhoneConnection(): WearPhoneConnection? {
            return connection
        }

        private var glucoDataReceiver: GlucoseDataReceiver? = null
        private var xDripReceiver: XDripBroadcastReceiver?  = null
        private var aapsReceiver: AAPSReceiver?  = null
        private var dexcomReceiver: DexcomBroadcastReceiver? = null
        private var nsEmulatorReceiver: NsEmulatorReceiver? = null
        private var diaboxReceiver: DiaboxReceiver? = null
        private var notificationReceiver: NotificationReceiver? = null
        private val registeredReceivers = mutableSetOf<String>()

        @SuppressLint("UnspecifiedRegisterReceiverFlag")
        fun registerReceiver(context: Context, receiver: NamedReceiver, filter: IntentFilter): Boolean {
            Log.i(LOG_ID, "Register receiver ${receiver.getName()} for $receiver on $context")
            try {
                if (receiver is NamedBroadcastReceiver) {
                    PackageUtils.registerReceiver(context, receiver, filter)
                }
                registeredReceivers.add(receiver.getName())
                return true
            } catch (exc: Exception) {
                Log.e(LOG_ID, "registerReceiver exception: " + exc.toString())
            }
            return false
        }

        fun unregisterReceiver(context: Context, receiver: NamedReceiver?) {
            try {
                if (receiver != null) {
                    Log.i(LOG_ID, "Unregister receiver ${receiver.getName()} on $context")
                    registeredReceivers.remove(receiver.getName())
                    if (receiver is NamedBroadcastReceiver) {
                        context.unregisterReceiver(receiver)
                    }
                }
            } catch (exc: Exception) {
                Log.e(LOG_ID, "unregisterReceiver exception: " + exc.toString())
            }
        }

        fun isRegistered(receiver: NamedReceiver): Boolean {
            return registeredReceivers.contains(receiver.getName())
        }

        fun updateSourceReceiver(context: Context, key: String? = null) {
            Log.d(LOG_ID, "Register receiver")
            try {
                val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, MODE_PRIVATE)
                if(key.isNullOrEmpty() || key == Constants.SHARED_PREF_SOURCE_JUGGLUCO_ENABLED) {
                    if (sharedPref.getBoolean(Constants.SHARED_PREF_SOURCE_JUGGLUCO_ENABLED, true)) {
                        if(glucoDataReceiver == null) {
                            glucoDataReceiver = GlucoseDataReceiver()
                            if(!registerReceiver(context, glucoDataReceiver!!, IntentFilter("glucodata.Minute")))
                                glucoDataReceiver = null
                        }
                    } else if (glucoDataReceiver != null) {
                        unregisterReceiver(context, glucoDataReceiver)
                        glucoDataReceiver = null
                    }
                }

                if(key.isNullOrEmpty() || key == Constants.SHARED_PREF_SOURCE_XDRIP_ENABLED) {
                    if (sharedPref.getBoolean(Constants.SHARED_PREF_SOURCE_XDRIP_ENABLED, true)) {
                        if(xDripReceiver == null) {
                            xDripReceiver = XDripBroadcastReceiver()
                            if(!registerReceiver(context, xDripReceiver!!, IntentFilter("com.eveningoutpost.dexdrip.BgEstimate")))
                                xDripReceiver = null
                        }
                    } else if (xDripReceiver != null) {
                        unregisterReceiver(context, xDripReceiver)
                        xDripReceiver = null
                    }
                }

                if(key.isNullOrEmpty() || key == Constants.SHARED_PREF_SOURCE_AAPS_ENABLED) {
                    if (sharedPref.getBoolean(Constants.SHARED_PREF_SOURCE_AAPS_ENABLED, true)) {
                        if(aapsReceiver == null) {
                            aapsReceiver = AAPSReceiver()
                            if(!registerReceiver(context, aapsReceiver!!, IntentFilter(Intents.AAPS_BROADCAST_ACTION)))
                                aapsReceiver = null
                        }
                    } else if (aapsReceiver != null) {
                        unregisterReceiver(context, aapsReceiver)
                        aapsReceiver = null
                    }
                }

                if(key.isNullOrEmpty() || key == Constants.SHARED_PREF_SOURCE_BYODA_ENABLED) {
                    if (sharedPref.getBoolean(Constants.SHARED_PREF_SOURCE_BYODA_ENABLED, true)) {
                        if(dexcomReceiver == null) {
                            val dexcomFilter = IntentFilter()
                            dexcomFilter.addAction(Intents.DEXCOM_CGM_BROADCAST_ACTION)
                            dexcomFilter.addAction(Intents.DEXCOM_G7_BROADCAST_ACTION)
                            dexcomReceiver = DexcomBroadcastReceiver()
                            if(!registerReceiver(context, dexcomReceiver!!, dexcomFilter))
                                dexcomReceiver = null
                        }
                    } else if (dexcomReceiver != null) {
                        unregisterReceiver(context, dexcomReceiver)
                        dexcomReceiver = null
                    }
                }

                if(key.isNullOrEmpty() || key == Constants.SHARED_PREF_SOURCE_EVERSENSE_ENABLED) {
                    if (sharedPref.getBoolean(Constants.SHARED_PREF_SOURCE_EVERSENSE_ENABLED, true)) {
                        if(nsEmulatorReceiver == null) {
                            nsEmulatorReceiver = NsEmulatorReceiver()
                            if(!registerReceiver(context, nsEmulatorReceiver!!, IntentFilter(Intents.NS_EMULATOR_BROADCAST_ACTION)))
                                nsEmulatorReceiver = null
                        }
                    } else if (nsEmulatorReceiver != null) {
                        unregisterReceiver(context, nsEmulatorReceiver)
                        nsEmulatorReceiver = null
                    }
                }

                if(key.isNullOrEmpty() || key == Constants.SHARED_PREF_SOURCE_DIABOX_ENABLED) {
                    if (sharedPref.getBoolean(Constants.SHARED_PREF_SOURCE_DIABOX_ENABLED, true)) {
                        if(diaboxReceiver == null) {
                            diaboxReceiver = DiaboxReceiver()
                            if(!registerReceiver(context, diaboxReceiver!!, IntentFilter(Intents.DIABOX_BROADCAST_ACTION)))
                                diaboxReceiver = null
                        }
                    } else if (diaboxReceiver != null) {
                        unregisterReceiver(context, diaboxReceiver)
                        diaboxReceiver = null
                    }
                }

                if (key.isNullOrEmpty() || key == Constants.SHARED_PREF_SOURCE_NOTIFICATION_ENABLED) {
                    // default to false because reading notifications is a scary permission to give for no reason
                    if (sharedPref.getBoolean(Constants.SHARED_PREF_SOURCE_NOTIFICATION_ENABLED, false)) {
                        Log.d(LOG_ID, "Notification source enabled")
                        val notificationListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                        if (!notificationListeners.contains(context.packageName)) {
                            // disable until permission is granted:
                            with(sharedPref.edit()) {
                                putBoolean(Constants.SHARED_PREF_SOURCE_NOTIFICATION_ENABLED, false)
                                apply()
                            }
                            // request permissions
                            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } else {
                            notificationReceiver = NotificationReceiver()
                            registerReceiver(context, notificationReceiver!!, IntentFilter())
                        }
                    } else if(notificationReceiver!=null) {
                        unregisterReceiver(context, notificationReceiver)
                        notificationReceiver = null
                    }
                    // notification listeners can not be unregistered
                }


            } catch (exc: Exception) {
                Log.e(LOG_ID, "registerSourceReceiver exception: " + exc.toString())
            }
        }

        fun unregisterSourceReceiver(context: Context) {
            try {
                Log.d(LOG_ID, "Unregister receiver")
                if (glucoDataReceiver != null) {
                    unregisterReceiver(context, glucoDataReceiver)
                    glucoDataReceiver = null
                }
                if (xDripReceiver != null) {
                    unregisterReceiver(context, xDripReceiver)
                    xDripReceiver = null
                }
                if (dexcomReceiver != null) {
                    unregisterReceiver(context, dexcomReceiver)
                    dexcomReceiver = null
                }
                if (nsEmulatorReceiver != null) {
                    unregisterReceiver(context, nsEmulatorReceiver)
                    nsEmulatorReceiver = null
                }
                if (aapsReceiver != null) {
                    unregisterReceiver(context, aapsReceiver)
                    aapsReceiver = null
                }
                if (diaboxReceiver != null) {
                    unregisterReceiver(context, diaboxReceiver)
                    diaboxReceiver = null
                }
                if(notificationReceiver != null) {
                    unregisterReceiver(context, notificationReceiver)
                    notificationReceiver = null
                }
            } catch (exc: Exception) {
                Log.e(LOG_ID, "unregisterSourceReceiver exception: " + exc.toString())
            }
        }

        fun migrateSettings(context: Context) {
            val sharedPrefs = context.getSharedPreferences(Constants.SHARED_PREF_TAG, MODE_PRIVATE)
            Log.v(LOG_ID, "migrateSettings called")
            if(!sharedPrefs.contains(Constants.SHARED_PREF_OBSOLETE_TIME)) {
                val sharedGlucosePref = context.getSharedPreferences(Constants.GLUCODATA_BROADCAST_ACTION, MODE_PRIVATE)
                var obsoleteTime = 6
                if(sharedGlucosePref.contains(Constants.EXTRA_SOURCE_INDEX)) {
                    val srcOrdinal = sharedGlucosePref.getInt(Constants.EXTRA_SOURCE_INDEX, DataSource.NONE.ordinal)
                    if (srcOrdinal == DataSource.JUGGLUCO.ordinal || srcOrdinal == DataSource.LIBRELINK.ordinal) {
                        obsoleteTime = 5
                    }
                }
                Log.i(LOG_ID, "Migrate default obsolete time $obsoleteTime minutes")
                with(sharedPrefs.edit()) {
                    putInt(Constants.SHARED_PREF_OBSOLETE_TIME, obsoleteTime)
                    apply()
                }
            }

            // show other unit should be default on for mmol/l as there was the raw value before
            // so this is only related, if use mmol/l is already set, else set to false
            if(!sharedPrefs.contains(Constants.SHARED_PREF_SHOW_OTHER_UNIT)) {
                val useMmol = if(sharedPrefs.contains(Constants.SHARED_PREF_USE_MMOL))
                    sharedPrefs.getBoolean(Constants.SHARED_PREF_USE_MMOL, false)
                else false
                with(sharedPrefs.edit()) {
                    putBoolean(Constants.SHARED_PREF_SHOW_OTHER_UNIT, useMmol)
                    apply()
                }
            }

            if(!sharedPrefs.contains(Constants.SHARED_PREF_DEXCOM_SHARE_USE_US_URL)) {
                // check local for US and set to true if set
                val currentLocale = Locale.getDefault()
                val countryCode = currentLocale.country
                Log.i(LOG_ID, "Using country code $countryCode")
                if(countryCode == "US") {
                    with(sharedPrefs.edit()) {
                        putBoolean(Constants.SHARED_PREF_DEXCOM_SHARE_USE_US_URL, true)
                        apply()
                    }
                }
            }

            if(sharedPrefs.contains(Constants.SHARED_PREF_ALARM_SNOOZE_ON_NOTIFICATION)) {
                with(sharedPrefs.edit()) {
                    remove(Constants.SHARED_PREF_ALARM_SNOOZE_ON_NOTIFICATION)
                    putStringSet(Constants.SHARED_PREF_ALARM_SNOOZE_NOTIFICATION_BUTTONS, mutableSetOf("60", "90", "120"))
                    apply()
                }
            }

        }

        fun getSettings(): Bundle {
            val bundle = ReceiveData.getSettingsBundle()
            // other settings
            if (sharedPref != null) {
                bundle.putBoolean(Constants.SHARED_PREF_SHOW_OTHER_UNIT, sharedPref!!.getBoolean(Constants.SHARED_PREF_SHOW_OTHER_UNIT, ReceiveData.isMmol))
                bundle.putBoolean(Constants.SHARED_PREF_SOURCE_JUGGLUCO_ENABLED, sharedPref!!.getBoolean(Constants.SHARED_PREF_SOURCE_JUGGLUCO_ENABLED, true))
                bundle.putBoolean(Constants.SHARED_PREF_SOURCE_XDRIP_ENABLED, sharedPref!!.getBoolean(Constants.SHARED_PREF_SOURCE_XDRIP_ENABLED, true))
                bundle.putBoolean(Constants.SHARED_PREF_SOURCE_AAPS_ENABLED, sharedPref!!.getBoolean(Constants.SHARED_PREF_SOURCE_AAPS_ENABLED, true))
                bundle.putBoolean(Constants.SHARED_PREF_SOURCE_BYODA_ENABLED, sharedPref!!.getBoolean(Constants.SHARED_PREF_SOURCE_BYODA_ENABLED, true))
                bundle.putBoolean(Constants.SHARED_PREF_SOURCE_EVERSENSE_ENABLED, sharedPref!!.getBoolean(Constants.SHARED_PREF_SOURCE_EVERSENSE_ENABLED, true))
                bundle.putBoolean(Constants.SHARED_PREF_SOURCE_DIABOX_ENABLED, sharedPref!!.getBoolean(Constants.SHARED_PREF_SOURCE_DIABOX_ENABLED, true))
                bundle.putBoolean(Constants.SHARED_PREF_SOURCE_NOTIFICATION_ENABLED, sharedPref!!.getBoolean(Constants.SHARED_PREF_SOURCE_NOTIFICATION_ENABLED, false))
                bundle.putBoolean(Constants.SHARED_PREF_PHONE_WEAR_SCREEN_OFF_UPDATE, sharedPref!!.getBoolean(Constants.SHARED_PREF_PHONE_WEAR_SCREEN_OFF_UPDATE, true))
            }
            Log.v(LOG_ID, "getSettings called with bundle ${(Utils.dumpBundle(bundle))}")
            return bundle
        }

        fun setSettings(context: Context, bundle: Bundle) {
            Log.v(LOG_ID, "setSettings called with bundle ${(Utils.dumpBundle(bundle))}")
            val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, MODE_PRIVATE)
            with(sharedPref!!.edit()) {
                putBoolean(Constants.SHARED_PREF_SHOW_OTHER_UNIT, bundle.getBoolean(Constants.SHARED_PREF_SHOW_OTHER_UNIT, ReceiveData.isMmol))
                putBoolean(Constants.SHARED_PREF_SOURCE_JUGGLUCO_ENABLED, bundle.getBoolean(Constants.SHARED_PREF_SOURCE_JUGGLUCO_ENABLED, true))
                putBoolean(Constants.SHARED_PREF_SOURCE_XDRIP_ENABLED, bundle.getBoolean(Constants.SHARED_PREF_SOURCE_XDRIP_ENABLED, true))
                putBoolean(Constants.SHARED_PREF_SOURCE_AAPS_ENABLED, bundle.getBoolean(Constants.SHARED_PREF_SOURCE_AAPS_ENABLED, true))
                putBoolean(Constants.SHARED_PREF_SOURCE_BYODA_ENABLED, bundle.getBoolean(Constants.SHARED_PREF_SOURCE_BYODA_ENABLED, true))
                putBoolean(Constants.SHARED_PREF_SOURCE_EVERSENSE_ENABLED, bundle.getBoolean(Constants.SHARED_PREF_SOURCE_EVERSENSE_ENABLED, true))
                putBoolean(Constants.SHARED_PREF_SOURCE_DIABOX_ENABLED, bundle.getBoolean(Constants.SHARED_PREF_SOURCE_DIABOX_ENABLED, true))
                putBoolean(Constants.SHARED_PREF_PHONE_WEAR_SCREEN_OFF_UPDATE, bundle.getBoolean(Constants.SHARED_PREF_PHONE_WEAR_SCREEN_OFF_UPDATE, true))
                putBoolean(Constants.SHARED_PREF_SOURCE_NOTIFICATION_ENABLED, bundle.getBoolean(Constants.SHARED_PREF_SOURCE_NOTIFICATION_ENABLED, false))
                apply()
            }
            ReceiveData.setSettings(sharedPref, bundle)
        }
    }

    init {
        appSource = source
    }

    abstract fun getNotification() : Notification

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            Log.i(LOG_ID, "onStartCommand called foregroundService: $isForegroundService")
            GdhUncaughtExecptionHandler.init()
            if (!isForegroundService) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Log.i(LOG_ID, "Starting service in foreground with type ${ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC}!")
                    startForeground(
                        NOTIFICATION_ID,
                        getNotification(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    Log.i(LOG_ID, "Starting service in foreground!")
                    startForeground(NOTIFICATION_ID, getNotification())
                }
                isForegroundService = true
                stopTrigger()
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onStartCommand exception: " + exc.toString())
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && exc is ForegroundServiceStartNotAllowedException) {
                if(startServiceReceiver != null) {
                    triggerStartService(this, startServiceReceiver!!)
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

            ReceiveData.initData(this)
            SourceTaskService.run(this)
            PackageUtils.updatePackages(this)

            connection = WearPhoneConnection()
            connection!!.open(this, appSource == AppSource.PHONE_APP)

            updateSourceReceiver(this)
            broadcastServiceAPI = BroadcastServiceAPI()
            broadcastServiceAPI.init()
            updateBatteryReceiver()
            updateScreenReceiver()

            sharedPref!!.registerOnSharedPreferenceChangeListener(this)

            TextToSpeechUtils.initTextToSpeech(this)
            created = true

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
            unregisterSourceReceiver(this)
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
            connection!!.close()
            connection = null
            super.onDestroy()
            service = null
            created = false
            isRunning = false
            isForegroundService = false
            TextToSpeechUtils.destroyTextToSpeech(this)
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


    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
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
                Constants.SHARED_PREF_SOURCE_NOTIFICATION_ENABLED -> {
                    updateSourceReceiver(this, key)
                    shareSettings = true
                }
                Constants.SHARED_PREF_SHOW_OTHER_UNIT -> {
                    shareSettings = true
                }
                Constants.SHARED_PREF_BATTERY_RECEIVER_ENABLED -> {
                    updateBatteryReceiver()
                }
                Constants.SHARED_PREF_PHONE_WEAR_SCREEN_OFF_UPDATE -> {
                    updateScreenReceiver()
                    shareSettings = true
                }
            }
            if (shareSettings) {
                val extras = Bundle()
                extras.putBundle(Constants.SETTINGS_BUNDLE, getSettings())
                InternalNotifier.notify(this, NotifySource.SETTINGS, extras)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString())
        }
    }

    fun sendToConnectedDevices(dataSource: NotifySource, extras: Bundle) {
        Thread {
            try {
                if(connection != null)
                    connection!!.sendMessage(dataSource, extras, null)
            } catch (exc: Exception) {
                Log.e(LOG_ID, "SendMessage exception: " + exc.toString())
            }
        }.start()
    }
}
