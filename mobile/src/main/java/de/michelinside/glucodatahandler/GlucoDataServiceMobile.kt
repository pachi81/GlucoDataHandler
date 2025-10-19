package de.michelinside.glucodatahandler

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import de.michelinside.glucodatahandler.common.utils.Log
import androidx.annotation.RequiresApi
import de.michelinside.glucodatahandler.android_auto.CarModeReceiver
import de.michelinside.glucodatahandler.common.*
import de.michelinside.glucodatahandler.common.chart.ChartCreator
import de.michelinside.glucodatahandler.common.notification.ChannelType
import de.michelinside.glucodatahandler.notification.AlarmNotification
import de.michelinside.glucodatahandler.common.notifier.*
import de.michelinside.glucodatahandler.common.receiver.BatteryReceiver
import de.michelinside.glucodatahandler.common.receiver.GlucoseDataReceiver
import de.michelinside.glucodatahandler.common.receiver.XDripBroadcastReceiver
import de.michelinside.glucodatahandler.common.utils.Utils
import de.michelinside.glucodatahandler.common.utils.Utils.isScreenReaderOn
import de.michelinside.glucodatahandler.healthconnect.HealthConnectManager
import de.michelinside.glucodatahandler.tasker.setWearConnectionState
import de.michelinside.glucodatahandler.watch.WatchDrip
import de.michelinside.glucodatahandler.widget.BatteryLevelWidgetNotifier
import de.michelinside.glucodatahandler.widget.FloatingWidget
import de.michelinside.glucodatahandler.widget.GlucoseBaseWidget
import de.michelinside.glucodatahandler.widget.LockScreenWallpaper
import de.michelinside.glucodatahandler.xdripserver.XDripServer
import java.math.RoundingMode


class GlucoDataServiceMobile: GlucoDataService(AppSource.PHONE_APP), NotifierInterface {
    private lateinit var floatingWidget: FloatingWidget
    private lateinit var lockScreenWallpaper: LockScreenWallpaper
    private var lastForwardTime = 0L

    init {
        Log.i(LOG_ID, "init called")
    }

