package de.michelinside.glucodatahandler

import android.content.SharedPreferences
import android.util.Log
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.WearPhoneConnection
import de.michelinside.glucodatahandler.common.notification.AlarmNotificationBase


object AlarmNotificationWear : AlarmNotificationBase() {
    private var noAlarmOnPhoneConnected = false
    override val active: Boolean get() {
        return getEnabled() || vibrateOnly
    }

    override fun canShowNotification(): Boolean {
        return !(noAlarmOnPhoneConnected && WearPhoneConnection.nodesConnected)
    }
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        try {
            super.onSharedPreferenceChanged(sharedPreferences, key)
            if (key == null) {
                onSharedPreferenceChanged(sharedPreferences, Constants.SHARED_PREF_WEAR_NO_ALARM_POPUP_PHONE_CONNECTED)
            } else {
                when(key) {
                    Constants.SHARED_PREF_WEAR_NO_ALARM_POPUP_PHONE_CONNECTED -> noAlarmOnPhoneConnected = sharedPreferences.getBoolean(
                        Constants.SHARED_PREF_WEAR_NO_ALARM_POPUP_PHONE_CONNECTED, noAlarmOnPhoneConnected)
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString())
        }
    }
}