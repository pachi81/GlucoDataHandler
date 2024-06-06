package de.michelinside.glucodataauto.preferences

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.preference.*
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodataauto.R
import de.michelinside.glucodatahandler.common.notification.ChannelType
import de.michelinside.glucodatahandler.common.notification.Channels


abstract class SettingsFragmentBase(private val prefResId: Int) : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {
    protected val LOG_ID = "GDH.SettingsFragmentBase"
    private val updateEnablePrefs = mutableSetOf<String>()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Log.d(LOG_ID, "onCreatePreferences called")
        try {
            preferenceManager.sharedPreferencesName = Constants.SHARED_PREF_TAG
            setPreferencesFromResource(prefResId, rootKey)

            initPreferences()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreatePreferences exception: " + exc.toString())
        }
    }

    open fun initPreferences() {
    }


    open fun updatePreferences() {

    }

    override fun onResume() {
        Log.d(LOG_ID, "onResume called")
        try {
            super.onResume()
            preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
            updateEnablePrefs.clear()
            update()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onResume exception: " + exc.toString())
        }
    }

    override fun onPause() {
        Log.d(LOG_ID, "onPause called")
        try {
            super.onPause()
            preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onPause exception: " + exc.toString())
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        Log.d(LOG_ID, "onSharedPreferenceChanged called for " + key)
        try {
            if(updateEnablePrefs.isEmpty() || updateEnablePrefs.contains(key!!)) {
                updateEnableStates(sharedPreferences!!)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString())
        }
    }


    fun <T : Preference?> setEnableState(sharedPreferences: SharedPreferences, key: String, enableKey: String, secondEnableKey: String? = null, defValue: Boolean = false) {
        val pref = findPreference<T>(key)
        if (pref != null) {
            pref.isEnabled = sharedPreferences.getBoolean(enableKey, defValue) && (if (secondEnableKey != null) !sharedPreferences.getBoolean(secondEnableKey, defValue) else true)
            if(!updateEnablePrefs.contains(enableKey)) {
                Log.v(LOG_ID, "Add update enable pref $enableKey")
                updateEnablePrefs.add(enableKey)
            }
            if (secondEnableKey != null && !updateEnablePrefs.contains(secondEnableKey)) {
                Log.v(LOG_ID, "Add update second enable pref $secondEnableKey")
                updateEnablePrefs.add(secondEnableKey)
            }
        }
    }

    private fun update() {
        val sharedPref = preferenceManager.sharedPreferences!!
        updateEnableStates(sharedPref)
        updatePreferences()
    }


    fun updateEnableStates(sharedPreferences: SharedPreferences) {
        try {
            Log.v(LOG_ID, "updateEnableStates called")
            setEnableState<SwitchPreferenceCompat>(sharedPreferences, Constants.SHARED_PREF_CAR_NOTIFICATION_ALARM_ONLY, Constants.SHARED_PREF_CAR_NOTIFICATION)
            setEnableState<SeekBarPreference>(sharedPreferences, Constants.SHARED_PREF_CAR_NOTIFICATION_INTERVAL_NUM, Constants.SHARED_PREF_CAR_NOTIFICATION, Constants.SHARED_PREF_CAR_NOTIFICATION_ALARM_ONLY)
            setEnableState<SeekBarPreference>(sharedPreferences, Constants.SHARED_PREF_CAR_NOTIFICATION_REAPPEAR_INTERVAL, Constants.SHARED_PREF_CAR_NOTIFICATION, Constants.SHARED_PREF_CAR_NOTIFICATION_ALARM_ONLY)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateEnableStates exception: " + exc.toString())
        }
    }
}

class GeneralSettingsFragment: SettingsFragmentBase(R.xml.pref_general) {}
class RangeSettingsFragment: SettingsFragmentBase(R.xml.pref_target_range) {}
class GDASettingsFragment: SettingsFragmentBase(R.xml.pref_gda) {
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

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        if(key == Constants.SHARED_PREF_CAR_NOTIFICATION) {
            if (preferenceManager.sharedPreferences!!.getBoolean(Constants.SHARED_PREF_CAR_NOTIFICATION, false) && !Channels.notificationChannelActive(requireContext(), ChannelType.ANDROID_AUTO)) {
                val intent: Intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                if (Channels.notificationActive(requireContext())) // only the channel is inactive!
                    intent.putExtra(Settings.EXTRA_CHANNEL_ID, ChannelType.ANDROID_AUTO.channelId)
                startActivity(intent)
            } else {
                updatePreferences()
            }
        }
    }

}