package de.michelinside.glucodataauto.preferences

import android.content.SharedPreferences
import android.util.Log
import androidx.preference.*
import de.michelinside.glucodataauto.R
import de.michelinside.glucodatahandler.common.R as CR
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.preferences.PreferenceHelper
import de.michelinside.glucodatahandler.common.receiver.GlucoseDataReceiver


class SourceOfflineFragment : SettingsFragmentBase(R.xml.sources_offline) {
    override val LOG_ID = "GDH.AA.SourceOfflineFragment"
    private var settingsChanged = false

    override fun initPreferences() {
        Log.d(LOG_ID, "initPreferences called")
        try {
            settingsChanged = false

            PreferenceHelper.replaceSecondTitle(findPreference("cat_gdh_info"))
            PreferenceHelper.replaceSecondSummary(findPreference("source_gdh_info"))

            PreferenceHelper.setLinkOnClick(findPreference<Preference>(Constants.SHARED_PREF_EVERSENSE_ESEL_INFO), CR.string.esel_link, requireContext())
            PreferenceHelper.setLinkOnClick(findPreference("source_juggluco_video"), CR.string.video_tutorial_juggluco, requireContext())
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreatePreferences exception: " + exc.toString())
        }
    }


    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        Log.d(LOG_ID, "onSharedPreferenceChanged called for " + key)
        try {
            super.onSharedPreferenceChanged(sharedPreferences, key)
            when(key) {
                Constants.SHARED_PREF_SOURCE_JUGGLUCO_WEBSERVER_ENABLED -> {
                    // update last 24 hours to fill data
                    GlucoseDataReceiver.resetLastServerTime()
                    GlucoseDataReceiver.checkHandleWebServerRequests(requireContext(), true)
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString())
        }
    }

    override fun updateEnableStates(sharedPreferences: SharedPreferences) {
        try {
            Log.d(LOG_ID, "updateEnableStates called")

            setEnableState<SwitchPreferenceCompat>(sharedPreferences, Constants.SHARED_PREF_SOURCE_JUGGLUCO_WEBSERVER_ENABLED, Constants.SHARED_PREF_SOURCE_JUGGLUCO_ENABLED)
            setEnableState<SwitchPreferenceCompat>(sharedPreferences, Constants.SHARED_PREF_SOURCE_JUGGLUCO_WEBSERVER_IOB_SUPPORT, Constants.SHARED_PREF_SOURCE_JUGGLUCO_WEBSERVER_ENABLED, Constants.SHARED_PREF_SOURCE_JUGGLUCO_ENABLED)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateEnableStates exception: " + exc.toString())
        }
    }

}