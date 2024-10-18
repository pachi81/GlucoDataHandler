package de.michelinside.glucodatahandler.preferences

import android.os.Bundle
import android.util.Log
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.Constants


class SourceFragment : SettingsFragmentCompatBase() {
    private val LOG_ID = "GDH.SourceFragment"

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Log.d(LOG_ID, "onCreatePreferences called")
        try {
            preferenceManager.sharedPreferencesName = Constants.SHARED_PREF_TAG
            setPreferencesFromResource(R.xml.sources, rootKey)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreatePreferences exception: " + exc.toString())
        }
    }
}