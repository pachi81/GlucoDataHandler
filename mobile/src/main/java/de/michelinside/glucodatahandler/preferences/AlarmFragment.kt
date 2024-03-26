package de.michelinside.glucodatahandler.preferences

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Environment.DIRECTORY_NOTIFICATIONS
import android.os.Handler
import android.provider.DocumentsContract
import android.provider.Settings
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.notification.AlarmHandler
import de.michelinside.glucodatahandler.notification.AlarmNotification
import de.michelinside.glucodatahandler.common.notification.AlarmType
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.tasks.DataSourceTask


class AlarmFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val LOG_ID = "GDH.AlarmFragment"
    private var settingsChanged = false
    private var soundSaverMap: MutableMap<AlarmType, ActivityResultLauncher<Intent>> = mutableMapOf()


    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Log.d(LOG_ID, "onCreatePreferences called")
        try {
            settingsChanged = false
            preferenceManager.sharedPreferencesName = Constants.SHARED_PREF_TAG
            setPreferencesFromResource(R.xml.alarms, rootKey)

            createAlarmPrefSettings("alarm_very_low_", AlarmType.VERY_LOW)
            createAlarmPrefSettings("alarm_low_", AlarmType.LOW)
            createAlarmPrefSettings("alarm_high_", AlarmType.HIGH)
            createAlarmPrefSettings("alarm_very_high_", AlarmType.VERY_HIGH)

        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreatePreferences exception: " + exc.toString())
        }
    }

    private fun createAlarmPrefSettings(pref_prefix: String,
                                        alarmType: AlarmType) {
        soundSaverMap.put(alarmType, registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            Log.v(LOG_ID, "$alarmType result ${result.resultCode}: ${result.data}")
            if (result.resultCode == Activity.RESULT_OK) {
                val intent = result.data
                intent?.data?.also { uri ->
                    Log.v(LOG_ID, "Save media to " + uri)
                    AlarmNotification.saveAlarm(requireContext(), alarmType, uri)
                }
            }
        })
        setAlarmTest(pref_prefix + "test", alarmType)
        setAlarmSettings(pref_prefix + "settings", alarmType)
        setAlarmSave(pref_prefix + "save_sound", alarmType, soundSaverMap.get(alarmType)!!)
    }

    override fun onDestroyView() {
        Log.d(LOG_ID, "onDestroyView called")
        try {
            if (settingsChanged) {
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

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        Log.d(LOG_ID, "onSharedPreferenceChanged called for " + key)
        try {
            if(AlarmHandler.alarmPreferencesToSend.contains(key))
                settingsChanged = true
            when(key) {
                Constants.SHARED_PREF_ALARM_NOTIFICATION_ENABLED -> AlarmNotification.setEnabled(sharedPreferences.getBoolean(Constants.SHARED_PREF_ALARM_NOTIFICATION_ENABLED, true))
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString())
        }
    }


    private fun setAlarmTest(preference: String, alarmType: AlarmType) {
        val pref = findPreference<Preference>(preference)
        if (pref != null) {
            pref.setOnPreferenceClickListener {
                Log.d(LOG_ID, "Trigger test for $alarmType")
                pref.isEnabled = false
                Thread {
                    Thread.sleep(5*1000)
                    Handler(requireContext().mainLooper).post {
                        AlarmNotification.triggerNotification(alarmType, requireContext(), true)
                        pref.isEnabled = true
                    }
                }.start()
                true
            }
        }
    }

    private fun setAlarmSettings(preference: String, alarmType: AlarmType) {
        val pref = findPreference<Preference>(preference)
        if (pref != null) {
            pref.setOnPreferenceClickListener {
                Log.d(LOG_ID, "Open settings for $alarmType")
                val intent: Intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, AlarmNotification.getChannelId(alarmType))
                startActivity(intent)
                true
            }
        }
    }

    private fun getAlarmFileName(resId: Int): String {
        val name = requireContext().resources.getResourceEntryName(resId).replace("gdh", "GDH").replace("_", " ")
        return "$name.mp3"
    }

    private fun setAlarmSave(
        preference: String,
        alarmType: AlarmType,
        soundSaver: ActivityResultLauncher<Intent>
    ) {
        val pref = findPreference<Preference>(preference)
        val resId = AlarmNotification.getAlarmSoundRes(alarmType)
        if (pref != null && resId != null) {
            pref.setOnPreferenceClickListener {
                var alarmUri: Uri? = null
                MediaScannerConnection.scanFile(requireContext(), arrayOf(Environment.getExternalStoragePublicDirectory(
                    DIRECTORY_NOTIFICATIONS).absolutePath), null
                ) { s: String, uri: Uri ->
                    Log.v(LOG_ID, "Set URI $uri for path $s")
                    alarmUri = uri
                }
                Log.d(LOG_ID, "Save sound for $alarmType to ${alarmUri}")
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "audio/mpeg"
                    val fileName = getAlarmFileName(resId)
                    putExtra(Intent.EXTRA_TITLE, fileName)
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, alarmUri)
                }
                soundSaver.launch(intent)
                true
            }
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
