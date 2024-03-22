package de.michelinside.glucodatahandler

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import de.michelinside.glucodatahandler.android_auto.CarModeReceiver
import de.michelinside.glucodatahandler.common.*
import de.michelinside.glucodatahandler.notification.AlarmNotification
import de.michelinside.glucodatahandler.common.notifier.*
import de.michelinside.glucodatahandler.common.receiver.XDripBroadcastReceiver
import de.michelinside.glucodatahandler.common.utils.GlucoDataUtils
import de.michelinside.glucodatahandler.notification.PermanentNotification
import de.michelinside.glucodatahandler.tasker.setWearConnectionState
import de.michelinside.glucodatahandler.watch.WatchDrip
import de.michelinside.glucodatahandler.widget.FloatingWidget
import de.michelinside.glucodatahandler.widget.GlucoseBaseWidget
import de.michelinside.glucodatahandler.widget.LockScreenWallpaper


class GlucoDataServiceMobile: GlucoDataService(AppSource.PHONE_APP), NotifierInterface {
    private lateinit var floatingWidget: FloatingWidget

    init {
        Log.d(LOG_ID, "init called")
        InternalNotifier.addNotifier(this, TaskerDataReceiver, mutableSetOf(NotifySource.BROADCAST,NotifySource.IOB_COB_CHANGE,NotifySource.MESSAGECLIENT,NotifySource.OBSOLETE_VALUE))
    }

    companion object {
        private val LOG_ID = "GDH.GlucoDataServiceMobile"
        fun start(context: Context, force: Boolean = false) {
            Log.v(LOG_ID, "start called")
            start(AppSource.PHONE_APP, context, GlucoDataServiceMobile::class.java, force)
        }

        fun sendLogcatRequest() {
            if(connection != null) {
                Log.d(LOG_ID, "sendLogcatRequest called")
                connection!!.sendMessage(NotifySource.LOGCAT_REQUEST, null, filterReiverId = connection!!.pickBestNodeId())
            }
        }
    }

    override fun onCreate() {
        try {
            Log.d(LOG_ID, "onCreate called")
            super.onCreate()
            val filter = mutableSetOf(
                NotifySource.BROADCAST,
                NotifySource.MESSAGECLIENT,
                NotifySource.OBSOLETE_VALUE, // to trigger re-start for the case of stopped by the system
                NotifySource.CAR_CONNECTION,
                NotifySource.CAPILITY_INFO)
            InternalNotifier.addNotifier(this, this, filter)
            floatingWidget = FloatingWidget(this)
            PermanentNotification.create(applicationContext)
            CarModeReceiver.init(applicationContext)
            GlucoseBaseWidget.updateWidgets(applicationContext)
            WatchDrip.init(applicationContext)
            floatingWidget.create()
            LockScreenWallpaper.create(this)
            AlarmNotification.initNotifications(this)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreate exception: " + exc.message.toString() )
        }
    }

    override fun getNotification(): Notification {
        return PermanentNotification.getNotification(
            !sharedPref!!.getBoolean(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_EMPTY, false),
            Constants.SHARED_PREF_PERMANENT_NOTIFICATION_ICON, true
        )
    }

    override fun onDestroy() {
        try {
            Log.d(LOG_ID, "onDestroy called")
            PermanentNotification.destroy()
            AlarmNotification.destroy(this)
            CarModeReceiver.cleanup(applicationContext)
            WatchDrip.close(applicationContext)
            floatingWidget.destroy()
            LockScreenWallpaper.destroy(this)
            super.onDestroy()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onDestroy exception: " + exc.message.toString() )
        }
    }

