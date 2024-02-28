package de.michelinside.glucodataauto

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.car.app.connection.CarConnection
import de.michelinside.glucodataauto.android_auto.CarMediaBrowserService
import de.michelinside.glucodataauto.android_auto.CarNotification
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.tasks.BackgroundWorker
import de.michelinside.glucodatahandler.common.tasks.TimeTaskService

object GlucoDataServiceAuto {
    private const val LOG_ID = "GDH.AA.GlucoDataServiceAuto"
    private var car_connected = false
    private var running = false
    private var init = false
    val connected: Boolean get() = car_connected || CarMediaBrowserService.active
    fun init(context: Context) {
        if(!init) {
            Log.v(LOG_ID, "init called")
            GlucoDataService.context = context
            TimeTaskService.useWorker = true
            ReceiveData.initData(context)
            CarNotification.initNotification(context)
            CarConnection(context.applicationContext).type.observeForever(GlucoDataServiceAuto::onConnectionStateUpdated)
            init = true
        }
    }

    fun start(context: Context) {
        if(!running) {
            Log.i(LOG_ID, "starting")
            CarNotification.enable(context)
            TimeTaskService.run(context)
            sendStateBroadcast(context, true)
            running = true
        }
    }

    fun stop(context: Context) {
        if(!connected && running) {
            Log.i(LOG_ID, "stopping")
            CarNotification.disable(context)
            sendStateBroadcast(context, false)
            BackgroundWorker.stopAllWork(context)
            running = false
        }
    }

    fun onConnectionStateUpdated(connectionState: Int) {
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
        Log.d(LOG_ID, "Sending state broadcast for state: " + enabled)
        val intent = Intent(Constants.GLUCODATAAUTO_STATE_ACTION)
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        intent.putExtra(Constants.GLUCODATAAUTO_STATE_EXTRA, enabled)
        context.sendBroadcast(intent)
    }
}