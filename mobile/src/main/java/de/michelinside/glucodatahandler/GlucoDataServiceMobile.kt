package de.michelinside.glucodatahandler

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import de.michelinside.glucodatahandler.common.utils.Log
import androidx.annotation.RequiresApi
import de.michelinside.glucodatahandler.android_auto.CarModeReceiver
import de.michelinside.glucodatahandler.common.*
import de.michelinside.glucodatahandler.common.notification.ChannelType
import de.michelinside.glucodatahandler.notification.AlarmNotification
import de.michelinside.glucodatahandler.common.notifier.*
import de.michelinside.glucodatahandler.common.receiver.BatteryReceiver
import de.michelinside.glucodatahandler.healthconnect.HealthConnectManager
import de.michelinside.glucodatahandler.tasker.setWearConnectionState
import de.michelinside.glucodatahandler.watch.WatchDrip
import de.michelinside.glucodatahandler.widget.BatteryLevelWidgetNotifier
import de.michelinside.glucodatahandler.widget.FloatingWidget
import de.michelinside.glucodatahandler.widget.GlucoseBaseWidget
import de.michelinside.glucodatahandler.widget.LockScreenWallpaper
import de.michelinside.glucodatahandler.xdripserver.XDripServer
import androidx.core.content.edit
import de.michelinside.glucodatahandler.tasker.TaskerWatchBatteryReceiver
import de.michelinside.glucodatahandler.transfer.TransferService


class GlucoDataServiceMobile: GlucoDataService(AppSource.PHONE_APP), NotifierInterface {
    private lateinit var floatingWidget: FloatingWidget
    private lateinit var lockScreenWallpaper: LockScreenWallpaper

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
                val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, MODE_PRIVATE)
                Log.i(LOG_ID, "migrateSettings called")

                // full screen alarm notification
                if(!sharedPref.contains(Constants.SHARED_PREF_ALARM_FULLSCREEN_NOTIFICATION_ENABLED)) {
                    if (AlarmNotification.hasFullscreenPermission(context)) {
                        Log.i(LOG_ID, "Enabling fullscreen notification as default")
                        sharedPref.edit {
                            putBoolean(
                                Constants.SHARED_PREF_ALARM_FULLSCREEN_NOTIFICATION_ENABLED,
                                true
                            )
                        }
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
            HealthConnectManager.init(applicationContext)
            XDripServer.init(applicationContext)
            TransferService.start(applicationContext)
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
            InternalNotifier.addNotifier(
                this,
                TaskerWatchBatteryReceiver,
                mutableSetOf(
                    NotifySource.BROADCAST,
                    NotifySource.NODE_BATTERY_LEVEL
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
            InternalNotifier.remNotifier(this, this)
            InternalNotifier.remNotifier(this, TaskerDataReceiver)
            InternalNotifier.remNotifier(this, TaskerWatchBatteryReceiver)
            TransferService.stop(applicationContext)
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
        lockScreenWallpaper.update()
        floatingWidget.update()
        if(!PermanentNotification.recreateBitmap())
            PermanentNotification.showNotifications()
    }

}