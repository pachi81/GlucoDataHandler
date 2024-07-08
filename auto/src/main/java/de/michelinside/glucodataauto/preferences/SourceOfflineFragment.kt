package de.michelinside.glucodataauto.preferences

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.preference.*
import de.michelinside.glucodataauto.R
import de.michelinside.glucodatahandler.common.R as CR
import de.michelinside.glucodatahandler.common.Constants


class SourceOfflineFragment : PreferenceFragmentCompat() {
    private val LOG_ID = "GDH.AA.SourceOfflineFragment"
    private var settingsChanged = false

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Log.d(LOG_ID, "onCreatePreferences called")
        try {
            settingsChanged = false
            preferenceManager.sharedPreferencesName = Constants.SHARED_PREF_TAG
            setPreferencesFromResource(R.xml.sources_offline, rootKey)

            val prefEselLink = findPreference<Preference>(Constants.SHARED_PREF_EVERSENSE_ESEL_INFO)
            if(prefEselLink != null) {
                prefEselLink.setOnPreferenceClickListener {
                    val browserIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(resources.getText(CR.string.esel_link).toString())
                    )
                    startActivity(browserIntent)
                    true
                }
            }

        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreatePreferences exception: " + exc.toString())
        }
    }

}