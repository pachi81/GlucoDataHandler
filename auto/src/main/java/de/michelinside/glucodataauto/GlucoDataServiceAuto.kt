package de.michelinside.glucodataauto

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.car.app.connection.CarConnection
import de.michelinside.glucodataauto.android_auto.CarMediaBrowserService
import de.michelinside.glucodataauto.android_auto.CarMediaPlayer
import de.michelinside.glucodataauto.android_auto.CarNotification
import de.michelinside.glucodatahandler.common.AppSource
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GdhUncaughtExecptionHandler
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.notification.ChannelType
import de.michelinside.glucodatahandler.common.notification.Channels
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodataauto.receiver.AAPSReceiver
import de.michelinside.glucodataauto.receiver.DexcomBroadcastReceiver
import de.michelinside.glucodataauto.receiver.DiaboxReceiver
import de.michelinside.glucodataauto.receiver.GlucoDataReceiver
import de.michelinside.glucodataauto.receiver.LibrePatchedReceiver
import de.michelinside.glucodataauto.receiver.NsEmulatorReceiver
import de.michelinside.glucodataauto.receiver.XDripReceiver
import de.michelinside.glucodatahandler.common.GlucoDataService.Companion.checkNotificationReceiverPermission
import de.michelinside.glucodatahandler.common.GlucoDataService.Companion.updateNotificationReceiver
import de.michelinside.glucodatahandler.common.Intents
import de.michelinside.glucodatahandler.common.database.dbAccess
import de.michelinside.glucodatahandler.common.notifier.DataSource
import de.michelinside.glucodatahandler.common.receiver.BroadcastServiceAPI
import de.michelinside.glucodatahandler.common.receiver.GlucoseDataReceiver
import de.michelinside.glucodatahandler.common.tasks.BackgroundWorker
import de.michelinside.glucodatahandler.common.tasks.SourceTaskService
import de.michelinside.glucodatahandler.common.tasks.TimeTaskService
import de.michelinside.glucodatahandler.common.utils.Log
import de.michelinside.glucodatahandler.common.utils.PackageUtils
import de.michelinside.glucodatahandler.common.utils.TextToSpeechUtils
import de.michelinside.glucodatahandler.common.utils.Utils
import de.michelinside.glucodatahandler.common.R as CR
import androidx.core.content.edit
import de.michelinside.glucodatahandler.common.SettingsMigrator

