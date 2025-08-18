package de.michelinside.glucodataauto.preferences

import android.os.Bundle
import android.util.Log
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodataauto.R
import de.michelinside.glucodatahandler.common.preferences.PreferenceFragmentCompatBase


class HelpFragment() : PreferenceFragmentCompatBase() {
    protected val LOG_ID = "GDH.AA.HelpFragment"

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Log.d(LOG_ID, "onCreatePreferences called")
        try {
            preferenceManager.sharedPreferencesName = Constants.SHARED_PREF_TAG
            setPreferencesFromResource(R.xml.help, rootKey)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreatePreferences exception: " + exc.toString())
        }
    }
}