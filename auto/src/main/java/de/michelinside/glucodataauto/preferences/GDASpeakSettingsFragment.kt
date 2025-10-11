package de.michelinside.glucodataauto.preferences

import android.content.SharedPreferences
import de.michelinside.glucodatahandler.common.utils.Log
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import de.michelinside.glucodataauto.GlucoDataServiceAuto
import de.michelinside.glucodataauto.R
import de.michelinside.glucodatahandler.common.R as CR
import de.michelinside.glucodataauto.android_auto.CarMediaPlayer
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.utils.TextToSpeechUtils

class GDASpeakSettingsFragment: SettingsFragmentBase(R.xml.pref_gda_speak) {

    override fun initPreferences() {
        super.initPreferences()
        val prefTest = findPreference<Preference>(Constants.AA_MEDIA_PLAYER_SPEAK_TEST)
        prefTest?.setOnPreferenceClickListener {
            if(GlucoDataServiceAuto.connected) {
                CarMediaPlayer.play(requireContext())
            } else {
                var text = ReceiveData.getAsText(requireContext(), true, false)
                if(!GlucoDataServiceAuto.patientName.isNullOrEmpty()) {
                    text = "${GlucoDataServiceAuto.patientName}, $text"
                }
                TextToSpeechUtils.speak(text)
            }
            true
        }
        if(!TextToSpeechUtils.isAvailable()) {
            val prefSpeak = findPreference<SwitchPreferenceCompat>(Constants.AA_MEDIA_PLAYER_SPEAK_NEW_VALUE)
            if(prefSpeak!!.isChecked) {
                prefSpeak.isChecked = false
                preferenceManager.sharedPreferences?.edit()?.putBoolean(Constants.AA_MEDIA_PLAYER_SPEAK_NEW_VALUE, false)?.apply()
            }
            prefSpeak.isEnabled = false
            prefTest!!.isEnabled = false
        }
    }

    override fun updateEnableStates(sharedPreferences: SharedPreferences) {
        try {
            Log.v(LOG_ID, "updateEnableStates called")
            setEnableState<SwitchPreferenceCompat>(sharedPreferences, Constants.AA_MEDIA_PLAYER_SPEAK_ALARM_ONLY, Constants.AA_MEDIA_PLAYER_SPEAK_NEW_VALUE)
            setEnableState<SeekBarPreference>(sharedPreferences, Constants.AA_MEDIA_PLAYER_SPEAK_INTERVAL, Constants.AA_MEDIA_PLAYER_SPEAK_NEW_VALUE, Constants.AA_MEDIA_PLAYER_SPEAK_ALARM_ONLY, invertSecond = true)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateEnableStates exception: " + exc.toString())
        }
    }

    override fun updatePreferences() {
        super.updatePreferences()
        val prefSpeak = findPreference<SwitchPreferenceCompat>(Constants.AA_MEDIA_PLAYER_SPEAK_NEW_VALUE) ?: return
        prefSpeak.icon = ContextCompat.getDrawable(requireContext(), if(prefSpeak.isChecked) CR.drawable.icon_volume_normal else CR.drawable.icon_volume_off)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        if(key == Constants.AA_MEDIA_PLAYER_SPEAK_NEW_VALUE) {
            updatePreferences()
        }
    }

}