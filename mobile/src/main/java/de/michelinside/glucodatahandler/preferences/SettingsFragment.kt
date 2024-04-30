package de.michelinside.glucodatahandler.preferences

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.preference.*
import de.michelinside.glucodatahandler.BuildConfig
import de.michelinside.glucodatahandler.Dialogs
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.R as CR


class SettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val LOG_ID = "GDH.SettingsFragment"

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Log.d(LOG_ID, "onCreatePreferences called")
        try {
            preferenceManager.sharedPreferencesName = Constants.SHARED_PREF_TAG
            setPreferencesFromResource(R.xml.preferences, rootKey)

            if (BuildConfig.DEBUG) {
                val notifySwitch =
                    findPreference<SwitchPreferenceCompat>(Constants.SHARED_PREF_DUMMY_VALUES)
                notifySwitch!!.isVisible = true
            }
            update()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreatePreferences exception: " + exc.toString())
        }
    }

    override fun onResume() {
        Log.d(LOG_ID, "onResume called")
        try {
            preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
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
            when(key) {
                Constants.SHARED_PREF_APP_COLOR_SCHEME -> {
                    update()
                    Dialogs.updateColorScheme(requireContext())
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString())
        }
    }

    private fun update() {
        val sharedPref = preferenceManager.sharedPreferences!!
        val colorSchemePref = findPreference<ListPreference>(Constants.SHARED_PREF_APP_COLOR_SCHEME)
        if(colorSchemePref != null) {
            colorSchemePref.summary = resources.getString(when (sharedPref.getString(Constants.SHARED_PREF_APP_COLOR_SCHEME, "")) {
                "dark" -> CR.string.application_color_scheme_dark
                "light" -> CR.string.application_color_scheme_light
                else -> CR.string.application_color_scheme_system
            })
        }
    }
}
