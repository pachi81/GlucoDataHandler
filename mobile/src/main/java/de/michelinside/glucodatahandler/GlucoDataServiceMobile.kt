package de.michelinside.glucodatahandler

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import de.michelinside.glucodatahandler.android_auto.CarModeReceiver
import de.michelinside.glucodatahandler.common.*
import de.michelinside.glucodatahandler.common.notifier.*
import de.michelinside.glucodatahandler.widget.FloatingWidget
import de.michelinside.glucodatahandler.widget.GlucoseBaseWidget

class GlucoDataServiceMobile: GlucoDataService(AppSource.PHONE_APP), NotifierInterface {
    private val LOG_ID = "GlucoDataHandler.GlucoDataServiceMobile"
    private lateinit var floatingWidget: FloatingWidget
    init {
        Log.d(LOG_ID, "init called")
        InternalNotifier.addNotifier(TaskerDataReceiver, mutableSetOf(NotifyDataSource.BROADCAST,NotifyDataSource.MESSAGECLIENT))
    }

    companion object {
        fun start(context: Context) {
            start(context, GlucoDataServiceMobile::class.java)
        }
    }

    override fun onCreate() {
        try {
            Log.d(LOG_ID, "onCreate called")
            super.onCreate()
            floatingWidget = FloatingWidget(this)
            PermanentNotification.create(applicationContext)
            CarModeReceiver.initNotification(applicationContext)
            GlucoseBaseWidget.updateWidgets(applicationContext)
            floatingWidget.create()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreate exception: " + exc.message.toString() )
        }
    }

    override fun getNotification(): Notification {
        return PermanentNotification.getNotification(
            !sharedPref!!.getBoolean(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_EMPTY, false),
            Constants.SHARED_PREF_PERMANENT_NOTIFICATION_ICON
        )
    }

    override fun onDestroy() {
        try {
            Log.d(LOG_ID, "onDestroy called")
            PermanentNotification.destroy()
            CarModeReceiver.cleanupNotification(applicationContext)
            floatingWidget.destroy()
            super.onDestroy()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onDestroy exception: " + exc.message.toString() )
        }
    }

    override fun OnNotifyData(context: Context, dataSource: NotifyDataSource, extras: Bundle?) {
        try {
            Log.d(LOG_ID, "OnNotifyData called for source " + dataSource.toString())
            start(context)
            super.OnNotifyData(context, dataSource, extras)
            if (extras != null) {
                if (dataSource == NotifyDataSource.MESSAGECLIENT || dataSource == NotifyDataSource.BROADCAST) {
                    val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
                    if (sharedPref.getBoolean(Constants.SHARED_PREF_SEND_TO_XDRIP, false)) {
                        Log.d(LOG_ID, "Send bg value to xDrip")
                        val intent = Intent()
                        intent.action = Constants.XDRIP_ACTION_GLUCOSE_READING
                        // always sends time as start time, because it is only set, if the sensorId have changed!
                        val sensor = Bundle()
                        sensor.putLong("sensorStartTime", ReceiveData.time)  // use last received time as start time
                        val currentSensor = Bundle()
                        currentSensor.putBundle("currentSensor", sensor)
                        intent.putExtra("sas", currentSensor)
                        val bleManager = Bundle()
                        bleManager.putString("sensorSerial", ReceiveData.sensorID)
                        intent.putExtra("bleManager", bleManager)
                        intent.putExtra("glucose", ReceiveData.rawValue.toDouble())
                        intent.putExtra("timestamp", ReceiveData.time)
                        intent.setPackage("com.eveningoutpost.dexdrip")
                        context.sendBroadcast(intent)
                    }

                    // forward every broadcast, because the receiver can be defined in Juggluco, too
                    if (sharedPref.getBoolean(Constants.SHARED_PREF_SEND_TO_GLUCODATA_AOD, false)) {
                        var receivers = sharedPref.getStringSet(Constants.SHARED_PREF_GLUCODATA_RECEIVERS, HashSet<String>())
                        Log.d(LOG_ID, "Resend Glucodata Broadcast to " + receivers?.size.toString() + " receivers")
                        if (receivers == null || receivers.size == 0) {
                            receivers = setOf("")
                        }
                        for( receiver in receivers ) {
                            val intent = Intent()
                            intent.action = Constants.GLUCODATA_BROADCAST_ACTION
                            intent.putExtras(extras)
                            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                            if (!receiver.isEmpty()) {
                                intent.setPackage(receiver)
                                Log.d(LOG_ID, "Send glucodata broadcast to " + receiver.toString())
                            } else {
                                Log.d(LOG_ID, "Send global glucodata broadcast")
                            }
                            context.sendBroadcast(intent)
                        }
                    }
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: " + exc.message.toString() )
        }
    }
}