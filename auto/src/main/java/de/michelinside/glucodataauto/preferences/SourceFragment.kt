package de.michelinside.glucodataauto.preferences

import android.os.Bundle
import de.michelinside.glucodatahandler.common.utils.Log
import de.michelinside.glucodatahandler.common.R
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.preferences.PreferenceFragmentCompatBase


class SourceFragment : PreferenceFragmentCompatBase() {
    private val LOG_ID = "GDH.AA.SourceFragment"

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