class GlucoDataServiceAuto: Service(), SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        private const val LOG_ID = "GDH.AA.GlucoDataServiceAuto"
        private var isForegroundService = false
        const val NOTIFICATION_ID = 666
        private var car_connected = false
        private var running = false
        private var init = false
        private var dataSyncCount = 0

        private var glucoDataReceiver: GlucoDataReceiver? = null
        private var xDripReceiver: XDripReceiver?  = null
        private var aapsReceiver: AAPSReceiver?  = null
        private var dexcomReceiver: DexcomBroadcastReceiver? = null
        private var nsEmulatorReceiver: NsEmulatorReceiver? = null
        private var diaboxReceiver: DiaboxReceiver? = null
        private val broadcastServiceAPI = BroadcastServiceAPI()
        private var librePatchedReceiver: LibrePatchedReceiver?  = null

        val connected: Boolean get() = car_connected

        fun init(context: Context) {
            Log.d(LOG_ID, "init called: init=$init")
            if(!init) {
                Log.i(LOG_ID, "init called")
                GlucoDataService.appSource = AppSource.AUTO_APP
                GlucoDataService.context = context.applicationContext
                ReceiveData.initData(context.applicationContext, false)
                Log.init(context)
                migrateSettings(context)
                startService(context, false, true)
                init = true
            }
        }

        private fun migrateSettings(context: Context) {
            Log.i(LOG_ID, "migrateSettings called")
            SettingsMigrator.migrateSettings(context)
            val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, MODE_PRIVATE)
            if(!sharedPref.contains(Constants.SHARED_PREF_NIGHTSCOUT_IOB_COB)) {
                sharedPref.edit {
                    putBoolean(Constants.SHARED_PREF_NIGHTSCOUT_IOB_COB, false)
                }
            }

            // Juggluco webserver settings
            if(!sharedPref.contains(Constants.SHARED_PREF_SOURCE_JUGGLUCO_WEBSERVER_ENABLED) || !sharedPref.contains(Constants.SHARED_PREF_SOURCE_JUGGLUCO_WEBSERVER_IOB_SUPPORT)) {
                // check current source for Juggluco and if Nightscout is enabled for local requests supporting IOB
                var webServer = false
                var apiSecret = ""
                if(sharedPref.getBoolean(Constants.SHARED_PREF_SOURCE_JUGGLUCO_ENABLED, true)
                    && sharedPref.getBoolean(Constants.SHARED_PREF_NIGHTSCOUT_ENABLED, false)
                    && sharedPref.getBoolean(Constants.SHARED_PREF_NIGHTSCOUT_IOB_COB, false)
                    && sharedPref.getString(Constants.SHARED_PREF_NIGHTSCOUT_TOKEN, "").isNullOrEmpty()
                    && sharedPref.getString(Constants.SHARED_PREF_NIGHTSCOUT_URL, "")!!.trim().trimEnd('/') == GlucoseDataReceiver.JUGGLUCO_WEBSERVER
                ) {
                    val sharedGlucosePref = context.getSharedPreferences(Constants.GLUCODATA_BROADCAST_ACTION, MODE_PRIVATE)
                    if(DataSource.fromIndex(sharedGlucosePref.getInt(Constants.EXTRA_SOURCE_INDEX, DataSource.NONE.ordinal)) == DataSource.JUGGLUCO) {
                        webServer = true
                        apiSecret = sharedPref.getString(Constants.SHARED_PREF_NIGHTSCOUT_SECRET, "")?:""
                    }
                }
                Log.i(LOG_ID, "Using Juggluco webserver: $webServer")
                sharedPref.edit {
                    putBoolean(Constants.SHARED_PREF_SOURCE_JUGGLUCO_WEBSERVER_ENABLED, webServer)
                    putBoolean(
                        Constants.SHARED_PREF_SOURCE_JUGGLUCO_WEBSERVER_IOB_SUPPORT,
                        webServer
                    )
                    if (webServer) {
                        putString(
                            Constants.SHARED_PREF_SOURCE_JUGGLUCO_WEBSERVER_API_SECRET,
                            apiSecret
                        )
                        putBoolean(Constants.SHARED_PREF_NIGHTSCOUT_ENABLED, false)
                    }
                }
            }
        }

        fun getNotification(context: Context): Notification {
            Channels.createNotificationChannel(context, ChannelType.ANDROID_AUTO_FOREGROUND)

            val pendingIntent = PackageUtils.getAppIntent(context, MainActivity::class.java, 11)

            val notificationBuilder =  Notification.Builder(context, ChannelType.ANDROID_AUTO_FOREGROUND.channelId)
                .setContentTitle(context.resources.getString(CR.string.gda_foreground_info))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setAutoCancel(false)
                .setCategory(Notification.CATEGORY_STATUS)
                .setVisibility(Notification.VISIBILITY_PUBLIC)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                notificationBuilder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE) // Recommended for Android 14
            }

            return notificationBuilder.build()
        }

        private fun startService(context: Context, foreground: Boolean, startup: Boolean = false) {
            try {
                Log.d(LOG_ID, "Starting service for foreground: $foreground - isForegroundService: $isForegroundService")
                val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, MODE_PRIVATE)
                val foregroundConfig = sharedPref.getBoolean(Constants.SHARED_PREF_FOREGROUND_SERVICE, false)
                val isForeground = foreground || foregroundConfig
                if(startup || isForeground != isForegroundService) {
                    Log.i(LOG_ID, "Starting service for foreground: $isForeground - isForegroundService: $isForegroundService")
                    val serviceIntent = Intent(context, GlucoDataServiceAuto::class.java)
                    serviceIntent.putExtra(Constants.SHARED_PREF_FOREGROUND_SERVICE, isForeground)
                    if (isForeground)
                        context.startForegroundService(serviceIntent)
                    else
                        context.startService(serviceIntent)
                }
                CarMediaBrowserService.setForeground(context, foregroundConfig)
            } catch (exc: Exception) {
                Log.e(LOG_ID, "startService exception: " + exc.message.toString() + "\n" + exc.stackTraceToString())
            }
        }

        fun setForeground(context: Context, foreground: Boolean) {
            try {
                Log.i(LOG_ID, "setForeground called " + foreground)
                if (isForegroundService != foreground) {
                    startService(context, foreground)
                }
            } catch (exc: Exception) {
                Log.e(LOG_ID, "setForeground exception: " + exc.message.toString() + "\n" + exc.stackTraceToString())
            }
        }

        fun start(context: Context) {
            try {
                if(!running) {
                    init(context)
                    Log.i(LOG_ID, "starting")
                    CarMediaBrowserService.enable()
                    CarMediaPlayer.enable(context)
                    CarNotification.enable(context)
                    startDataSync()
                    setForeground(context, true)
                    running = true
                }
            } catch (exc: Exception) {
                Log.e(LOG_ID, "start exception: " + exc.message.toString() + "\n" + exc.stackTraceToString())
            }
        }

        fun stop(context: Context) {
            try {
                if(!connected && running) {
                    Log.i(LOG_ID, "stopping")
                    CarMediaBrowserService.disable()
                    CarMediaPlayer.disable(context)
                    CarNotification.disable(context)
                    stopDataSync(context)
                    setForeground(context, false)
                    running = false
                }
            } catch (exc: Exception) {
                Log.e(LOG_ID, "stop exception: " + exc.message.toString() + "\n" + exc.stackTraceToString())
            }
        }

        fun startDataSync(force: Boolean = false) {
            try {
                Log.i(LOG_ID, "starting datasync - count=$dataSyncCount - context: ${GlucoDataService.context} - force: $force")
                if ((dataSyncCount == 0 || force) && GlucoDataService.context != null) {
                    dbAccess.deleteOldValues(System.currentTimeMillis()-Constants.DB_MAX_DATA_GDA_TIME_MS)
                    updateSourceReceiver(GlucoDataService.context!!)
                    broadcastServiceAPI.init()
                    TimeTaskService.run(GlucoDataService.context!!)
                    SourceTaskService.run(GlucoDataService.context!!)
                    sendStateBroadcast(GlucoDataService.context!!, true)
                    if(ReceiveData.source == DataSource.JUGGLUCO || ReceiveData.source == DataSource.NONE) {
                        GlucoseDataReceiver.checkHandleWebServerRequests(GlucoDataService.context!!, true)
                    }
                    Log.i(LOG_ID, "Datasync started")
                }
                if(!force)
                    dataSyncCount++
            } catch (exc: Exception) {
                Log.e(LOG_ID, "startDataSync exception: " + exc.message.toString() + "\n" + exc.stackTraceToString())
            }
        }

        fun stopDataSync(context: Context) {
            try {
                dataSyncCount--
                Log.i(LOG_ID, "stopping datasync - count=$dataSyncCount")
                if (dataSyncCount == 0 && GlucoDataService.context != null) {
                    unregisterSourceReceiver(GlucoDataService.context!!)
                    broadcastServiceAPI.close(context)
                    sendStateBroadcast(context, false)
                    BackgroundWorker.stopAllWork(context)
                    Log.i(LOG_ID, "Datasync stopped")
                }
            } catch (exc: Exception) {
                Log.e(LOG_ID, "stopDataSync exception: " + exc.message.toString() + "\n" + exc.stackTraceToString())
            }
        }

        private fun onConnectionStateUpdated(connectionState: Int) {
            try {
                val message = when(connectionState) {
                    CarConnection.CONNECTION_TYPE_NOT_CONNECTED -> "Not connected to a head unit"
                    CarConnection.CONNECTION_TYPE_NATIVE -> "Connected to Android Automotive OS"
                    CarConnection.CONNECTION_TYPE_PROJECTION -> "Connected to Android Auto"
                    else -> "Unknown car connection type"
                }
                Log.v(LOG_ID, "onConnectionStateUpdated: " + message + " (" + connectionState.toString() + ")")
                if (init) {
                    if (connectionState == CarConnection.CONNECTION_TYPE_NOT_CONNECTED)  {
                        if(car_connected) {
                            Log.i(LOG_ID, "Exited Car Mode")
                            car_connected = false
                            stop(GlucoDataService.context!!)
                        }
                    } else {
                        if(!car_connected) {
                            Log.i(LOG_ID, "Entered Car Mode")
                            car_connected = true
                            start(GlucoDataService.context!!)
                        }
                    }
                    InternalNotifier.notify(GlucoDataService.context!!, NotifySource.CAR_CONNECTION, null)
                }
            } catch (exc: Exception) {
                Log.e(LOG_ID, "onConnectionStateUpdated exception: " + exc.message.toString() + "\n" + exc.stackTraceToString() )
            }
        }

        private fun sendStateBroadcast(context: Context, enabled: Boolean) {
            try {
                val duration = if(enabled) GlucoDataService.sharedPref?.getInt(Constants.SHARED_PREF_GRAPH_BITMAP_DURATION, 2)?:2 else 0
                Log.i(LOG_ID, "Sending state broadcast for state: $enabled (duration: $duration) - to ${Constants.PACKAGE_GLUCODATAHANDLER}")
                val intent = Intent(Constants.GLUCODATAAUTO_STATE_ACTION)
                intent.setPackage(Constants.PACKAGE_GLUCODATAHANDLER)
                intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                intent.putExtra(Constants.GLUCODATAAUTO_STATE_EXTRA, enabled)
                if(enabled)
                    intent.putExtra(Constants.EXTRA_GRAPH_DURATION_HOURS, duration)
                context.sendBroadcast(intent)
            } catch (exc: Exception) {
                Log.e(LOG_ID, "sendStateBroadcast exception: " + exc.toString())
            }
        }

        fun updateSourceReceiver(context: Context, key: String? = null) {
            Log.d(LOG_ID, "Register receiver for $key")
            try {
                val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, MODE_PRIVATE)
                if(key.isNullOrEmpty() || key == Constants.SHARED_PREF_SOURCE_JUGGLUCO_ENABLED) {
                    if (sharedPref.getBoolean(Constants.SHARED_PREF_SOURCE_JUGGLUCO_ENABLED, true)) {
                        if(glucoDataReceiver == null) {
                            glucoDataReceiver = GlucoDataReceiver()
                            if(!GlucoDataService.registerReceiver(context, glucoDataReceiver!!, IntentFilter("glucodata.Minute")))
                                glucoDataReceiver = null
                        }
                    } else if (glucoDataReceiver != null) {
                        GlucoDataService.unregisterReceiver(context, glucoDataReceiver)
                        glucoDataReceiver = null
                    }
                }

                if(key.isNullOrEmpty() || key == Constants.SHARED_PREF_SOURCE_XDRIP_ENABLED) {
                    if (sharedPref.getBoolean(Constants.SHARED_PREF_SOURCE_XDRIP_ENABLED, true)) {
                        if(xDripReceiver == null) {
                            xDripReceiver = XDripReceiver()
                            if(!GlucoDataService.registerReceiver(context, xDripReceiver!!, IntentFilter("com.eveningoutpost.dexdrip.BgEstimate")))
                                xDripReceiver = null
                        }
                    } else if (xDripReceiver != null) {
                        GlucoDataService.unregisterReceiver(context, xDripReceiver)
                        xDripReceiver = null
                    }
                }

                if(key.isNullOrEmpty() || key == Constants.SHARED_PREF_SOURCE_AAPS_ENABLED) {
                    if (sharedPref.getBoolean(Constants.SHARED_PREF_SOURCE_AAPS_ENABLED, true)) {
                        if(aapsReceiver == null) {
                            aapsReceiver = AAPSReceiver()
                            if(!GlucoDataService.registerReceiver(context, aapsReceiver!!, IntentFilter(Intents.AAPS_BROADCAST_ACTION)))
                                aapsReceiver = null
                        }
                    } else if (aapsReceiver != null) {
                        GlucoDataService.unregisterReceiver(context, aapsReceiver)
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
                            if(!GlucoDataService.registerReceiver(context, dexcomReceiver!!, dexcomFilter))
                                dexcomReceiver = null
                        }
                    } else if (dexcomReceiver != null) {
                        GlucoDataService.unregisterReceiver(context, dexcomReceiver)
                        dexcomReceiver = null
                    }
                }

                if(key.isNullOrEmpty() || key == Constants.SHARED_PREF_SOURCE_EVERSENSE_ENABLED) {
                    if (sharedPref.getBoolean(Constants.SHARED_PREF_SOURCE_EVERSENSE_ENABLED, true)) {
                        if(nsEmulatorReceiver == null) {
                            nsEmulatorReceiver = NsEmulatorReceiver()
                            if(!GlucoDataService.registerReceiver(context, nsEmulatorReceiver!!, IntentFilter(Intents.NS_EMULATOR_BROADCAST_ACTION)))
                                nsEmulatorReceiver = null
                        }
                    } else if (nsEmulatorReceiver != null) {
                        GlucoDataService.unregisterReceiver(context, nsEmulatorReceiver)
                        nsEmulatorReceiver = null
                    }
                }

                if(key.isNullOrEmpty() || key == Constants.SHARED_PREF_SOURCE_DIABOX_ENABLED) {
                    if (sharedPref.getBoolean(Constants.SHARED_PREF_SOURCE_DIABOX_ENABLED, true)) {
                        if(diaboxReceiver == null) {
                            diaboxReceiver = DiaboxReceiver()
                            if(!GlucoDataService.registerReceiver(context, diaboxReceiver!!, IntentFilter(Intents.DIABOX_BROADCAST_ACTION)))
                                diaboxReceiver = null
                        }
                    } else if (diaboxReceiver != null) {
                        GlucoDataService.unregisterReceiver(context, diaboxReceiver)
                        diaboxReceiver = null
                    }
                }

                if(key.isNullOrEmpty() || key == Constants.SHARED_PREF_SOURCE_LIBRE_PATCHED_ENABLED) {
                    if (sharedPref.getBoolean(Constants.SHARED_PREF_SOURCE_LIBRE_PATCHED_ENABLED, true)) {
                        if(librePatchedReceiver == null) {
                            librePatchedReceiver = LibrePatchedReceiver()
                            val filter = IntentFilter()
                            filter.addAction(Constants.XDRIP_ACTION_GLUCOSE_READING)
                            filter.addAction(Constants.XDRIP_ACTION_SENSOR_ACTIVATE)
                            if(!GlucoDataService.registerReceiver(context, librePatchedReceiver!!, filter))
                                librePatchedReceiver = null
                        }
                    } else if (librePatchedReceiver != null) {
                        GlucoDataService.unregisterReceiver(context, librePatchedReceiver)
                        librePatchedReceiver = null
                    }
                }

                if (key.isNullOrEmpty() || key == Constants.SHARED_PREF_SOURCE_NOTIFICATION_ENABLED) {
                    updateNotificationReceiver(sharedPref, context)
                }

            } catch (exc: Exception) {
                Log.e(LOG_ID, "registerSourceReceiver exception: " + exc.toString())
            }
        }

        fun unregisterSourceReceiver(context: Context) {
            try {
                Log.d(LOG_ID, "Unregister receiver")
                if (glucoDataReceiver != null) {
                    GlucoDataService.unregisterReceiver(context, glucoDataReceiver)
                    glucoDataReceiver = null
                }
                if (xDripReceiver != null) {
                    GlucoDataService.unregisterReceiver(context, xDripReceiver)
                    xDripReceiver = null
                }
                if (dexcomReceiver != null) {
                    GlucoDataService.unregisterReceiver(context, dexcomReceiver)
                    dexcomReceiver = null
                }
                if (nsEmulatorReceiver != null) {
                    GlucoDataService.unregisterReceiver(context, nsEmulatorReceiver)
                    nsEmulatorReceiver = null
                }
                if (aapsReceiver != null) {
                    GlucoDataService.unregisterReceiver(context, aapsReceiver)
                    aapsReceiver = null
                }
                if (diaboxReceiver != null) {
                    GlucoDataService.unregisterReceiver(context, diaboxReceiver)
                    diaboxReceiver = null
                }
                if (librePatchedReceiver != null) {
                    GlucoDataService.unregisterReceiver(context, librePatchedReceiver)
                    librePatchedReceiver = null
                }
                GlucoDataService.unregisterSourceReceiver(context)
            } catch (exc: Exception) {
                Log.e(LOG_ID, "unregisterSourceReceiver exception: " + exc.toString())
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            Log.i(LOG_ID, "onStartCommand called with intent ${Utils.dumpBundle(intent?.extras)}, flags $flags and startId $startId")
            GdhUncaughtExecptionHandler.init()
            val sharedPref = getSharedPreferences(Constants.SHARED_PREF_TAG, MODE_PRIVATE)
            val isForeground = (if(intent != null) intent.getBooleanExtra(Constants.SHARED_PREF_FOREGROUND_SERVICE, false) else false) || sharedPref.getBoolean(Constants.SHARED_PREF_FOREGROUND_SERVICE, false)
            if (isForeground && !isForegroundService) {
                Log.i(LOG_ID, "Starting service in foreground!")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                    startForeground(NOTIFICATION_ID, getNotification(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                else
                    startForeground(NOTIFICATION_ID, getNotification(this))
                isForegroundService = true
            } else if ( isForegroundService && !isForeground ) {
                isForegroundService = false
                Log.i(LOG_ID, "Stopping service in foreground!")
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
            super.onStartCommand(intent, flags, startId)
            GlucoDataService.context = applicationContext
            ReceiveData.initData(applicationContext, false)
            TextToSpeechUtils.initTextToSpeech(this)
            sharedPref.registerOnSharedPreferenceChangeListener(this)
            GlucoDataService.patientName = GlucoDataService.sharedPref!!.getString(Constants.PATIENT_NAME, "")
            CarConnection(applicationContext).type.observeForever(GlucoDataServiceAuto::onConnectionStateUpdated)
            InternalNotifier.notify(this, NotifySource.SERVICE_STARTED, null)
            if(dataSyncCount > 0)
                startDataSync(true)
            else
                BackgroundWorker.stopAllWork(this)  // stop all running threads, if datasync is not active
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onStartCommand exception: " + exc.message.toString() + "\n" + exc.stackTraceToString())
        }

        return START_STICKY  // keep alive
    }

    override fun onDestroy() {
        Log.v(LOG_ID, "onDestroy called")
        val sharedPref = getSharedPreferences(Constants.SHARED_PREF_TAG, MODE_PRIVATE)
        sharedPref.unregisterOnSharedPreferenceChangeListener(this)
        TextToSpeechUtils.destroyTextToSpeech(this)
        Log.close(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.v(LOG_ID, "onBind called with intent " + intent)
        return null
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        try {
            Log.d(LOG_ID, "onSharedPreferenceChanged called with key $key - dataSyncCount = $dataSyncCount")
            when(key) {
                Constants.SHARED_PREF_SOURCE_JUGGLUCO_ENABLED,
                Constants.SHARED_PREF_SOURCE_XDRIP_ENABLED,
                Constants.SHARED_PREF_SOURCE_AAPS_ENABLED,
                Constants.SHARED_PREF_SOURCE_BYODA_ENABLED,
                Constants.SHARED_PREF_SOURCE_EVERSENSE_ENABLED,
                Constants.SHARED_PREF_SOURCE_DIABOX_ENABLED,
                Constants.SHARED_PREF_SOURCE_NOTIFICATION_ENABLED -> {
                    if(dataSyncCount>0) {
                        updateSourceReceiver(this, key)
                    } else if(key == Constants.SHARED_PREF_SOURCE_NOTIFICATION_ENABLED && sharedPreferences.getBoolean(Constants.SHARED_PREF_SOURCE_NOTIFICATION_ENABLED, false)) {
                        checkNotificationReceiverPermission(this, true)
                    }
                }
                Constants.PATIENT_NAME -> {
                    GlucoDataService.patientName = sharedPreferences.getString(Constants.PATIENT_NAME, "")
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString())
        }
    }

}