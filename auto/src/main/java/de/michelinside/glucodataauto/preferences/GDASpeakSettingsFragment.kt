package de.michelinside.glucodataauto.preferences

import android.content.SharedPreferences
import android.util.Log
import androidx.preference.Preference
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import de.michelinside.glucodataauto.GlucoDataServiceAuto
import de.michelinside.glucodataauto.R
import de.michelinside.glucodataauto.android_auto.CarMediaPlayer
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.utils.TextToSpeechUtils

class GDASpeakSettingsFragment: SettingsFragmentBase(R.xml.pref_gda_speak) {

    override fun initPreferences() {
        super.initPreferences()
        val pref = findPreference<Preference>(Constants.AA_MEDIA_PLAYER_SPEAK_TEST)
        pref?.setOnPreferenceClickListener {
            if(GlucoDataServiceAuto.connected) {
                CarMediaPlayer.play(requireContext())
            } else {
                TextToSpeechUtils.speak(ReceiveData.getAsText(requireContext(), false, false))
            }
            true
        }
    }

    override fun updateEnableStates(sharedPreferences: SharedPreferences) {
        try {
            Log.v(LOG_ID, "updateEnableStates called")
            setEnableState<SwitchPreferenceCompat>(sharedPreferences, Constants.AA_MEDIA_PLAYER_SPEAK_ALARM_ONLY, Constants.AA_MEDIA_PLAYER_SPEAK_NEW_VALUE)
            setEnableState<SeekBarPreference>(sharedPreferences, Constants.AA_MEDIA_PLAYER_SPEAK_INTERVAL, Constants.AA_MEDIA_PLAYER_SPEAK_NEW_VALUE, Constants.AA_MEDIA_PLAYER_SPEAK_ALARM_ONLY)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateEnableStates exception: " + exc.toString())
        }
    }
}