    companion object {
        private const val LOG_ID = "GDH.GlucoDataServiceMobile"
        private var starting = false
        private var migrated = false
        fun start(context: Context) {
            if(!starting) {
                starting = true
                Log.d(LOG_ID, "start called")
                startServiceReceiver = StartServiceReceiver::class.java
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
                val sharedPrefs = context.getSharedPreferences(Constants.SHARED_PREF_TAG, MODE_PRIVATE)
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

                // create default tap actions
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
                // widgets
                if(!sharedPrefs.contains(Constants.SHARED_PREF_FLOATING_WIDGET_TAP_ACTION)) {
                    val curApp = context.packageName
                    Log.i(LOG_ID, "Setting default tap action for floating widget to $curApp")
                    with(sharedPrefs.edit()) {
                        putString(Constants.SHARED_PREF_FLOATING_WIDGET_TAP_ACTION, curApp)
                        apply()
                    }
                }
                if(!sharedPrefs.contains(Constants.SHARED_PREF_WIDGET_TAP_ACTION)) {
                    val curApp = context.packageName
                    Log.i(LOG_ID, "Setting default tap action for widget to $curApp")
                    with(sharedPrefs.edit()) {
                        putString(Constants.SHARED_PREF_WIDGET_TAP_ACTION, curApp)
                        apply()
                    }
                }
                if(!sharedPrefs.contains(Constants.SHARED_PREF_FLOATING_WIDGET_SIZE_MIGRATION) || !sharedPrefs.contains(Constants.SHARED_PREF_FLOATING_WIDGET_SIZE_MIGRATION_2)) {
                    if(sharedPrefs.contains(Constants.SHARED_PREF_FLOATING_WIDGET_SIZE)) {
                        val oldValue = sharedPrefs.getInt(Constants.SHARED_PREF_FLOATING_WIDGET_SIZE, 0)
                        if(oldValue in 1..10) {
                            Log.i(LOG_ID, "Migrating size from $oldValue")
                            with(sharedPrefs.edit()) {
                                putInt(Constants.SHARED_PREF_FLOATING_WIDGET_SIZE, oldValue+1)
                                apply()
                            }
                        }
                    }
                    with(sharedPrefs.edit()) {
                        putBoolean(Constants.SHARED_PREF_FLOATING_WIDGET_SIZE_MIGRATION, true)
                        putBoolean(Constants.SHARED_PREF_FLOATING_WIDGET_SIZE_MIGRATION_2, true)
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

                // graph settings related to screen reader
                if(!sharedPrefs.contains(Constants.SHARED_PREF_GRAPH_DURATION_PHONE_MAIN)) {
                    val isScreenReader = context.isScreenReaderOn()
                    Log.i(LOG_ID, "Setting default duration for graph - screenReader: $isScreenReader")
                    with(sharedPrefs.edit()) {
                        putInt(Constants.SHARED_PREF_GRAPH_DURATION_PHONE_MAIN, if(isScreenReader) 0 else ChartCreator.defaultDurationHours)
                        apply()
                    }
                }
                if(sharedPrefs.contains(Constants.DEPRECATED_SHARED_PREF_GRAPH_DURATION_PHONE_NOTIFICATION) || !sharedPrefs.contains(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_SHOW_GRAPH)) {
                    val isScreenReader = context.isScreenReaderOn()
                    Log.i(LOG_ID, "Setting default duration for notification graph - screenReader: $isScreenReader")
                    with(sharedPrefs.edit()) {
                        putBoolean(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_SHOW_GRAPH, !isScreenReader && sharedPrefs.getInt(Constants.DEPRECATED_SHARED_PREF_GRAPH_DURATION_PHONE_NOTIFICATION, 0) > 0)
                        remove(Constants.DEPRECATED_SHARED_PREF_GRAPH_DURATION_PHONE_NOTIFICATION)
                        apply()
                    }
                }
                if(!sharedPrefs.contains(Constants.SHARED_PREF_FULLSCREEN_LANDSCAPE)) {
                    val isScreenReader = context.isScreenReaderOn()
                    Log.i(LOG_ID, "Setting default fullscreen mode for screenReader: $isScreenReader")
                    with(sharedPrefs.edit()) {
                        putBoolean(Constants.SHARED_PREF_FULLSCREEN_LANDSCAPE, !isScreenReader)
                        apply()
                    }
                }

                if(sharedPrefs.contains(Constants.DEPRECATED_SHARED_PREF_GRAPH_DURATION_PHONE_WIDGET)) {
                    val oldDuration = sharedPrefs.getInt(Constants.DEPRECATED_SHARED_PREF_GRAPH_DURATION_PHONE_WIDGET, 0)
                    Log.i(LOG_ID, "Migratate old widget duration of $oldDuration hours to bitmap duration")
                    with(sharedPrefs.edit()) {
                        if(oldDuration > 0)
                            putInt(Constants.SHARED_PREF_GRAPH_BITMAP_DURATION, oldDuration)
                        remove(Constants.DEPRECATED_SHARED_PREF_GRAPH_DURATION_PHONE_WIDGET)
                        apply()
                    }
                }
                if(sharedPrefs.contains(Constants.DEPRECATED_SHARED_PREF_GRAPH_SHOW_AXIS_PHONE_WIDGET)) {
                    val oldShowAxis = sharedPrefs.getBoolean(Constants.DEPRECATED_SHARED_PREF_GRAPH_SHOW_AXIS_PHONE_WIDGET, false)
                    Log.i(LOG_ID, "Migratate old widget show axis of $oldShowAxis to bitmap show axis")
                    with(sharedPrefs.edit()) {
                        putBoolean(Constants.SHARED_PREF_GRAPH_BITMAP_SHOW_AXIS, oldShowAxis)
                        remove(Constants.DEPRECATED_SHARED_PREF_GRAPH_SHOW_AXIS_PHONE_WIDGET)
                        apply()
                    }
                }

                // Juggluco webserver settings
                if(!sharedPrefs.contains(Constants.SHARED_PREF_SOURCE_JUGGLUCO_WEBSERVER_ENABLED) || !sharedPrefs.contains(Constants.SHARED_PREF_SOURCE_JUGGLUCO_WEBSERVER_IOB_SUPPORT)) {
                    // check current source for Juggluco and if Nightscout is enabled for local requests supporting IOB
                    var webServer = false
                    if(sharedPrefs.getBoolean(Constants.SHARED_PREF_SOURCE_JUGGLUCO_ENABLED, true)
                        && sharedPrefs.getBoolean(Constants.SHARED_PREF_NIGHTSCOUT_ENABLED, false)
                        && sharedPrefs.getBoolean(Constants.SHARED_PREF_NIGHTSCOUT_IOB_COB, false)
                        && sharedPrefs.getString(Constants.SHARED_PREF_NIGHTSCOUT_TOKEN, "").isNullOrEmpty()
                        && sharedPrefs.getString(Constants.SHARED_PREF_NIGHTSCOUT_SECRET, "").isNullOrEmpty()
                        && sharedPrefs.getString(Constants.SHARED_PREF_NIGHTSCOUT_URL, "")!!.trim().trimEnd('/') == GlucoseDataReceiver.JUGGLUCO_WEBSERVER
                        ) {
                        val sharedGlucosePref = context.getSharedPreferences(Constants.GLUCODATA_BROADCAST_ACTION, MODE_PRIVATE)
                        if(DataSource.fromIndex(sharedGlucosePref.getInt(Constants.EXTRA_SOURCE_INDEX, DataSource.NONE.ordinal)) == DataSource.JUGGLUCO) {
                            webServer = true
                        }
                    }
                    Log.i(LOG_ID, "Using Juggluco webserver: $webServer")
                    with(sharedPrefs.edit()) {
                        putBoolean(Constants.SHARED_PREF_SOURCE_JUGGLUCO_WEBSERVER_ENABLED, webServer)
                        putBoolean(Constants.SHARED_PREF_SOURCE_JUGGLUCO_WEBSERVER_IOB_SUPPORT, webServer)
                        if(webServer) {
                            putBoolean(Constants.SHARED_PREF_NIGHTSCOUT_ENABLED, false)
                        }
                        apply()
                    }
                }

                // notification icon
                if(!sharedPrefs.contains(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_ICON)
                    || sharedPrefs.getString(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_ICON, PermanentNotification.StatusBarIcon.GLUCOSE.pref) == PermanentNotification.StatusBarIcon.APP.pref) {
                    with(sharedPrefs.edit()) {
                        putString(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_ICON, PermanentNotification.StatusBarIcon.GLUCOSE.pref)
                        apply()
                    }
                }
                if(!sharedPrefs.contains(Constants.SHARED_PREF_SECOND_PERMANENT_NOTIFICATION_ICON)
                    || sharedPrefs.getString(Constants.SHARED_PREF_SECOND_PERMANENT_NOTIFICATION_ICON, PermanentNotification.StatusBarIcon.TREND.pref) == PermanentNotification.StatusBarIcon.APP.pref) {
                    with(sharedPrefs.edit()) {
                        putString(Constants.SHARED_PREF_SECOND_PERMANENT_NOTIFICATION_ICON, PermanentNotification.StatusBarIcon.TREND.pref)
                        apply()
                    }
                }
                if(!sharedPrefs.contains(Constants.SHARED_PREF_THIRD_PERMANENT_NOTIFICATION_ICON)
                    || sharedPrefs.getString(Constants.SHARED_PREF_THIRD_PERMANENT_NOTIFICATION_ICON, PermanentNotification.StatusBarIcon.DELTA.pref) == PermanentNotification.StatusBarIcon.APP.pref) {
                    with(sharedPrefs.edit()) {
                        putString(Constants.SHARED_PREF_THIRD_PERMANENT_NOTIFICATION_ICON, PermanentNotification.StatusBarIcon.DELTA.pref)
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
            Log.i(LOG_ID, "onCreate called")
            super.onCreate()
            val filter = mutableSetOf(
                NotifySource.BROADCAST,
                NotifySource.MESSAGECLIENT,
                NotifySource.OBSOLETE_VALUE, // to trigger re-start for the case of stopped by the system
                NotifySource.CAR_CONNECTION,
                NotifySource.CAPILITY_INFO,
                NotifySource.BATTERY_LEVEL,   // used for watchdog-check
                NotifySource.DB_DATA_CHANGED)
            InternalNotifier.addNotifier(this, this, filter)
            floatingWidget = FloatingWidget(this)
            lockScreenWallpaper = LockScreenWallpaper(this)
            PermanentNotification.create(applicationContext)
            CarModeReceiver.init(applicationContext)
            GlucoseBaseWidget.updateWidgets(applicationContext)
            BatteryLevelWidgetNotifier.OnNotifyData(applicationContext, NotifySource.CAPILITY_INFO, null)
            WatchDrip.init(applicationContext)
            floatingWidget.create()
            lockScreenWallpaper.create()
            AlarmNotification.initNotifications(this)
            HealthConnectManager.init(this.applicationContext)
            XDripServer.init(this.applicationContext)
            InternalNotifier.addNotifier(
                this,
                TaskerDataReceiver,
                mutableSetOf(
                    NotifySource.BROADCAST,
                    NotifySource.IOB_COB_CHANGE,
                    NotifySource.MESSAGECLIENT,
                    NotifySource.OBSOLETE_VALUE,
                    NotifySource.ALARM_TRIGGER,
                    NotifySource.DELTA_ALARM_TRIGGER
                )
            )
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreate exception: " + exc.message.toString() )
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            Log.i(LOG_ID, "onStartCommand called")
            val start = super.onStartCommand(intent, flags, startId)
            PermanentNotification.showNotifications(true)
            return start
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onStartCommand exception: " + exc.toString())
        }
        return START_STICKY  // keep alive
    }

    override fun getNotification(): Notification {
        Log.i(LOG_ID, "getNotification called")
        return PermanentNotification.getNotification(
            !sharedPref!!.getBoolean(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_EMPTY, false),
            Constants.SHARED_PREF_PERMANENT_NOTIFICATION_ICON,
            ChannelType.MOBILE_FOREGROUND,
            sharedPref!!.getBoolean(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_CUSTOM_LAYOUT, true)
        )
    }

    override fun onDestroy() {
        try {
            Log.w(LOG_ID, "onDestroy called")
            PermanentNotification.destroy()
            AlarmNotification.destroy(this)
            CarModeReceiver.cleanup(applicationContext)
            WatchDrip.close(applicationContext)
            floatingWidget.destroy()
            lockScreenWallpaper.destroy()
            HealthConnectManager.close(this.applicationContext)
            XDripServer.close(this.applicationContext)
            super.onDestroy()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onDestroy exception: " + exc.message.toString() )
        }
    }

    fun sendBroadcast(intent: Intent, receiverPrefKey: String, context: Context, sharedPref: SharedPreferences) {
        try {
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            var receivers = sharedPref.getStringSet(receiverPrefKey, HashSet<String>())
            Log.i(LOG_ID, "Forward " + receiverPrefKey + " Broadcast to " + receivers?.size.toString() + " receivers")
            if(Log.isLoggable(LOG_ID, android.util.Log.DEBUG))
                Log.d(LOG_ID, "Forward package: ${Utils.dumpBundle(intent.extras)}")
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
            lastForwardTime = ReceiveData.time
        } catch (ex: Exception) {
            Log.e(LOG_ID, "Exception while sending broadcast for " + receiverPrefKey + ": " + ex)
        }
    }
    /*
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
*/
    private fun forwardBroadcast(context: Context, extras: Bundle) {
        Log.v(LOG_ID, "forwardBroadcast called")
        CarModeReceiver.sendToGlucoDataAuto(context)

        val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, MODE_PRIVATE)
        /*
        if (sharedPref.getBoolean(Constants.SHARED_PREF_SEND_TO_BANGLEJS, false)) {
            sendToBangleJS(context)
        }*/

        val interval = sharedPref.getInt(Constants.SHARED_PREF_SEND_TO_RECEIVER_INTERVAL, 1)
        val elapsed = Utils.getElapsedTimeMinute(lastForwardTime, RoundingMode.HALF_UP)
        if (interval > 1 && elapsed < interval) {
            Log.d(LOG_ID, "Ignore data because of interval $interval - elapsed: $elapsed")
            return
        }

        if (sharedPref.getBoolean(Constants.SHARED_PREF_SEND_TO_XDRIP, false)) {
            val intent = Intent(Constants.XDRIP_ACTION_GLUCOSE_READING)
            // always sends time as start time, because it is only set, if the sensorId have changed!
            val sensor = Bundle()
            sensor.putLong("sensorStartTime", if(ReceiveData.sensorStartTime > 0) ReceiveData.sensorStartTime else Utils.getDayStartTime())  // use start time of the current day
            val currentSensor = Bundle()
            currentSensor.putBundle("currentSensor", sensor)
            intent.putExtra("sas", currentSensor)
            val bleManager = Bundle()
            bleManager.putString("sensorSerial", ReceiveData.sensorID ?: context.packageName)
            intent.putExtra("bleManager", bleManager)
            intent.putExtra("glucose", ReceiveData.rawValue.toDouble())
            intent.putExtra("timestamp", ReceiveData.time)
            sendBroadcast(intent, Constants.SHARED_PREF_XDRIP_RECEIVERS, context, sharedPref)
        }

        if (sharedPref.getBoolean(Constants.SHARED_PREF_SEND_XDRIP_BROADCAST, false)) {
            val xDripExtras = XDripBroadcastReceiver.createExtras(context)
            if (xDripExtras != null) {
                val intent = Intent(Intents.XDRIP_BROADCAST_ACTION)
                intent.putExtras(xDripExtras)
                sendBroadcast(intent, Constants.SHARED_PREF_XDRIP_BROADCAST_RECEIVERS, context, sharedPref)
            }
        }

        if (sharedPref.getBoolean(Constants.SHARED_PREF_SEND_TO_GLUCODATA_AOD, false)) {
            val intent = Intent(Constants.GLUCODATA_BROADCAST_ACTION)
            intent.putExtras(extras)
            sendBroadcast(intent, Constants.SHARED_PREF_GLUCODATA_RECEIVERS, context, sharedPref)
        }
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            Log.d(LOG_ID, "OnNotifyData called for source " + dataSource.toString())
            start(context)
            if (dataSource == NotifySource.CAPILITY_INFO) {
                context.setWearConnectionState(WearPhoneConnection.nodesConnected)
            }
            if (dataSource == NotifySource.CAR_CONNECTION && CarModeReceiver.connected) {
                CarModeReceiver.sendToGlucoDataAuto(context, true, true)
            }
            if (dataSource == NotifySource.DB_DATA_CHANGED && CarModeReceiver.connected) {
                CarModeReceiver.sendToGlucoDataAuto(context, false, true)
            }
            if (dataSource == NotifySource.BATTERY_LEVEL) {
                checkServices(context)
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

    override fun updateBatteryReceiver() {
        try {
            if(batteryReceiver == null) {
                Log.i(LOG_ID, "register batteryReceiver")
                batteryReceiver = BatteryReceiver()
                registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            } else {
                if (!sharedPref!!.getBoolean(Constants.SHARED_PREF_BATTERY_RECEIVER_ENABLED, true)) {
                    Log.i(LOG_ID, "batteryReceiver disabled - keep active as watchdog")
                    // notify new battery level to update UI
                    BatteryReceiver.batteryPercentage = 0
                    InternalNotifier.notify(this, NotifySource.BATTERY_LEVEL, BatteryReceiver.batteryBundle)
                } else {
                    Log.i(LOG_ID, "batteryReceiver enabled - re-register")
                    unregisterReceiver(batteryReceiver)
                    registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateBatteryReceiver exception: " + exc.toString())
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.i(LOG_ID, "onConfigurationChanged called: $newConfig")
        if(!PermanentNotification.recreateBitmap())
            PermanentNotification.showNotifications()
    }

}