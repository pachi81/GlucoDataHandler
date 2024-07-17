package de.michelinside.glucodatahandler

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi
import de.michelinside.glucodatahandler.android_auto.CarModeReceiver
import de.michelinside.glucodatahandler.common.*
import de.michelinside.glucodatahandler.notification.AlarmNotification
import de.michelinside.glucodatahandler.common.notifier.*
import de.michelinside.glucodatahandler.common.receiver.XDripBroadcastReceiver
import de.michelinside.glucodatahandler.common.utils.GlucoDataUtils
import de.michelinside.glucodatahandler.tasker.setWearConnectionState
import de.michelinside.glucodatahandler.watch.WatchDrip
import de.michelinside.glucodatahandler.widget.FloatingWidget
import de.michelinside.glucodatahandler.widget.GlucoseBaseWidget
import de.michelinside.glucodatahandler.widget.LockScreenWallpaper


class GlucoDataServiceMobile: GlucoDataService(AppSource.PHONE_APP), NotifierInterface {
    private lateinit var floatingWidget: FloatingWidget

    init {
        Log.d(LOG_ID, "init called")
        InternalNotifier.addNotifier(
            this,
            TaskerDataReceiver,
            mutableSetOf(
                NotifySource.BROADCAST,
                NotifySource.IOB_COB_CHANGE,
                NotifySource.MESSAGECLIENT,
                NotifySource.OBSOLETE_VALUE,
                NotifySource.ALARM_TRIGGER
            )
        )
    }

    companion object {
        private val LOG_ID = "GDH.GlucoDataServiceMobile"
        private var starting = false
        private var migrated = false
        fun start(context: Context) {
            if(!starting) {
                starting = true
                Log.v(LOG_ID, "start called")
                migrateSettings(context)
                start(AppSource.PHONE_APP, context, GlucoDataServiceMobile::class.java)
                starting = false
            }
        }

        fun sendLogcatRequest() {
            if(connection != null) {
                Log.d(LOG_ID, "sendLogcatRequest called")
                connection!!.sendMessage(NotifySource.LOGCAT_REQUEST, null, filterReceiverId = connection!!.pickBestNodeId())
            }
        }


        private fun migrateSettings(context: Context) {
            try {
                if(migrated)
                    return

                migrated = true
                val sharedPrefs = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
                Log.i(LOG_ID, "migrateSettings called")
                if(!sharedPrefs.contains(Constants.SHARED_PREF_GLUCODATA_RECEIVERS)) {
                    val receivers = HashSet<String>()
                    val sendToAod = sharedPrefs.getBoolean(Constants.SHARED_PREF_SEND_TO_GLUCODATA_AOD, false)
                    if (sendToAod)
                        receivers.add("de.metalgearsonic.glucodata.aod")
                    Log.i(LOG_ID, "Upgrade receivers to " + receivers.toString())
                    with(sharedPrefs.edit()) {
                        putStringSet(Constants.SHARED_PREF_GLUCODATA_RECEIVERS, receivers)
                        apply()
                    }
                }

                if(!sharedPrefs.contains(Constants.SHARED_PREF_XDRIP_RECEIVERS)) {
                    val receivers = HashSet<String>()
                    receivers.add("com.eveningoutpost.dexdrip")
                    Log.i(LOG_ID, "Upgrade receivers to " + receivers.toString())
                    with(sharedPrefs.edit()) {
                        putStringSet(Constants.SHARED_PREF_XDRIP_RECEIVERS, receivers)
                        apply()
                    }
                }

                // create default tap actions as it was before
                // notifications
                if(!sharedPrefs.contains(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_TAP_ACTION)) {
                    val curApp = context.packageName
                    Log.i(LOG_ID, "Setting default tap action for notification to $curApp")
                    with(sharedPrefs.edit()) {
                        putString(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_TAP_ACTION, curApp)
                        apply()
                    }
                }
                if(!sharedPrefs.contains(Constants.SHARED_PREF_SECOND_PERMANENT_NOTIFICATION_TAP_ACTION)) {
                    val curApp = context.packageName
                    Log.i(LOG_ID, "Setting default tap action for second notification to $curApp")
                    with(sharedPrefs.edit()) {
                        putString(Constants.SHARED_PREF_SECOND_PERMANENT_NOTIFICATION_TAP_ACTION, curApp)
                        apply()
                    }
                }
                // full screen alarm notification
                if(!sharedPrefs.contains(Constants.SHARED_PREF_ALARM_FULLSCREEN_NOTIFICATION_ENABLED)) {
                    if (AlarmNotification.hasFullscreenPermission(context)) {
                        Log.i(LOG_ID, "Enabling fullscreen notification as default")
                        with(sharedPrefs.edit()) {
                            putBoolean(Constants.SHARED_PREF_ALARM_FULLSCREEN_NOTIFICATION_ENABLED, true)
                            apply()
                        }
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

            } catch (exc: Exception) {
                Log.e(LOG_ID, "migrateSettings exception: " + exc.message.toString() )
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

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            Log.d(LOG_ID, "onStartCommand called")
            val start = super.onStartCommand(intent, flags, startId)
            PermanentNotification.showNotifications(true)
            return start
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onStartCommand exception: " + exc.toString())
        }
        return START_STICKY  // keep alive
    }

    override fun getNotification(): Notification {
        Log.v(LOG_ID, "getNotification called")
        return PermanentNotification.getNotification(
            !sharedPref!!.getBoolean(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_EMPTY, false),
            Constants.SHARED_PREF_PERMANENT_NOTIFICATION_ICON,
            true,
            sharedPref!!.getBoolean(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_CUSTOM_LAYOUT, true)
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
                    CarModeReceiver.sendToGlucoDataAuto(context, autoExtras, true)
            }
            if (extras != null) {
                if (dataSource == NotifySource.MESSAGECLIENT || dataSource == NotifySource.BROADCAST) {
                    forwardBroadcast(context, extras)
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: " + exc.message.toString() )
        }
    }
}