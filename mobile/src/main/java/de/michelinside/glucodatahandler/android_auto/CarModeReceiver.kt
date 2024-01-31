package de.michelinside.glucodatahandler.android_auto

import android.content.Context
import android.util.Log
import androidx.car.app.connection.CarConnection
import de.michelinside.glucodatahandler.common.*
import de.michelinside.glucodatahandler.common.notifier.*
import de.michelinside.glucodatahandler.tasker.setAndroidAutoConnectionState

object CarModeReceiver {
    private const val LOG_ID = "GDH.CarModeReceiver"
    private var init = false
    private var car_connected = false
    val connected: Boolean get() = car_connected

    fun init(context: Context) {
        try {
            if(!init) {
                Log.v(LOG_ID, "init called")
                CarConnection(context).type.observeForever(CarModeReceiver::onConnectionStateUpdated)
                init = true
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "init exception: " + exc.message.toString() )
        }
    }

    fun cleanup(context: Context) {
        try {
            if (init) {
                Log.v(LOG_ID, "cleanup called")
                CarConnection(context).type.removeObserver(CarModeReceiver::onConnectionStateUpdated)
                init = false
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "init exception: " + exc.message.toString())
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
            Log.d(LOG_ID, "onConnectionStateUpdated: " + message + " (" + connectionState.toString() + ")")
            if (connectionState == CarConnection.CONNECTION_TYPE_NOT_CONNECTED)  {
                Log.i(LOG_ID, "Exited Car Mode")
                car_connected = false
                GlucoDataService.context?.setAndroidAutoConnectionState(false)
            } else {
                Log.i(LOG_ID, "Entered Car Mode")
                car_connected = true
                GlucoDataService.context?.setAndroidAutoConnectionState(true)
            }
            InternalNotifier.notify(GlucoDataService.context!!, NotifySource.CAR_CONNECTION, null)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onConnectionStateUpdated exception: " + exc.message.toString() )
        }
    }
}
