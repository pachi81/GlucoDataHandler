package de.michelinside.glucodataauto.preferences

import android.util.Log
import de.michelinside.glucodataauto.R

class GDAMediaSettingsFragment: SettingsFragmentBase(R.xml.pref_gda_media) {

    override fun initPreferences() {
        Log.d(LOG_ID, "GDAMediaSettingsFragment initPreferences called")
        super.initPreferences()
    }

}