    fun sendBroadcast(intent: Intent, receiverPrefKey: String, context: Context, sharedPref: SharedPreferences) {
        try {
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            var receivers = sharedPref.getStringSet(receiverPrefKey, HashSet<String>())
            Log.d(LOG_ID, "Resend " + receiverPrefKey + " Broadcast to " + receivers?.size.toString() + " receivers")
            if (receivers == null || receivers.size == 0) {
                receivers = setOf("")
            }
            for( receiver in receivers ) {
                val sendIntent = intent.clone() as Intent
                if (!receiver.isEmpty()) {
                    sendIntent.setPackage(receiver)
                    Log.d(LOG_ID, "Send broadcast " + receiverPrefKey + " to " + receiver.toString())
                } else {
                    Log.d(LOG_ID, "Send global broadcast " + receiverPrefKey)
                    sendIntent.putExtra(Constants.EXTRA_SOURCE_PACKAGE, context.packageName)
                }
                context.sendBroadcast(sendIntent)
            }
        } catch (ex: Exception) {
            Log.e(LOG_ID, "Exception while sending broadcast for " + receiverPrefKey + ": " + ex)
        }
    }

    private fun sendToBangleJS(context: Context) {
        val send2Bangle = "require(\"Storage\").writeJSON(\"widbgjs.json\", {" +
                "'bg': " + ReceiveData.rawValue.toString() + "," +
                "'bgTimeStamp': " + ReceiveData.time + "," +
                "'bgDirection': '" + GlucoDataUtils.getDexcomLabel(ReceiveData.rate) + "'" +
                "});"

        Log.i(LOG_ID, "Send to bangleJS: " + send2Bangle)
        val sendIntent = Intent("com.banglejs.uart.tx")
        sendIntent.putExtra("line", send2Bangle)
        context.sendBroadcast(sendIntent)
    }

    private fun forwardBroadcast(context: Context, extras: Bundle) {
        Log.v(LOG_ID, "forwardBroadcast called")
        val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
        CarModeReceiver.sendToGlucoDataAuto(context, extras.clone() as Bundle)
        if (sharedPref.getBoolean(Constants.SHARED_PREF_SEND_TO_XDRIP, false)) {
            val intent = Intent(Constants.XDRIP_ACTION_GLUCOSE_READING)
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
            sendBroadcast(intent, Constants.SHARED_PREF_XDRIP_RECEIVERS, context, sharedPref)
        }

        if (sharedPref.getBoolean(Constants.SHARED_PREF_SEND_XDRIP_BROADCAST, false)) {
            val xDripExtras = XDripBroadcastReceiver.createExtras(context)
            if (xDripExtras != null) {
                val intent = Intent(Constants.XDRIP_BROADCAST_ACTION)
                intent.putExtras(xDripExtras)
                sendBroadcast(intent, Constants.SHARED_PREF_XDRIP_BROADCAST_RECEIVERS, context, sharedPref)
            }
        }

        if (sharedPref.getBoolean(Constants.SHARED_PREF_SEND_TO_GLUCODATA_AOD, false)) {
            val intent = Intent(Constants.GLUCODATA_BROADCAST_ACTION)
            intent.putExtras(extras)
            sendBroadcast(intent, Constants.SHARED_PREF_GLUCODATA_RECEIVERS, context, sharedPref)
        }

        if (sharedPref.getBoolean(Constants.SHARED_PREF_SEND_TO_BANGLEJS, false)) {
            sendToBangleJS(context)
        }
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            Log.v(LOG_ID, "OnNotifyData called for source " + dataSource.toString())
            start(context)
            if (dataSource == NotifySource.CAPILITY_INFO) {
                context.setWearConnectionState(WearPhoneConnection.nodesConnected)
            }
            if (dataSource == NotifySource.CAR_CONNECTION && CarModeReceiver.connected) {
                val autoExtras = ReceiveData.createExtras()
                if (autoExtras != null)
                    CarModeReceiver.sendToGlucoDataAuto(context, autoExtras)
            }
            if (extras != null) {
                if (dataSource == NotifySource.MESSAGECLIENT || dataSource == NotifySource.BROADCAST) {
                    forwardBroadcast(context, extras)
                    if(ReceiveData.forceAlarm) {
                        AlarmNotification.triggerNotification(ReceiveData.getAlarmType(), context)
                    }
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: " + exc.message.toString() )
        }
    }
}