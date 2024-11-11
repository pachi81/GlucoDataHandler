package de.michelinside.glucodataauto.preferences

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.preference.*
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodataauto.R


abstract class SettingsFragmentBase(private val prefResId: Int) : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {
    protected val LOG_ID = "GDH.AA.SettingsFragmentBase"
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


    open fun updateEnableStates(sharedPreferences: SharedPreferences) {}
}

class GeneralSettingsFragment: SettingsFragmentBase(R.xml.pref_general) {}
class RangeSettingsFragment: SettingsFragmentBase(R.xml.pref_target_range) {}
