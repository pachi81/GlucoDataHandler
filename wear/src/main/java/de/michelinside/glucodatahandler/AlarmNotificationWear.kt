package de.michelinside.glucodatahandler

import android.app.NotificationChannel
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import de.michelinside.glucodatahandler.common.utils.Log
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.WearPhoneConnection
import de.michelinside.glucodatahandler.common.notification.AlarmHandler
import de.michelinside.glucodatahandler.common.notification.AlarmNotificationBase
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.receiver.BatteryReceiver
import de.michelinside.glucodatahandler.common.utils.Utils


object AlarmNotificationWear : AlarmNotificationBase() {
    private var noAlarmOnPhoneConnected = false
    private var noAlarmOnAAConnected = false
    private var aa_connected = false
    private var noAlarmWhileCharging = false

    var isAaConnected: Boolean
        get() {
            return (aa_connected && WearPhoneConnection.nodesConnected)
        }
        set(value) {
            Log.i(LOG_ID, "Set Android Auto connected: $value")
            aa_connected = value
        }

    private val isAaActive: Boolean get() {
        return (noAlarmOnAAConnected && isAaConnected)
    }

    private val isCharging: Boolean get() {
        return (noAlarmWhileCharging && BatteryReceiver.isCharging(BatteryReceiver.batteryStatus))
    }

    override val active: Boolean get() {
        Log.d(LOG_ID, "Check active enabled: ${getEnabled()} - vibrate: $vibrateOnly - charging: $isCharging - aa-active: $isAaActive")
        return isEnabled() && !isAaActive && !isCharging
    }

    fun isEnabled(): Boolean {
        return (getEnabled() || vibrateOnly)
    }
    override fun canShowNotification(): Boolean {
        return !(noAlarmOnPhoneConnected && WearPhoneConnection.nodesConnected)
    }

    override fun adjustNoticiationChannel(context: Context, channel: NotificationChannel) {
        channel.enableVibration(true)
        channel.vibrationPattern = longArrayOf(0)
    }

    override fun getStartDelayMs(context: Context): Int {
        val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
        return maxOf(sharedPref.getInt(Constants.SHARED_PREF_ALARM_START_DELAY, 1500), 500)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        try {
            Log.d(LOG_ID, "onSharedPreferenceChanged called for " + key)
            if (key == null) {
                onSharedPreferenceChanged(sharedPreferences, Constants.SHARED_PREF_WEAR_NO_ALARM_POPUP_PHONE_CONNECTED)
                onSharedPreferenceChanged(sharedPreferences, Constants.SHARED_PREF_NO_ALARM_NOTIFICATION_AUTO_CONNECTED)
                onSharedPreferenceChanged(sharedPreferences, Constants.SHARED_PREF_WEAR_NO_ALARM_WHILE_CHARGING)
                if(GlucoDataService.context != null)
                    AlarmHandler.checkNotifier(GlucoDataService.context!!)
            } else {
                when(key) {
                    Constants.SHARED_PREF_WEAR_NO_ALARM_POPUP_PHONE_CONNECTED -> noAlarmOnPhoneConnected = sharedPreferences.getBoolean(
                        Constants.SHARED_PREF_WEAR_NO_ALARM_POPUP_PHONE_CONNECTED, noAlarmOnPhoneConnected)
                    Constants.SHARED_PREF_NO_ALARM_NOTIFICATION_AUTO_CONNECTED -> noAlarmOnAAConnected = sharedPreferences.getBoolean(
                        Constants.SHARED_PREF_NO_ALARM_NOTIFICATION_AUTO_CONNECTED, noAlarmOnAAConnected)
                    Constants.SHARED_PREF_WEAR_NO_ALARM_WHILE_CHARGING -> noAlarmWhileCharging = sharedPreferences.getBoolean(
                        Constants.SHARED_PREF_WEAR_NO_ALARM_WHILE_CHARGING, noAlarmWhileCharging)
                }
            }
            super.onSharedPreferenceChanged(sharedPreferences, key)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString())
        }
    }

    override fun getNotifierFilter(): MutableSet<NotifySource> {
        return mutableSetOf(NotifySource.CAR_CONNECTION, NotifySource.CAPILITY_INFO, NotifySource.DISPLAY_STATE_CHANGED, NotifySource.BATTERY_LEVEL)
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            when(dataSource) {
                NotifySource.CAR_CONNECTION -> {
                    Log.i(LOG_ID, "Car connection has changed: ${Utils.dumpBundle(extras)}")
                    if (extras?.containsKey(Constants.EXTRA_AA_CONNECTED) == true) {
                        isAaConnected = extras.getBoolean(Constants.EXTRA_AA_CONNECTED)
                    }
                }
                NotifySource.CAPILITY_INFO -> {
                    if(!WearPhoneConnection.nodesConnected)
                        isAaConnected = false  // reset for the case it will disconnect on phone
                }
                NotifySource.DISPLAY_STATE_CHANGED -> {
                    AlarmHandler.checkNotifier(context)
                }
                NotifySource.BATTERY_LEVEL -> {
                    updateAlarmState(context)
                }
                else -> super.OnNotifyData(context, dataSource, extras)
            }
            if(dataSource == NotifySource.ALARM_STATE_CHANGED) {
                AlarmHandler.checkNotifier(context)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: " + exc.toString())
        }
    }
}