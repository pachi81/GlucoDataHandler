package de.michelinside.glucodatahandler.preferences

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.notification.AlarmHandler
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.tasks.DataSourceTask
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

    private fun update() {
        if (!AlarmNotification.hasFullscreenPermission()) {
            val pref =
                findPreference<SwitchPreferenceCompat>(Constants.SHARED_PREF_ALARM_FULLSCREEN_NOTIFICATION_ENABLED)
            if (pref != null && pref.isChecked) {
                Log.i(LOG_ID, "Disable fullscreen setting as there is no permission!")
                pref.isChecked = false
                with(preferenceManager.sharedPreferences!!.edit()) {
                    putBoolean(Constants.SHARED_PREF_ALARM_FULLSCREEN_NOTIFICATION_ENABLED, false)
                    apply()
                }
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        Log.d(LOG_ID, "onSharedPreferenceChanged called for " + key)
        try {
            if(AlarmHandler.alarmPreferencesToSend.contains(key))
                settingsChanged = true
            when(key) {
                Constants.SHARED_PREF_ALARM_FULLSCREEN_NOTIFICATION_ENABLED -> {
                    if (sharedPreferences.getBoolean(Constants.SHARED_PREF_ALARM_FULLSCREEN_NOTIFICATION_ENABLED, false) && !AlarmNotification.hasFullscreenPermission()) {
                        requestFullScreenPermission()
                    }
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString())
        }
    }

    private fun requestFullScreenPermission() {
        try {
            Log.v(LOG_ID, "requestFullScreenPermission called")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.parse("package:" + requireContext().packageName)
                )
                intent.putExtra(Settings.EXTRA_APP_PACKAGE,requireContext().packageName)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "requestOverlayPermission exception: " + exc.toString())
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

