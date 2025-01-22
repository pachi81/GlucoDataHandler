package de.michelinside.glucodataauto.preferences

import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import de.michelinside.glucodataauto.R
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.notification.ChannelType
import de.michelinside.glucodatahandler.common.notification.Channels


class GDANotificationSettingsFragment: SettingsFragmentBase(R.xml.pref_gda_notification) {
    override fun updatePreferences() {
        super.updatePreferences()
        val pref = findPreference<SwitchPreferenceCompat>(Constants.SHARED_PREF_CAR_NOTIFICATION) ?: return
        if (pref.isChecked && !Channels.notificationChannelActive(requireContext(), ChannelType.ANDROID_AUTO)) {
            Log.i(LOG_ID, "Disable car notification as there is no permission!")
            pref.isChecked = false
            with(preferenceManager.sharedPreferences!!.edit()) {
                putBoolean(Constants.SHARED_PREF_CAR_NOTIFICATION, false)
                apply()
            }
        }
        pref.icon = ContextCompat.getDrawable(requireContext(), if(pref.isChecked) R.drawable.icon_popup else R.drawable.icon_popup_off)
    }

    override fun updateEnableStates(sharedPreferences: SharedPreferences) {
        try {
            Log.v(LOG_ID, "updateEnableStates called")
            setEnableState<SwitchPreferenceCompat>(sharedPreferences, Constants.SHARED_PREF_CAR_NOTIFICATION_ALARM_ONLY, Constants.SHARED_PREF_CAR_NOTIFICATION)
            setEnableState<SeekBarPreference>(sharedPreferences, Constants.SHARED_PREF_CAR_NOTIFICATION_INTERVAL_NUM, Constants.SHARED_PREF_CAR_NOTIFICATION, Constants.SHARED_PREF_CAR_NOTIFICATION_ALARM_ONLY)
            setEnableState<SeekBarPreference>(sharedPreferences, Constants.SHARED_PREF_CAR_NOTIFICATION_REAPPEAR_INTERVAL, Constants.SHARED_PREF_CAR_NOTIFICATION, Constants.SHARED_PREF_CAR_NOTIFICATION_ALARM_ONLY)
            setEnableState<SwitchPreferenceCompat>(sharedPreferences, Constants.SHARED_PREF_CAR_NOTIFICATION_SHOW_IOB_COB, Constants.SHARED_PREF_CAR_NOTIFICATION)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateEnableStates exception: " + exc.toString())
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        if(key == Constants.SHARED_PREF_CAR_NOTIFICATION) {
            if (preferenceManager.sharedPreferences!!.getBoolean(Constants.SHARED_PREF_CAR_NOTIFICATION, false) && !Channels.notificationChannelActive(requireContext(), ChannelType.ANDROID_AUTO)) {
                val intent: Intent = if (Channels.notificationActive(requireContext())) { // only the channel is inactive!
                    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                        .putExtra(Settings.EXTRA_CHANNEL_ID, ChannelType.ANDROID_AUTO.channelId)
                } else {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                }
                startActivity(intent)
            } else {
                updatePreferences()
            }
        }
    }

}