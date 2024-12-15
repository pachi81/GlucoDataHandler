package de.michelinside.glucodataauto.preferences

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.preference.*
import de.michelinside.glucodataauto.R
import de.michelinside.glucodatahandler.common.R as CR
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.preferences.PreferenceHelper


class SourceOfflineFragment : PreferenceFragmentCompat() {
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

            val prefEselLink = findPreference<Preference>(Constants.SHARED_PREF_EVERSENSE_ESEL_INFO)
            if(prefEselLink != null) {
                prefEselLink.setOnPreferenceClickListener {
                    try {
                        val browserIntent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(resources.getText(CR.string.esel_link).toString())
                        )
                        startActivity(browserIntent)
                    } catch (exc: Exception) {
                        Log.e(LOG_ID, "setLinkOnClick exception for key ${prefEselLink.key}" + exc.toString())
                    }
                    true
                }
            }

        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreatePreferences exception: " + exc.toString())
        }
    }

}