package de.michelinside.glucodatahandler

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import de.michelinside.glucodatahandler.common.*
import de.michelinside.glucodatahandler.common.chart.ChartBitmapCreator
import de.michelinside.glucodatahandler.common.notification.ChannelType
import de.michelinside.glucodatahandler.common.notification.Channels
import de.michelinside.glucodatahandler.common.R as CR
import de.michelinside.glucodatahandler.common.notifier.*
import de.michelinside.glucodatahandler.common.receiver.ScreenEventReceiver
import de.michelinside.glucodatahandler.common.utils.PackageUtils
import de.michelinside.glucodatahandler.common.utils.Utils.isScreenReaderOn


class GlucoDataServiceWear: GlucoDataService(AppSource.WEAR_APP), NotifierInterface {
    companion object {
        private val LOG_ID = "GDH.GlucoDataServiceWear"
        private var starting = false
        private var migrated = false

        fun start(context: Context) {
            if(!starting) {
                starting = true
                Log.d(LOG_ID, "start called")
                startServiceReceiver = StartServiceReceiver::class.java
                migrateSettings(context)
                start(AppSource.WEAR_APP, context, GlucoDataServiceWear::class.java)
                starting = false
            }
        }

        private fun migrateSettings(context: Context) {
            try {
                if(migrated)
                    return

                migrated = true
                Log.i(LOG_ID, "migrateSettings called")
                val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
                // notification to vibrate_only
                if(!sharedPref.contains(Constants.SHARED_PREF_NOTIFICATION_VIBRATE) && sharedPref.contains("notification")) {
                    with(sharedPref.edit()) {
                        putBoolean(Constants.SHARED_PREF_NOTIFICATION_VIBRATE, sharedPref.getBoolean("notification", false))
                        apply()
                    }
                }
                // complications
                if(!sharedPref.contains(Constants.SHARED_PREF_COMPLICATION_TAP_ACTION)) {
                    val curApp = context.packageName
                    Log.i(LOG_ID, "Setting default tap action for complications to $curApp")
                    with(sharedPref.edit()) {
                        putString(Constants.SHARED_PREF_COMPLICATION_TAP_ACTION, curApp)
                        apply()
                    }
                }

                // graph settings
                if(!sharedPref.contains(Constants.SHARED_PREF_GRAPH_DURATION_WEAR_COMPLICATION)) {
                    val isScreenReader = context.isScreenReaderOn()
                    Log.i(LOG_ID, "Setting default duration for graph - screenReader: $isScreenReader")
                    with(sharedPref.edit()) {
                        putInt(Constants.SHARED_PREF_GRAPH_DURATION_WEAR_COMPLICATION, if(isScreenReader) 0 else ChartBitmapCreator.defaultDurationHours)
                        apply()
                    }
                }
            } catch( exc: Exception ) {
                Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
            }
        }
    }

    init {
        Log.d(LOG_ID, "init called")
        InternalNotifier.addNotifier( this,
            BatteryLevelComplicationUpdater,
            mutableSetOf(
                NotifySource.CAPILITY_INFO,
                NotifySource.BATTERY_LEVEL,
                NotifySource.NODE_BATTERY_LEVEL
            )
        )
    }

    private fun updateComplicationNotifier() {
        Log.d(LOG_ID, "updateComplicationNotifier called")
        ActiveComplicationHandler.checkAlwaysUpdateComplications(this)
        val filter = mutableSetOf(
            NotifySource.MESSAGECLIENT,
            NotifySource.BROADCAST,
            NotifySource.SETTINGS,
            NotifySource.DISPLAY_STATE_CHANGED
        )

        if(ActiveComplicationHandler.canUpdateComplications(NotifySource.TIME_VALUE) && (sharedPref == null || sharedPref!!.getBoolean(Constants.SHARED_PREF_RELATIVE_TIME, true))) {
            Log.v(LOG_ID, "add time value filter - display off: ${ScreenEventReceiver.isDisplayOff()}")
            filter.add(NotifySource.TIME_VALUE)
        } else if(!ReceiveData.isObsoleteLong() && ActiveComplicationHandler.canUpdateComplications(NotifySource.OBSOLETE_VALUE)) {
            filter.add(NotifySource.OBSOLETE_VALUE)
        }

        InternalNotifier.addNotifier( this,
            ActiveComplicationHandler, filter
        )
    }

