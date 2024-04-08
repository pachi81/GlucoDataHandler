package de.michelinside.glucodatahandler.preferences

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.R as CR
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.notification.AlarmHandler
import de.michelinside.glucodatahandler.common.notification.AlarmType
import de.michelinside.glucodatahandler.common.notification.SoundMode
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.tasks.DataSourceTask
import de.michelinside.glucodatahandler.common.utils.Utils
import de.michelinside.glucodatahandler.notification.AlarmNotification


class AlarmFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val LOG_ID = "GDH.AlarmFragment"
    companion object {
        var settingsChanged = false
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Log.d(LOG_ID, "onCreatePreferences called")
        try {
            settingsChanged = false
            preferenceManager.sharedPreferencesName = Constants.SHARED_PREF_TAG
            setPreferencesFromResource(R.xml.alarms, rootKey)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreatePreferences exception: " + exc.toString())
        }
    }

    override fun onDestroyView() {
        Log.d(LOG_ID, "onDestroyView called")
        try {
            if (settingsChanged) {
                Log.v(LOG_ID, "Notify alarm_settings change")
                InternalNotifier.notify(requireContext(), NotifySource.ALARM_SETTINGS, DataSourceTask.getSettingsBundle(preferenceManager.sharedPreferences!!))
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onDestroyView exception: " + exc.toString())
        }
        super.onDestroyView()
    }


    override fun onResume() {
        Log.d(LOG_ID, "onResume called")
        try {
            preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
            update()
            super.onResume()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onResume exception: " + exc.toString())
        }
    }

    override fun onPause() {
        Log.d(LOG_ID, "onPause called")
        try {
            preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
            super.onPause()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onPause exception: " + exc.toString())
        }
    }

    @SuppressLint("InlinedApi")
    private fun update() {
        Log.d(LOG_ID, "update called")
        try {
            val prefAlarmEnabled = findPreference<SwitchPreferenceCompat>(Constants.SHARED_PREF_ALARM_NOTIFICATION_ENABLED)
            if(prefAlarmEnabled != null) {
                prefAlarmEnabled.isEnabled = Utils.checkPermission(requireContext(), android.Manifest.permission.POST_NOTIFICATIONS, Build.VERSION_CODES.TIRAMISU)
                if(!prefAlarmEnabled.isEnabled && prefAlarmEnabled.isChecked) {
                    prefAlarmEnabled.isChecked = false
                    with(preferenceManager.sharedPreferences!!.edit()) {
                        putBoolean(Constants.SHARED_PREF_ALARM_NOTIFICATION_ENABLED, false)
                        apply()
                    }
                }

            }
            updateAlarmCat(Constants.SHARED_PREF_ALARM_VERY_LOW)
            updateAlarmCat(Constants.SHARED_PREF_ALARM_LOW)
            updateAlarmCat(Constants.SHARED_PREF_ALARM_HIGH)
            updateAlarmCat(Constants.SHARED_PREF_ALARM_VERY_HIGH)
            updateAlarmCat(Constants.SHARED_PREF_ALARM_OBSOLETE)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onPause exception: " + exc.toString())
        }
    }

    private fun updateAlarmCat(key: String) {
        val pref = findPreference<Preference>(key) ?: return
        val alarmType = AlarmType.fromIndex(pref.extras.getInt("type"))
        pref.icon = ContextCompat.getDrawable(requireContext(), getAlarmCatIcon(alarmType, key + "_enabled"))
        pref.summary = getAlarmCatSummary(alarmType)
    }

    private fun getAlarmCatIcon(alarmType: AlarmType, enableKey: String): Int {
        if(!preferenceManager.sharedPreferences!!.getBoolean(enableKey, true)) {
            return SoundMode.OFF.icon
        }
        return AlarmNotification.getSoundMode(alarmType).icon
    }

    private fun getAlarmCatSummary(alarmType: AlarmType): String {
        return when(alarmType) {
            AlarmType.VERY_LOW,
            AlarmType.LOW,
            AlarmType.HIGH,
            AlarmType.VERY_HIGH -> resources.getString(CR.string.alarm_type_summary, getBorderText(alarmType))
            AlarmType.OBSOLETE -> resources.getString(CR.string.alarm_obsolete_summary)
            else -> ""
        }
    }

    private fun getBorderText(alarmType: AlarmType): String {
        var value = when(alarmType) {
            AlarmType.VERY_LOW -> ReceiveData.low
            AlarmType.LOW -> ReceiveData.targetMin
            AlarmType.HIGH -> ReceiveData.targetMax
            AlarmType.VERY_HIGH -> ReceiveData.high
            else -> 0F
        }

        if (alarmType == AlarmType.LOW)
            value -= if(ReceiveData.isMmol) 0.1F else 1F

        if (alarmType == AlarmType.HIGH)
            value += if(ReceiveData.isMmol) 0.1F else 1F

        return "$value ${ReceiveData.getUnit()}"
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        Log.d(LOG_ID, "onSharedPreferenceChanged called for " + key)
        try {
            if(AlarmHandler.alarmPreferencesToSend.contains(key))
                settingsChanged = true
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString())
        }
    }


    /*
    private fun setRingtoneSelect(
        preference: String,
        picker: ActivityResultLauncher<Intent>,
        curUir: Uri?
    ) {
        Log.v(LOG_ID, "setRingtoneSelect called for preference $preference with uri $curUir" )
        val pref = findPreference<Preference>(preference)
        if (pref != null) {
            if (curUir != null && curUir.toString().isNotEmpty()) {
                val ringtone = RingtoneManager.getRingtone(requireContext(), curUir)
                val title = ringtone.getTitle(requireContext())
                if (title.isNullOrEmpty()) {
                    pref.summary = resources.getString(de.michelinside.glucodatahandler.common.R.string.alarm_sound_summary)
                } else {
                    Log.d(LOG_ID, "Ringtone '$title' for uri $curUir")
                    pref.summary = title
                }
            } else {
                pref.summary = resources.getString(de.michelinside.glucodatahandler.common.R.string.alarm_sound_summary)
            }
            pref.setOnPreferenceClickListener {
                val ringtoneIntent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                ringtoneIntent.putExtra(
                    RingtoneManager.EXTRA_RINGTONE_TYPE,
                    RingtoneManager.TYPE_ALL
                )
                ringtoneIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, curUir)
                picker.launch(ringtoneIntent)
                true
            }
        }
    }

    private fun setRingtoneResult(preference: String, picker: ActivityResultLauncher<Intent>, newUri: Uri?) {
        Log.i(LOG_ID, "Set custom ringtone for $preference: $newUri")
        with (preferenceManager.sharedPreferences!!.edit()) {
            putString(preference, newUri?.toString())
            apply()
        }
        setRingtoneSelect(preference, picker, newUri)
    }
    */

}

