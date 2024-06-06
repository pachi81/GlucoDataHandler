package de.michelinside.glucodataauto

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.car.app.connection.CarConnection
import de.michelinside.glucodataauto.android_auto.CarMediaBrowserService
import de.michelinside.glucodataauto.android_auto.CarNotification
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.notification.ChannelType
import de.michelinside.glucodatahandler.common.notification.Channels
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.tasks.BackgroundWorker
import de.michelinside.glucodatahandler.common.tasks.SourceTaskService
import de.michelinside.glucodatahandler.common.tasks.TimeTaskService
import de.michelinside.glucodatahandler.common.utils.PackageUtils

class GlucoDataServiceAuto: Service() {
    companion object {
        private const val LOG_ID = "GDH.AA.GlucoDataServiceAuto"
        private var isForegroundService = false
        const val NOTIFICATION_ID = 666
        private var car_connected = false
        private var running = false
        private var init = false
        private var dataSyncCount = 0
        val connected: Boolean get() = car_connected || CarMediaBrowserService.active
        fun init(context: Context) {
            Log.v(LOG_ID, "init called: init=$init")
            if(!init) {
                GlucoDataService.context = context
                CarNotification.initNotification(context)
                startService(context, false)
                init = true
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
                    CarNotification.enable(context)
                    startDataSync(context)
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
                    CarNotification.disable(context)
                    stopDataSync(context)
                    setForeground(context, false)
                    running = false
                }
            } catch (exc: Exception) {
                Log.e(LOG_ID, "stop exception: " + exc.message.toString() + "\n" + exc.stackTraceToString())
            }
        }

        fun startDataSync(context: Context) {
            try {
                Log.i(LOG_ID, "starting datasync - count=$dataSyncCount")
                if (dataSyncCount == 0) {
                    TimeTaskService.run(context)
                    SourceTaskService.run(context)
                    sendStateBroadcast(context, true)
                    Log.i(LOG_ID, "Datasync started")
                }
                dataSyncCount++
            } catch (exc: Exception) {
                Log.e(LOG_ID, "startDataSync exception: " + exc.message.toString() + "\n" + exc.stackTraceToString())
            }
        }

        fun stopDataSync(context: Context) {
            try {
                dataSyncCount--
                Log.i(LOG_ID, "stopping datasync - count=$dataSyncCount")
                if (dataSyncCount == 0) {
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
                Log.d(LOG_ID, "Sending state broadcast for state: " + enabled)
                val intent = Intent(Constants.GLUCODATAAUTO_STATE_ACTION)
                intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                intent.putExtra(Constants.GLUCODATAAUTO_STATE_EXTRA, enabled)
                context.sendBroadcast(intent)
            } catch (exc: Exception) {
                Log.e(LOG_ID, "sendStateBroadcast exception: " + exc.toString())
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            Log.d(LOG_ID, "onStartCommand called")
            super.onStartCommand(intent, flags, startId)
            GlucoDataService.context = applicationContext
            ReceiveData.initData(applicationContext)
            CarNotification.initNotification(this)
            val sharedPref = getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
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
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onStartCommand exception: " + exc.message.toString() + "\n" + exc.stackTraceToString())
        }

        return START_STICKY  // keep alive
    }

    override fun onDestroy() {
        Log.v(LOG_ID, "onDestroy called")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.v(LOG_ID, "onBind called with intent " + intent)
        return null
    }

    private fun getNotification(): Notification {
        Channels.createNotificationChannel(this, ChannelType.ANDROID_AUTO_FOREGROUND)

        val pendingIntent = PackageUtils.getAppIntent(this, MainActivity::class.java, 11, false)

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

}