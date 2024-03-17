package de.michelinside.glucodatahandler.common

import android.annotation.SuppressLint
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
import de.michelinside.glucodatahandler.common.utils.GlucoDataUtils
import de.michelinside.glucodatahandler.common.notification.ChannelType
import de.michelinside.glucodatahandler.common.notification.Channels
import de.michelinside.glucodatahandler.common.utils.Utils

enum class AppSource {
    NOT_SET,
    PHONE_APP,
    WEAR_APP;
}

abstract class GlucoDataService(source: AppSource) : WearableListenerService() {
    private lateinit var receiver: GlucoseDataReceiver
    private lateinit var batteryReceiver: BatteryReceiver
    private lateinit var xDripReceiver: XDripBroadcastReceiver
    private lateinit var aapsReceiver: AAPSReceiver

    @SuppressLint("StaticFieldLeak")
    companion object {
        private val LOG_ID = "GDH.GlucoDataService"
        private var isForegroundService = false
        @JvmStatic
        @SuppressLint("StaticFieldLeak")
        protected var connection: WearPhoneConnection? = null
        val foreground get() = isForegroundService
        const val NOTIFICATION_ID = 123
        var appSource = AppSource.NOT_SET
        private var isRunning = false
        val running get() = isRunning
        @SuppressLint("StaticFieldLeak")
        var service: GlucoDataService? = null
        var context: Context? get() {
            if(service != null)
                return service!!.applicationContext
            return extContext
        } set(value) {
            extContext = value
        }
        @SuppressLint("StaticFieldLeak")
        private var extContext: Context? = null
        val sharedPref: SharedPreferences? get() {
            if (context != null) {
                return context!!.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            }
            return null
        }

        fun start(source: AppSource, context: Context, cls: Class<*>, force: Boolean = false) {
            if (!running || force) {
                Log.v(LOG_ID, "start called")
                try {
                    appSource = source
                    val serviceIntent = Intent(
                        context,
                        cls
                    )
                    /*
                    val sharedPref = context.getSharedPreferences(
                        Constants.SHARED_PREF_TAG,
                        Context.MODE_PRIVATE
                    )*/
                    serviceIntent.putExtra(
                        Constants.SHARED_PREF_FOREGROUND_SERVICE,
                        // default on wear and phone
                        true//sharedPref.getBoolean(Constants.SHARED_PREF_FOREGROUND_SERVICE, true)
                    )
                    context.startForegroundService(serviceIntent)
                } catch (exc: Exception) {
                    Log.e(
                        LOG_ID,
                        "start exception: " + exc.message.toString()
                    )
                }
            }
        }

        fun checkForConnectedNodes() {
            try {
                if (connection!=null)
                    connection!!.checkForConnectedNodes()
            } catch (exc: Exception) {
                Log.e(
                    LOG_ID,
                    "checkForConnectedNodes exception: " + exc.message.toString()
                )
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
            Log.v(LOG_ID, "onStartCommand called")
            super.onStartCommand(intent, flags, startId)
            val isForeground = true // intent?.getBooleanExtra(Constants.SHARED_PREF_FOREGROUND_SERVICE, true)    --> always use foreground!!!
            if (isForeground && !isForegroundService && Utils.checkPermission(this, android.Manifest.permission.POST_NOTIFICATIONS, Build.VERSION_CODES.TIRAMISU)) {
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

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        try {
            super.onCreate()
            Log.i(LOG_ID, "onCreate called")
            service = this
            isRunning = true

            ReceiveData.initData(this)
            SourceTaskService.run(this)

            connection = WearPhoneConnection()
            connection!!.open(this, appSource == AppSource.PHONE_APP)

            Log.d(LOG_ID, "Register Receiver")
            receiver = GlucoseDataReceiver()
            xDripReceiver = XDripBroadcastReceiver()
            aapsReceiver = AAPSReceiver()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(receiver, IntentFilter("glucodata.Minute"), RECEIVER_EXPORTED or RECEIVER_VISIBLE_TO_INSTANT_APPS)
                registerReceiver(xDripReceiver,IntentFilter("com.eveningoutpost.dexdrip.BgEstimate"), RECEIVER_EXPORTED or RECEIVER_VISIBLE_TO_INSTANT_APPS)
                //registerReceiver(aapsReceiver,IntentFilter("info.nightscout.androidaps.status"), RECEIVER_EXPORTED or RECEIVER_VISIBLE_TO_INSTANT_APPS)
            } else {
                registerReceiver(receiver, IntentFilter("glucodata.Minute"))
                registerReceiver(xDripReceiver,IntentFilter("com.eveningoutpost.dexdrip.BgEstimate"))
                //registerReceiver(aapsReceiver,IntentFilter("info.nightscout.androidaps.status"))

            }
            batteryReceiver = BatteryReceiver()
            registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

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
            unregisterReceiver(receiver)
            unregisterReceiver(batteryReceiver)
            unregisterReceiver(xDripReceiver)
            //unregisterReceiver(aapsReceiver)
            TimeTaskService.stop()
            SourceTaskService.stop()
            connection!!.close()
            connection = null
            super.onDestroy()
            service = null
            isRunning = false
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onDestroy exception: " + exc.toString())
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
