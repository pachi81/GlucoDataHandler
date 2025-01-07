package de.michelinside.glucodatahandler.android_auto

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.car.app.connection.CarConnection
import de.michelinside.glucodatahandler.common.*
import de.michelinside.glucodatahandler.common.notification.AlarmHandler
import de.michelinside.glucodatahandler.common.notifier.*
import de.michelinside.glucodatahandler.common.tasks.ElapsedTimeTask
import de.michelinside.glucodatahandler.common.utils.PackageUtils
import de.michelinside.glucodatahandler.common.utils.Utils
import de.michelinside.glucodatahandler.tasker.setAndroidAutoConnectionState

object CarModeReceiver {
    private const val LOG_ID = "GDH.CarModeReceiver"
    private var init = false
    private var car_connected = false
    private var gda_enabled = false
    val connected: Boolean get() {  // connected to GlucoDataAuto
        if (!car_connected)
            return gda_enabled
        return car_connected
    }

    val AA_connected: Boolean get() {   // connected to Android Auto
        return car_connected
    }


    class GDAReceiver: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.i(LOG_ID, "onReceive called for intent " + intent + ": " + Utils.dumpBundle(intent.extras))
            if(!PackageUtils.isGlucoDataAutoAvailable(context)) {
                Log.i(LOG_ID, "GlucoDataAuto not available, but package received -> update packages")
                PackageUtils.updatePackages(context)
            }
            gda_enabled = intent.getBooleanExtra(Constants.GLUCODATAAUTO_STATE_EXTRA, false)
            if(!car_connected && gda_enabled) {
                InternalNotifier.notify(context, NotifySource.CAR_CONNECTION, null)
            }
        }

    }
    private val gdaReceiver = GDAReceiver()

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun init(context: Context) {
        try {
            if(!init) {
                Log.v(LOG_ID, "init called")
                CarConnection(context).type.observeForever(CarModeReceiver::onConnectionStateUpdated)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    GlucoDataService.context!!.registerReceiver(gdaReceiver, IntentFilter(Constants.GLUCODATAAUTO_STATE_ACTION),
                        Context.RECEIVER_EXPORTED or Context.RECEIVER_VISIBLE_TO_INSTANT_APPS)
                } else {
                    GlucoDataService.context!!.registerReceiver(gdaReceiver, IntentFilter(Constants.GLUCODATAAUTO_STATE_ACTION))
                }
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
                GlucoDataService.context!!.unregisterReceiver(gdaReceiver)
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
            val curState = AA_connected
            if (connectionState == CarConnection.CONNECTION_TYPE_NOT_CONNECTED)  {
                if (car_connected) {
                    Log.i(LOG_ID, "Exited Car Mode")
                    car_connected = false
                    GlucoDataService.context?.setAndroidAutoConnectionState(false)
                }
            } else if(!car_connected){
                Log.i(LOG_ID, "Entered Car Mode")
                car_connected = true
                GlucoDataService.context?.setAndroidAutoConnectionState(true)
            }
            if (curState != AA_connected)
                InternalNotifier.notify(GlucoDataService.context!!, NotifySource.CAR_CONNECTION, null)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onConnectionStateUpdated exception: " + exc.message.toString() )
        }
    }

    fun sendToGlucoDataAuto(context: Context, withSettings: Boolean = false) {
        try {
            val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            if (connected && sharedPref.getBoolean(Constants.SHARED_PREF_SEND_TO_GLUCODATAAUTO, true) && PackageUtils.isGlucoDataAutoAvailable(context)) {
                val extras = ReceiveData.createExtras(false)
                if(extras != null) {
                    Log.i(LOG_ID, "send to ${Constants.PACKAGE_GLUCODATAAUTO}")
                    val intent = Intent(Intents.GLUCODATA_ACTION)
                    intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    if(withSettings && sharedPref.getBoolean(Constants.SHARED_PREF_SEND_PREF_TO_GLUCODATAAUTO, true)) {
                        val settings = GlucoDataService.getSettings()
                        settings.putBoolean(Constants.SHARED_PREF_RELATIVE_TIME, ElapsedTimeTask.relativeTime)
                        extras.putBundle(Constants.SETTINGS_BUNDLE, settings)
                        extras.putBundle(Constants.ALARM_SETTINGS_BUNDLE, AlarmHandler.getSettings(true))
                    }
                    intent.putExtras(extras)
                    intent.setPackage(Constants.PACKAGE_GLUCODATAAUTO)
                    context.sendBroadcast(intent)
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "sendToGlucoDataAuto exception: " + exc.message.toString() )
        }
    }
}
