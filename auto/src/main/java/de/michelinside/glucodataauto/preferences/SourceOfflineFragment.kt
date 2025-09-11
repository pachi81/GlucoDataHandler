package de.michelinside.glucodataauto.preferences

import android.os.Bundle
import android.util.Log
import androidx.preference.*
import de.michelinside.glucodataauto.R
import de.michelinside.glucodatahandler.common.R as CR
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.preferences.PreferenceFragmentCompatBase
import de.michelinside.glucodatahandler.common.preferences.PreferenceHelper


class SourceOfflineFragment : PreferenceFragmentCompatBase() {
    private val LOG_ID = "GDH.AA.SourceOfflineFragment"
    private var settingsChanged = false

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Log.d(LOG_ID, "onCreatePreferences called")
        try {
            settingsChanged = false
            preferenceManager.sharedPreferencesName = Constants.SHARED_PREF_TAG
            setPreferencesFromResource(R.xml.sources_offline, rootKey)

            PreferenceHelper.replaceSecondTitle(findPreference("cat_gdh_info"))
            PreferenceHelper.replaceSecondSummary(findPreference("source_gdh_info"))

            PreferenceHelper.setLinkOnClick(findPreference<Preference>(Constants.SHARED_PREF_EVERSENSE_ESEL_INFO), CR.string.esel_link, requireContext())
            PreferenceHelper.setLinkOnClick(findPreference("source_juggluco_video"), CR.string.video_tutorial_juggluco, requireContext())
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreatePreferences exception: " + exc.toString())
        }
    }

}