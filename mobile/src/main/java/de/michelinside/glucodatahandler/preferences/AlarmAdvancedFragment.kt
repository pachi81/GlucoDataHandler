package de.michelinside.glucodatahandler.preferences

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.notification.AlarmHandler
import de.michelinside.glucodatahandler.common.notification.Channels
import de.michelinside.glucodatahandler.common.utils.Utils
import de.michelinside.glucodatahandler.notification.AlarmNotification

class AlarmAdvancedFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val LOG_ID = "GDH.AlarmAdvancedFragment"
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        try {
            Log.v(LOG_ID, "onCreatePreferences called for key: ${Utils.dumpBundle(this.arguments)}" )
            preferenceManager.sharedPreferencesName = Constants.SHARED_PREF_TAG
            setPreferencesFromResource(R.xml.alarm_advanced, rootKey)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreatePreferences exception: " + exc.toString())
        }
    }

    override fun onResume() {
        Log.d(LOG_ID, "onResume called")
        try {
            preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
            updateEnableStates(preferenceManager.sharedPreferences!!)
            update()
            super.onResume()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onResume exception: " + exc.toString())
        }
    }

    override fun onPause() {
        Log.d(LOG_ID, "onPause called")
        try {
            preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
            super.onPause()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onPause exception: " + exc.toString())
        }
    }


    fun <T : Preference?> setEnableState(sharedPreferences: SharedPreferences, key: String, enableKey: String, invert: Boolean = false) {
        val pref = findPreference<T>(key)
        if (pref != null)
            pref.isEnabled = invert != sharedPreferences.getBoolean(enableKey, false)
    }

    fun updateEnableStates(sharedPreferences: SharedPreferences) {
        try {
            setEnableState<SwitchPreferenceCompat>(sharedPreferences, Constants.SHARED_PREF_ALARM_FULLSCREEN_DISMISS_KEYGUARD, Constants.SHARED_PREF_ALARM_FULLSCREEN_NOTIFICATION_ENABLED)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateEnableStates exception: " + exc.toString())
        }
    }

    private fun update() {
        if (!Channels.getNotificationManager(requireContext()).isNotificationPolicyAccessGranted) {
            disableSwitch(Constants.SHARED_PREF_ALARM_FORCE_SOUND)
            disableSwitch(Constants.SHARED_PREF_ALARM_FORCE_VIBRATION)
        }
    }

    private fun disableSwitch(prefname: String) {
        val pref =
            findPreference<SwitchPreferenceCompat>(prefname)
        if (pref != null && pref.isChecked) {
            Log.i(LOG_ID, "Disable preference $prefname as there is no permission!")
            pref.isChecked = false
            with(preferenceManager.sharedPreferences!!.edit()) {
                putBoolean(prefname, false)
                apply()
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        Log.d(LOG_ID, "onSharedPreferenceChanged called for " + key)
        try {
            updateEnableStates(sharedPreferences)
            if(AlarmHandler.alarmPreferencesToSend.contains(key))
                AlarmFragment.settingsChanged = true
            when(key) {
                Constants.SHARED_PREF_ALARM_FORCE_SOUND -> checkDisablePref(Constants.SHARED_PREF_ALARM_FORCE_SOUND, Constants.SHARED_PREF_ALARM_FORCE_VIBRATION)
                Constants.SHARED_PREF_ALARM_FORCE_VIBRATION -> checkDisablePref(Constants.SHARED_PREF_ALARM_FORCE_VIBRATION, Constants.SHARED_PREF_ALARM_FORCE_SOUND)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString())
        }
    }

    private fun checkDisablePref(changedPref: String, targetPref: String) {
        val changed = findPreference<SwitchPreferenceCompat>(changedPref)
        if (changed!!.isChecked) {
            if (!Channels.getNotificationManager(requireContext()).isNotificationPolicyAccessGranted) {
                requestNotificationPolicyPermission()
            } else {
                val target = findPreference<SwitchPreferenceCompat>(targetPref)
                if (target!!.isChecked) {
                    target.isChecked = false
                    with(preferenceManager.sharedPreferences!!.edit()) {
                        putBoolean(targetPref, false)
                        apply()
                    }
                }
            }
        }
    }

    private fun requestNotificationPolicyPermission() {
        if (!Channels.getNotificationManager(requireContext()).isNotificationPolicyAccessGranted) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            startActivity(intent)
        }
    }
}