    override fun onCreate() {
        try {
            Log.i(LOG_ID, "onCreate called")
            super.onCreate()
            AlarmNotificationWear.initNotifications(this)
            val filter = mutableSetOf(
                NotifySource.CAPILITY_INFO,
                NotifySource.DISPLAY_STATE_CHANGED,
                NotifySource.BROADCAST,
                NotifySource.MESSAGECLIENT,
                NotifySource.BATTERY_LEVEL)  // used for watchdog-check
            InternalNotifier.addNotifier(this, this, filter)
            updateComplicationNotifier()
            ActiveComplicationHandler.OnNotifyData(this, NotifySource.CAPILITY_INFO, null)
            ChartComplicationUpdater.init(this)
        } catch (ex: Exception) {
            Log.e(LOG_ID, "onCreate exception: " + ex)
        }
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            Log.d(LOG_ID, "OnNotifyData called for source " + dataSource.toString())
            start(context)
            when (dataSource) {
                NotifySource.BATTERY_LEVEL -> {
                    checkServices(context)
                }
                NotifySource.CAPILITY_INFO -> {
                    if(ScreenEventReceiver.isDisplayOff()) {
                        ScreenEventReceiver.triggerNotify(this)
                    }
                }
                NotifySource.DISPLAY_STATE_CHANGED -> {
                    updateComplicationNotifier()
                    checkServices(context)
                    if(ScreenEventReceiver.isDisplayOff()) {
                        sendCommand(Command.PAUSE_NODE)
                    } else {
                        sendCommand(Command.RESUME_NODE)
                    }
                }
                else -> {}
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: " + exc.message.toString())
        }
    }

    override fun getNotification(): Notification {
        Log.i(LOG_ID,"create notification")
        Channels.createNotificationChannel(this, ChannelType.WEAR_FOREGROUND)

        val pendingIntent = PackageUtils.getAppIntent(this, WearActivity::class.java, 11)

        return Notification.Builder(this, ChannelType.WEAR_FOREGROUND.channelId)
            .setContentTitle(getString(CR.string.forground_notification_descr))
            .setSmallIcon(CR.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        try {
            Log.d(LOG_ID, "onSharedPreferenceChanged called with key $key")
            super.onSharedPreferenceChanged(sharedPreferences, key)
            if(key == Constants.SHARED_PREF_RELATIVE_TIME || key == Constants.SHARED_PREF_PHONE_WEAR_SCREEN_OFF_UPDATE)
                updateComplicationNotifier()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString())
        }
    }

    override fun updateScreenReceiver() {
        try {
            if(!sharedPref!!.getBoolean(Constants.SHARED_PREF_PHONE_WEAR_SCREEN_OFF_UPDATE, true)) {
                if(screenEventReceiver == null) {
                    Log.i(LOG_ID, "register screenEventReceiver")
                    screenEventReceiver = ScreenEventReceiverWear()
                    val filter = IntentFilter()
                    filter.addAction(Intent.ACTION_SCREEN_OFF)
                    filter.addAction(Intent.ACTION_SCREEN_ON)
                    registerReceiver(screenEventReceiver, filter)
                    screenEventReceiver!!.update(this)
                }
            } else if(screenEventReceiver != null) {
                Log.i(LOG_ID, "unregister screenEventReceiver")
                unregisterReceiver(screenEventReceiver)
                screenEventReceiver!!.reset(this)
                screenEventReceiver = null
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateScreenReceiver exception: " + exc.toString())
        }
    }

}