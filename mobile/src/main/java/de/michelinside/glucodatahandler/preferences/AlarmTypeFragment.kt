package de.michelinside.glucodatahandler.preferences

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.provider.DocumentsContract
import android.provider.Settings
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.notification.AlarmHandler
import de.michelinside.glucodatahandler.common.notification.AlarmType
import de.michelinside.glucodatahandler.common.utils.Utils
import de.michelinside.glucodatahandler.notification.AlarmNotification

class AlarmTypeFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val LOG_ID = "GDH.AlarmTypeFragment"
    private lateinit var soundSaver: ActivityResultLauncher<Intent>
    private var alarmType = AlarmType.NONE
    private var pref_prefix = ""
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        try {
            Log.v(LOG_ID, "onCreatePreferences called for key: ${Utils.dumpBundle(this.arguments)}" )
            preferenceManager.sharedPreferencesName = Constants.SHARED_PREF_TAG
            setPreferencesFromResource(R.xml.alarm_type, rootKey)
            if (requireArguments().containsKey("prefix") && requireArguments().containsKey("type")) {
                alarmType = AlarmType.fromIndex(requireArguments().getInt("type"))
                pref_prefix = requireArguments().getString("prefix")!!
                createAlarmPrefSettings()
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreatePreferences exception: " + exc.toString())
        }
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

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        Log.d(LOG_ID, "onSharedPreferenceChanged called for " + key)
        try {
            if(AlarmHandler.alarmPreferencesToSend.contains(key))
                AlarmFragment.settingsChanged = true
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString())
        }
    }

    private fun update() {
        Log.d(LOG_ID, "update called")
        try {
            val pref = findPreference<Preference>(pref_prefix + "settings")
            if (pref != null) {
                pref.icon = ContextCompat.getDrawable(
                    requireContext(),
                    AlarmNotification.getSoundMode(alarmType).icon
                )
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onPause exception: " + exc.toString())
        }
    }

    private fun createAlarmPrefSettings() {
        Log.v(LOG_ID, "createAlarmPrefSettings for alarm $alarmType with prefix $pref_prefix")
        updatePreferenceKeys(pref_prefix)
        updateData(pref_prefix, alarmType)
        soundSaver = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            Log.v(LOG_ID, "$alarmType result ${result.resultCode}: ${result.data}")
            if (result.resultCode == Activity.RESULT_OK) {
                val intent = result.data
                intent?.data?.also { uri ->
                    Log.v(LOG_ID, "Save media to " + uri)
                    AlarmNotification.saveAlarm(requireContext(), alarmType, uri)
                }
            }
        }
        setAlarmTest(pref_prefix + "test", alarmType)
        setAlarmSettings(pref_prefix + "settings", alarmType)
        setAlarmSave(pref_prefix + "save_sound", alarmType, soundSaver)
    }

    private fun updatePreferenceKeys(pref_prefix: String) {
        for (i in 0 until preferenceScreen.getPreferenceCount()) {
            val pref: Preference = preferenceScreen.getPreference(i)
            val newKey = pref_prefix + pref.key
            Log.v(LOG_ID, "Replace key ${pref.key} with $newKey")
            pref.key = newKey
        }
    }
    private fun updateData(pref_prefix: String, alarmType: AlarmType) {
        val enablePref = findPreference<SwitchPreferenceCompat>(pref_prefix+"enabled")
        enablePref!!.isChecked = preferenceManager.sharedPreferences!!.getBoolean(enablePref.key, true)

        val intervalPref = findPreference<SeekBarPreference>(pref_prefix+"interval")
        intervalPref!!.value = preferenceManager.sharedPreferences!!.getInt(intervalPref.key, AlarmHandler.getDefaultIntervalMin(alarmType))
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
            pref.icon = ContextCompat.getDrawable(requireContext(), AlarmNotification.getSoundMode(alarmType).icon)
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
                MediaScannerConnection.scanFile(requireContext(), arrayOf(
                    Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_NOTIFICATIONS
                    ).absolutePath), null
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
}
