package de.michelinside.glucodataauto.preferences

import android.content.SharedPreferences
import android.util.Log
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import de.michelinside.glucodataauto.R
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.utils.TextToSpeechUtils

class GDAMediaSettingsFragment: SettingsFragmentBase(R.xml.pref_gda_media) {

    override fun initPreferences() {
        Log.d(LOG_ID, "GDAMediaSettingsFragment initPreferences called")
        super.initPreferences()
        if(!TextToSpeechUtils.isAvailable()) {
            val pref = findPreference<SwitchPreferenceCompat>(Constants.AA_MEDIA_PLAYER_SPEAK_VALUES)
            if(pref!!.isChecked) {
                pref.isChecked = false
                preferenceManager.sharedPreferences?.edit()?.putBoolean(Constants.AA_MEDIA_PLAYER_SPEAK_VALUES, false)?.apply()
            }
            pref.isEnabled = false
        }
    }


    override fun updateEnableStates(sharedPreferences: SharedPreferences) {
        try {
            Log.v(LOG_ID, "updateEnableStates called")
            val intervalPref = findPreference<SeekBarPreference>(Constants.AA_MEDIA_PLAYER_DURATION)
            if(intervalPref != null) {
                intervalPref.isEnabled = !sharedPreferences.getBoolean(Constants.AA_MEDIA_PLAYER_SPEAK_VALUES, false)
                updateEnablePrefs.add(Constants.AA_MEDIA_PLAYER_SPEAK_VALUES)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateEnableStates exception: " + exc.toString())
        }
    }
}