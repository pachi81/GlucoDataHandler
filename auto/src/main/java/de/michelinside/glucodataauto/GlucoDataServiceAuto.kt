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
import android.util.Log
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
import de.michelinside.glucodataauto.receiver.NsEmulatorReceiver
import de.michelinside.glucodataauto.receiver.XDripReceiver
import de.michelinside.glucodatahandler.common.Intents
import de.michelinside.glucodatahandler.common.tasks.BackgroundWorker
import de.michelinside.glucodatahandler.common.tasks.SourceTaskService
import de.michelinside.glucodatahandler.common.tasks.TimeTaskService
import de.michelinside.glucodatahandler.common.utils.PackageUtils
import de.michelinside.glucodatahandler.common.utils.TextToSpeechUtils

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
        private var patient_name: String? = null
        val patientName: String? get() = patient_name

        val connected: Boolean get() = car_connected || CarMediaBrowserService.active

        fun init(context: Context) {
            Log.v(LOG_ID, "init called: init=$init")
            if(!init) {
                GlucoDataService.appSource = AppSource.AUTO_APP
                migrateSettings(context)
                CarNotification.initNotification(context)
                startService(context, false)
                init = true
            }
        }

        private fun migrateSettings(context: Context) {
            Log.v(LOG_ID, "migrateSettings called")
            GlucoDataService.migrateSettings(context)
            val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            if(!sharedPref.contains(Constants.SHARED_PREF_NIGHTSCOUT_IOB_COB)) {
                with(sharedPref.edit()) {
                    putBoolean(Constants.SHARED_PREF_NIGHTSCOUT_IOB_COB, false)
                    apply()
                }
            }
            if(Constants.IS_SECOND && !sharedPref.contains(Constants.PATIENT_NAME)) {
                with(sharedPref.edit()) {
                    putString(Constants.PATIENT_NAME, "SECOND")
                    apply()
                }
            }
        }

        private fun startService(context: Context, foreground: Boolean) {
            try {
                val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
                val isForeground = foreground || sharedPref.getBoolean(Constants.SHARED_PREF_FOREGROUND_SERVICE, false)
                val serviceIntent = Intent(context, GlucoDataServiceAuto::class.java)
                serviceIntent.putExtra(Constants.SHARED_PREF_FOREGROUND_SERVICE, isForeground)
                if (isForeground)
                    context.startForegroundService(serviceIntent)
                else
                    context.startService(serviceIntent)
            } catch (exc: Exception) {
                Log.e(LOG_ID, "startService exception: " + exc.message.toString() + "\n" + exc.stackTraceToString())
            }
        }

        fun setForeground(context: Context, foreground: Boolean) {
            try {
                Log.v(LOG_ID, "setForeground called " + foreground)
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
                    updateSourceReceiver(GlucoDataService.context!!)
                    TimeTaskService.run(GlucoDataService.context!!)
                    SourceTaskService.run(GlucoDataService.context!!)
                    sendStateBroadcast(GlucoDataService.context!!, true)
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
                Log.i(LOG_ID, "Sending state broadcast for state: $enabled - to ${Constants.PACKAGE_GLUCODATAHANDLER}")
                val intent = Intent(Constants.GLUCODATAAUTO_STATE_ACTION)
                intent.setPackage(Constants.PACKAGE_GLUCODATAHANDLER)
                intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                intent.putExtra(Constants.GLUCODATAAUTO_STATE_EXTRA, enabled)
                context.sendBroadcast(intent)
            } catch (exc: Exception) {
                Log.e(LOG_ID, "sendStateBroadcast exception: " + exc.toString())
            }
        }

        fun updateSourceReceiver(context: Context, key: String? = null) {
            Log.d(LOG_ID, "Register receiver")
            try {
                val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
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
            } catch (exc: Exception) {
                Log.e(LOG_ID, "unregisterSourceReceiver exception: " + exc.toString())
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            Log.d(LOG_ID, "onStartCommand called")
            GdhUncaughtExecptionHandler.init()
            super.onStartCommand(intent, flags, startId)
            GlucoDataService.context = applicationContext
            ReceiveData.initData(applicationContext)
            CarNotification.initNotification(this)
            TextToSpeechUtils.initTextToSpeech(this)
            val sharedPref = getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            sharedPref.registerOnSharedPreferenceChangeListener(this)
            patient_name = sharedPref.getString(Constants.PATIENT_NAME, "")
            val isForeground = (if(intent != null) intent.getBooleanExtra(Constants.SHARED_PREF_FOREGROUND_SERVICE, false) else false) || sharedPref.getBoolean(Constants.SHARED_PREF_FOREGROUND_SERVICE, false)
            if (isForeground && !isForegroundService) {
                Log.i(LOG_ID, "Starting service in foreground!")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    startForeground(NOTIFICATION_ID, getNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                else
                    startForeground(NOTIFICATION_ID, getNotification())
                isForegroundService = true
            } else if ( isForegroundService && !isForeground ) {
                isForegroundService = false
                Log.i(LOG_ID, "Stopping service in foreground!")
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
            CarConnection(applicationContext).type.observeForever(GlucoDataServiceAuto::onConnectionStateUpdated)
            if(dataSyncCount > 0)
                startDataSync(true)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onStartCommand exception: " + exc.message.toString() + "\n" + exc.stackTraceToString())
        }

        return START_STICKY  // keep alive
    }

    override fun onDestroy() {
        Log.v(LOG_ID, "onDestroy called")
        val sharedPref = getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
        sharedPref.unregisterOnSharedPreferenceChangeListener(this)
        TextToSpeechUtils.destroyTextToSpeech(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.v(LOG_ID, "onBind called with intent " + intent)
        return null
    }

    private fun getNotification(): Notification {
        Channels.createNotificationChannel(this, ChannelType.ANDROID_AUTO_FOREGROUND)

        val pendingIntent = PackageUtils.getAppIntent(this, MainActivity::class.java, 11)

        return Notification.Builder(this, ChannelType.ANDROID_AUTO_FOREGROUND.channelId)
            .setContentTitle(getString(de.michelinside.glucodatahandler.common.R.string.gda_foreground_info))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        try {
            Log.d(LOG_ID, "onSharedPreferenceChanged called with key $key")
            when(key) {
                Constants.SHARED_PREF_SOURCE_JUGGLUCO_ENABLED,
                Constants.SHARED_PREF_SOURCE_XDRIP_ENABLED,
                Constants.SHARED_PREF_SOURCE_AAPS_ENABLED,
                Constants.SHARED_PREF_SOURCE_BYODA_ENABLED,
                Constants.SHARED_PREF_SOURCE_EVERSENSE_ENABLED,
                Constants.SHARED_PREF_SOURCE_DIABOX_ENABLED -> {
                    if(dataSyncCount>0)
                        updateSourceReceiver(this, key)
                }
                Constants.PATIENT_NAME -> {
                    patient_name = sharedPreferences.getString(Constants.PATIENT_NAME, "")
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString())
        }
    }

}