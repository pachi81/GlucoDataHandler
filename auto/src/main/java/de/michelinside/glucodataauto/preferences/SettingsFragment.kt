package de.michelinside.glucodataauto.preferences

import android.os.Bundle
import android.util.Log
import androidx.preference.*
import de.michelinside.glucodataauto.BuildConfig
import de.michelinside.glucodataauto.R
import de.michelinside.glucodatahandler.common.Constants


class SettingsFragment : PreferenceFragmentCompat() {
    private val LOG_ID = "GDH.AA.SettingsFragment"

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
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreatePreferences exception: " + exc.toString())
        }
    }

}
