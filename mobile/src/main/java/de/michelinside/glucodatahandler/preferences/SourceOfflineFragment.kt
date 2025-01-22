package de.michelinside.glucodatahandler.preferences

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.preference.*
import de.michelinside.glucodatahandler.common.ui.Dialogs
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.R as CR
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.preferences.PreferenceHelper


class SourceOfflineFragment : SettingsFragmentCompatBase() {
    private val LOG_ID = "GDH.SourceFragment"
    private var settingsChanged = false

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Log.d(LOG_ID, "onCreatePreferences called")
        try {
            settingsChanged = false
            preferenceManager.sharedPreferencesName = Constants.SHARED_PREF_TAG
            setPreferencesFromResource(R.xml.sources_offline, rootKey)

            setupLocalIobAction(findPreference(Constants.SHARED_PREF_SOURCE_JUGGLUCO_SET_NS_IOB_ACTION))
            setupLocalIobAction(findPreference(Constants.SHARED_PREF_SOURCE_XDRIP_SET_NS_IOB_ACTION))

            PreferenceHelper.setLinkOnClick(findPreference(Constants.SHARED_PREF_EVERSENSE_ESEL_INFO), CR.string.esel_link, requireContext())
            PreferenceHelper.setLinkOnClick(findPreference("source_juggluco_video"), CR.string.video_tutorial_juggluco, requireContext())
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreatePreferences exception: " + exc.toString())
        }
    }

    private fun setupLocalIobAction(preference: Preference?) {
        if(preference != null) {
            preference.setOnPreferenceClickListener {
                Dialogs.showOkCancelDialog(requireContext(),
                    CR.string.activate_local_nightscout_iob_title,
                    CR.string.activate_local_nightscout_iob_message,
                    { _, _ -> with(preferenceManager!!.sharedPreferences!!.edit()) {
                        putString(Constants.SHARED_PREF_NIGHTSCOUT_URL, "http://127.0.0.1:17580")
                        putString(Constants.SHARED_PREF_NIGHTSCOUT_SECRET, "")
                        putString(Constants.SHARED_PREF_NIGHTSCOUT_TOKEN, "")
                        apply()
                    }
                        with(preferenceManager!!.sharedPreferences!!.edit()) {
                        putBoolean(Constants.SHARED_PREF_NIGHTSCOUT_IOB_COB, true)
                        putBoolean(Constants.SHARED_PREF_NIGHTSCOUT_ENABLED, true)
                        apply()
                    }
                })
                true
            }
        }
    }
}