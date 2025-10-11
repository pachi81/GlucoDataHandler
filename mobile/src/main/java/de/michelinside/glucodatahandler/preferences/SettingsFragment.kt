package de.michelinside.glucodatahandler.preferences

import android.os.Bundle
import de.michelinside.glucodatahandler.common.utils.Log
import androidx.preference.*
import de.michelinside.glucodatahandler.BuildConfig
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.preferences.PreferenceHelper


class SettingsFragment : SettingsFragmentCompatBase() {
    private val LOG_ID = "GDH.SettingsFragment"

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Log.d(LOG_ID, "onCreatePreferences called")
        try {
            preferenceManager.sharedPreferencesName = Constants.SHARED_PREF_TAG
            setPreferencesFromResource(R.xml.preferences, rootKey)

            PreferenceHelper.replaceSecondSummary(findPreference("pref_cat_android_auto"))
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
