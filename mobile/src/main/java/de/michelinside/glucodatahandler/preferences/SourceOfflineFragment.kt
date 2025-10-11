package de.michelinside.glucodatahandler.preferences

import de.michelinside.glucodatahandler.common.utils.Log
import androidx.preference.*
import de.michelinside.glucodatahandler.common.ui.Dialogs
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.R as CR
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.preferences.PreferenceHelper


class SourceOfflineFragment : SettingsFragmentBase(R.xml.sources_offline) {
    private var settingsChanged = false

    override fun initPreferences() {
        Log.d(LOG_ID, "initPreferences called")
        try {
            settingsChanged = false

            setupLocalIobAction(findPreference(Constants.SHARED_PREF_SOURCE_XDRIP_SET_NS_IOB_ACTION))

            PreferenceHelper.setLinkOnClick(findPreference(Constants.SHARED_PREF_EVERSENSE_ESEL_INFO), CR.string.esel_link, requireContext())
            PreferenceHelper.setLinkOnClick(findPreference("source_juggluco_video"), CR.string.video_tutorial_juggluco, requireContext())
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreatePreferences exception: " + exc.toString())
        }
    }

    private fun setupLocalIobAction(preference: Preference?) {
        preference?.setOnPreferenceClickListener {
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