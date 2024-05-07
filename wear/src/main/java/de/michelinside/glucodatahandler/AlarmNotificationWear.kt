package de.michelinside.glucodatahandler

import android.app.NotificationChannel
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.WearPhoneConnection
import de.michelinside.glucodatahandler.common.notification.AlarmNotificationBase
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.Utils


object AlarmNotificationWear : AlarmNotificationBase() {
    private var noAlarmOnPhoneConnected = false
    private var noAlarmOnAAConnected = false
    private var aa_connected = false

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

    override val active: Boolean get() {
        Log.d(LOG_ID, "Check active enabled: ${getEnabled()} - vibrate: $vibrateOnly - aa-active: $isAaActive")
        return (getEnabled() || vibrateOnly) && (!isAaActive)
    }

    override fun canShowNotification(): Boolean {
        return !(noAlarmOnPhoneConnected && WearPhoneConnection.nodesConnected)
    }

    override fun adjustNoticiationChannel(context: Context, channel: NotificationChannel) {
        channel.enableVibration(true)
        channel.vibrationPattern = longArrayOf(0, 500, 100, 500)
    }

    override fun getStartDelayMs(context: Context): Int {
        val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
        return maxOf(sharedPref.getInt(Constants.SHARED_PREF_ALARM_START_DELAY, 1500), 1500)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        try {
            if (key == null) {
                onSharedPreferenceChanged(sharedPreferences, Constants.SHARED_PREF_WEAR_NO_ALARM_POPUP_PHONE_CONNECTED)
                onSharedPreferenceChanged(sharedPreferences, Constants.SHARED_PREF_NO_ALARM_NOTIFICATION_AUTO_CONNECTED)
            } else {
                when(key) {
                    Constants.SHARED_PREF_WEAR_NO_ALARM_POPUP_PHONE_CONNECTED -> noAlarmOnPhoneConnected = sharedPreferences.getBoolean(
                        Constants.SHARED_PREF_WEAR_NO_ALARM_POPUP_PHONE_CONNECTED, noAlarmOnPhoneConnected)
                    Constants.SHARED_PREF_NO_ALARM_NOTIFICATION_AUTO_CONNECTED -> noAlarmOnAAConnected = sharedPreferences.getBoolean(
                        Constants.SHARED_PREF_NO_ALARM_NOTIFICATION_AUTO_CONNECTED, noAlarmOnAAConnected)
                }
            }
            super.onSharedPreferenceChanged(sharedPreferences, key)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString())
        }
    }

    override fun getNotifierFilter(): MutableSet<NotifySource> {
        return mutableSetOf(NotifySource.CAR_CONNECTION, NotifySource.CAPILITY_INFO)
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
                else -> super.OnNotifyData(context, dataSource, extras)

            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: " + exc.toString())
        }
    }
}