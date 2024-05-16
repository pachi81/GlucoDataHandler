package de.michelinside.glucodataauto.preferences

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.preference.*
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodataauto.R


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

    override fun onResume() {
        Log.d(LOG_ID, "onResume called")
        try {
            preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
            updateEnablePrefs.clear()
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
                Log.v(LOG_ID, "Add update enable pref $enableKey - second: $secondEnableKey")
                updateEnablePrefs.add(enableKey)
                if (secondEnableKey != null)
                    updateEnablePrefs.add(secondEnableKey)
            }
        }
    }

    private fun update() {
        val sharedPref = preferenceManager.sharedPreferences!!
        updateEnableStates(sharedPref)
    }


    fun updateEnableStates(sharedPreferences: SharedPreferences) {
        try {
            setEnableState<SwitchPreference>(sharedPreferences, Constants.SHARED_PREF_CAR_NOTIFICATION_ALARM_ONLY, Constants.SHARED_PREF_CAR_NOTIFICATION)
            setEnableState<SeekBarPreference>(sharedPreferences, Constants.SHARED_PREF_CAR_NOTIFICATION_INTERVAL_NUM, Constants.SHARED_PREF_CAR_NOTIFICATION, Constants.SHARED_PREF_CAR_NOTIFICATION_ALARM_ONLY)
            setEnableState<SeekBarPreference>(sharedPreferences, Constants.SHARED_PREF_CAR_NOTIFICATION_REAPPEAR_INTERVAL, Constants.SHARED_PREF_CAR_NOTIFICATION, Constants.SHARED_PREF_CAR_NOTIFICATION_ALARM_ONLY)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateEnableStates exception: " + exc.toString())
        }
    }
}

class GeneralSettingsFragment: SettingsFragmentBase(R.xml.pref_general) {}
class RangeSettingsFragment: SettingsFragmentBase(R.xml.pref_target_range) {}
class GDASettingsFragment: SettingsFragmentBase(R.xml.pref_gda) {}