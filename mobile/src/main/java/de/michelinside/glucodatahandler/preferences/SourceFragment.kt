package de.michelinside.glucodatahandler.preferences

import android.os.Bundle
import de.michelinside.glucodatahandler.common.utils.Log
import de.michelinside.glucodatahandler.common.R
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.preferences.PreferenceHelper


class SourceFragment : SettingsFragmentCompatBase() {
    private val LOG_ID = "GDH.SourceFragment"

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Log.d(LOG_ID, "onCreatePreferences called")
        try {
            preferenceManager.sharedPreferencesName = Constants.SHARED_PREF_TAG
            setPreferencesFromResource(R.xml.sources, rootKey)

            PreferenceHelper.setLinkOnClick(findPreference(Constants.SHARED_PREF_SOURCE_HELP), de.michelinside.glucodatahandler.common.R.string.help_link, requireContext())
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreatePreferences exception: " + exc.toString())
        }
    }
}