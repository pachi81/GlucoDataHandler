package de.michelinside.glucodatahandler

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.XDripBroadcastReceiver

class SettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val LOG_ID = "GlucoDataHandler.SettingsFragment"
    private var minValueEdit: GlucoseEditPreference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Log.d(LOG_ID, "onCreatePreferences called")
        preferenceManager.sharedPreferencesName = Constants.SHARED_PREF_TAG
        setPreferencesFromResource(R.xml.preferences, rootKey)

        minValueEdit = findPreference<GlucoseEditPreference>(Constants.SHARED_PREF_TARGET_MIN)
        if (BuildConfig.DEBUG) {
            val notifySwitch =
                findPreference<SwitchPreferenceCompat>(Constants.SHARED_PREF_NOTIFICATION)
            notifySwitch!!.isVisible = true
        }
    }

    override fun onResume() {
        Log.d(LOG_ID, "onResume called")
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        super.onResume()
    }

    override fun onPause() {
        Log.d(LOG_ID, "onPause called")
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        Log.d(LOG_ID, "onSharedPreferenceChanged called for " + key)
        when(key) {
            Constants.SHARED_PREF_USE_MMOL,
            Constants.SHARED_PREF_TARGET_MIN,
            Constants.SHARED_PREF_TARGET_MAX -> {
                ReceiveData.updateSettings(sharedPreferences!!)
            }
            Constants.SHARED_PREF_CAR_NOTIFICATION -> {
                CarModeReceiver.updateSettings(sharedPreferences!!)
            }
            Constants.SHARED_PREF_LOW_GLUCOSE,
            Constants.SHARED_PREF_HIGH_GLUCOSE -> {
                XDripBroadcastReceiver.updateSettings(sharedPreferences!!)
            }

        }
    }
}