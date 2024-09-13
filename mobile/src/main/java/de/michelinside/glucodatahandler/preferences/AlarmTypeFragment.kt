package de.michelinside.glucodatahandler.preferences

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.MediaScannerConnection
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.takisoft.preferencex.TimePickerPreference
import com.takisoft.preferencex.TimePickerPreferenceDialogFragmentCompat
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.R as CR
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.notification.AlarmHandler
import de.michelinside.glucodatahandler.common.notification.AlarmType
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.Utils
import de.michelinside.glucodatahandler.notification.AlarmNotification

class AlarmTypeFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener, NotifierInterface {
    private val LOG_ID = "GDH.AlarmTypeFragment"
    private lateinit var soundSaver: ActivityResultLauncher<Intent>
    private var ringtoneSelecter: ActivityResultLauncher<Intent>? = null
    private var alarmType = AlarmType.NONE
    private var curAlarmLevel = -1

    private fun getPrefKey(suffix: String): String {
        return alarmType.setting!!.getSettingName(suffix)
    }

    private val enabledPref: String get() {
        return getPrefKey(Constants.SHARED_PREF_ALARM_SUFFIX_ENABLED)
    }
    private val intervalPref: String get() {
        return getPrefKey(Constants.SHARED_PREF_ALARM_SUFFIX_INTERVAL)
    }
    private val retriggerPref: String get() {
        return getPrefKey(Constants.SHARED_PREF_ALARM_SUFFIX_RETRIGGER)
    }
    private val soundDelayPref: String get() {
        return getPrefKey(Constants.SHARED_PREF_ALARM_SUFFIX_SOUND_DELAY)
    }
    private val useCustomSoundPref: String get() {
        return getPrefKey(Constants.SHARED_PREF_ALARM_SUFFIX_USE_CUSTOM_SOUND)
    }
    private val customSoundPref: String get() {
        return getPrefKey(Constants.SHARED_PREF_ALARM_SUFFIX_CUSTOM_SOUND)
    }
    private val testAlarmPref: String get() {
        return getPrefKey(Constants.SHARED_PREF_ALARM_SUFFIX_TEST)
    }
    private val saveSoundPref: String get() {
        return getPrefKey(Constants.SHARED_PREF_ALARM_SUFFIX_SAVE_SOUND)
    }
    private val soundLevelPref: String get() {
        return getPrefKey(Constants.SHARED_PREF_ALARM_SUFFIX_SOUND_LEVEL)
    }
    private val inactiveEnabledPref: String get() {
        return getPrefKey(Constants.SHARED_PREF_ALARM_SUFFIX_INACTIVE_ENABLED)
    }
    private val inactiveStartPref: String get() {
        return getPrefKey(Constants.SHARED_PREF_ALARM_SUFFIX_INACTIVE_START_TIME)
    }
    private val inactiveEndPref: String get() {
        return getPrefKey(Constants.SHARED_PREF_ALARM_SUFFIX_INACTIVE_END_TIME)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        try {
            Log.v(LOG_ID, "onCreatePreferences called for key: ${Utils.dumpBundle(this.arguments)}" )
            preferenceManager.sharedPreferencesName = Constants.SHARED_PREF_TAG
            setPreferencesFromResource(R.xml.alarm_type, rootKey)
            if (requireArguments().containsKey("type")) {
                alarmType = AlarmType.fromIndex(requireArguments().getInt("type"))
                if (alarmType.setting == null) {
                    Log.e(LOG_ID, "Unsupported alarm type for creating fragment: $alarmType!")
                    return
                }
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
            InternalNotifier.addNotifier(requireContext(), this, mutableSetOf(NotifySource.NOTIFICATION_STOPPED))
            update()
            super.onResume()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onResume exception: " + exc.toString())
        }
    }

    override fun onPause() {
        Log.d(LOG_ID, "onPause called")
        try {
            stopTestSound()
            preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
            InternalNotifier.remNotifier(requireContext(), this)
            super.onPause()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onPause exception: " + exc.toString())
        }
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        Log.d(LOG_ID, "onDisplayPreferenceDialog called for " + preference.javaClass)
        try {
            var dialogFragment: DialogFragment? = null
            if (preference is TimePickerPreference) {
                dialogFragment = TimePickerPreferenceDialogFragmentCompat()
                val bundle = Bundle(1)
                bundle.putString("key", preference.key)
                dialogFragment.arguments = bundle
            }
            if (dialogFragment != null) {
                dialogFragment.setTargetFragment(this, 0)
                dialogFragment.show(parentFragmentManager, "androidx.preference.PreferenceFragment.DIALOG")
            } else {
                super.onDisplayPreferenceDialog(preference)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onDisplayPreferenceDialog exception: " + exc.toString())
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        Log.d(LOG_ID, "onSharedPreferenceChanged called for " + key)
        try {
            if(AlarmHandler.isAlarmSettingToShare(key))
                AlarmFragment.settingsChanged = true
            update()
            if(key == soundLevelPref) {
                startTestSound()
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString())
        }
    }

    private fun update() {
        Log.d(LOG_ID, "update called")
        try {
            val prefTest = findPreference<Preference>(testAlarmPref)
            if (prefTest != null) {
                prefTest.isEnabled = true
            }
            val prefSelectRingtone = findPreference<Preference>(customSoundPref)
            val prefUseCustomRingtone = findPreference<SwitchPreferenceCompat>(useCustomSoundPref)
            prefSelectRingtone!!.isEnabled = prefUseCustomRingtone!!.isChecked
            updateRingtoneSelectSummary()

            val inactivePref = findPreference<SwitchPreferenceCompat>(inactiveEnabledPref)
            if (inactivePref != null) {
                val prefStart = findPreference<TimePickerPreference>(inactiveStartPref)
                val prefEnd = findPreference<TimePickerPreference>(inactiveEndPref)
                prefStart!!.isEnabled = inactivePref.isChecked
                prefEnd!!.isEnabled = inactivePref.isChecked
            }

        } catch (exc: Exception) {
            Log.e(LOG_ID, "onPause exception: " + exc.toString())
        }
    }

    private fun createAlarmPrefSettings() {
        Log.v(LOG_ID, "createAlarmPrefSettings for alarm $alarmType")
        updatePreferenceKeys()
        updateData()

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
        setAlarmTest(testAlarmPref, alarmType)
        //setAlarmSettings(settingsPref, alarmType)
        setAlarmSave(saveSoundPref, alarmType, soundSaver)
        setRingtoneSelect(customSoundPref, Uri.parse(preferenceManager.sharedPreferences!!.getString(customSoundPref, "")))
    }

    private fun updatePreferenceKeys() {
        for (i in 0 until preferenceScreen.preferenceCount) {
            val pref: Preference = preferenceScreen.getPreference(i)
            if(!pref.key.isNullOrEmpty()) {
                val newKey = getPrefKey(pref.key)
                Log.v(LOG_ID, "Replace key ${pref.key} with $newKey")
                pref.key = newKey
            } else {
                val cat = pref as PreferenceCategory
                updatePreferenceKeys(cat)
            }
        }
    }


    private fun updatePreferenceKeys(preferenceCategory: PreferenceCategory) {
        for (i in 0 until preferenceCategory.preferenceCount) {
            val pref: Preference = preferenceCategory.getPreference(i)
            if(!pref.key.isNullOrEmpty()) {
                val newKey = getPrefKey(pref.key)
                Log.v(LOG_ID, "Replace key ${pref.key} with $newKey")
                pref.key = newKey
            } else {
                val cat = pref as PreferenceCategory
                updatePreferenceKeys(cat)
            }
        }
    }
    private fun updateData() {
        val enablePref = findPreference<SwitchPreferenceCompat>(enabledPref)
        enablePref!!.isChecked = preferenceManager.sharedPreferences!!.getBoolean(enablePref.key, true)

        val prefInterval = findPreference<SeekBarPreference>(intervalPref)
        prefInterval!!.value = preferenceManager.sharedPreferences!!.getInt(prefInterval.key, AlarmHandler.getDefaultIntervalMin(alarmType))
        prefInterval.summary = getIntervalSummary(alarmType)

        val prefRetrigger = findPreference<SeekBarPreference>(retriggerPref)
        prefRetrigger!!.value = preferenceManager.sharedPreferences!!.getInt(prefRetrigger.key, 0)

        val prefSoundDelay = findPreference<SeekBarPreference>(soundDelayPref)
        prefSoundDelay!!.value = preferenceManager.sharedPreferences!!.getInt(prefSoundDelay.key, 0)

        val prefUseCustomRingtone = findPreference<SwitchPreferenceCompat>(useCustomSoundPref)
        prefUseCustomRingtone!!.isChecked = preferenceManager.sharedPreferences!!.getBoolean(prefUseCustomRingtone.key, false)

        val levelPref = findPreference<SeekBarPreference>(soundLevelPref)
        if (levelPref != null) {
            levelPref.max = AlarmNotification.getMaxSoundLevel()
            levelPref.value = preferenceManager.sharedPreferences!!.getInt(levelPref.key, -1)
        }

        val inactivePref = findPreference<SwitchPreferenceCompat>(inactiveEnabledPref)
        inactivePref!!.isChecked = preferenceManager.sharedPreferences!!.getBoolean(inactivePref.key, false)

        val prefStart = findPreference<TimePickerPreference>(inactiveStartPref)
        prefStart!!.isEnabled = inactivePref.isChecked

        val prefEnd = findPreference<TimePickerPreference>(inactiveEndPref)
        prefEnd!!.isEnabled = inactivePref.isChecked
    }

    private fun getIntervalSummary(alarmType: AlarmType): String {
        return when(alarmType) {
            AlarmType.VERY_LOW,
            AlarmType.LOW -> resources.getString(de.michelinside.glucodatahandler.common.R.string.alarm_interval_summary_low)
            AlarmType.HIGH,
            AlarmType.VERY_HIGH -> resources.getString(de.michelinside.glucodatahandler.common.R.string.alarm_interval_summary_high)
            AlarmType.OBSOLETE -> resources.getString(de.michelinside.glucodatahandler.common.R.string.alarm_interval_summary_obsolete)
            else -> ""
        }
    }

    private fun setAlarmTest(preference: String, alarmType: AlarmType) {
        val pref = findPreference<Preference>(preference)
        pref?.setOnPreferenceClickListener {
            Log.d(LOG_ID, "Trigger test for $alarmType")
            stopTestSound()
            pref.isEnabled = false
            AlarmNotification.triggerTest(alarmType, requireContext())
            true
        }
    }

    /*
        private fun setAlarmSettings(preference: String, alarmType: AlarmType) {
            val pref = findPreference<Preference>(preference)
            if (pref != null) {
                pref.icon = ContextCompat.getDrawable(requireContext(), AlarmNotification.getSoundMode(alarmType, requireContext()).icon)
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

    */
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

    private fun updateRingtoneSelectSummary() {
        val pref = findPreference<Preference>(customSoundPref)
        val soundLevelPref = findPreference<SeekBarPreference>(soundLevelPref)
        if(pref != null) {
            if(pref.isEnabled) {
                val uri = Uri.parse(preferenceManager.sharedPreferences!!.getString(customSoundPref, ""))
                if (uri != null && uri.toString().isNotEmpty()) {
                    val ringtone = RingtoneManager.getRingtone(requireContext(), uri)
                    val title = ringtone.getTitle(requireContext())
                    if (title.isNullOrEmpty()) {
                        pref.summary = resources.getString(de.michelinside.glucodatahandler.common.R.string.alarm_sound_summary)
                        soundLevelPref!!.isEnabled = false
                    } else {
                        Log.d(LOG_ID, "Ringtone '$title' for uri $uri")
                        pref.summary = title
                        soundLevelPref!!.isEnabled = true
                    }
                } else {
                    pref.summary = resources.getString(de.michelinside.glucodatahandler.common.R.string.alarm_sound_summary)
                    soundLevelPref!!.isEnabled = false
                }
            } else {
                pref.summary = resources.getString(CR.string.alarm_app_sound)
                soundLevelPref!!.isEnabled = true
            }
        }
    }

    private fun setRingtoneSelect(preference: String, curUri: Uri?) {
        Log.v(LOG_ID, "setRingtoneSelect called for preference $preference with uri $curUri" )
        val pref = findPreference<Preference>(preference)
        if (pref != null) {
            if(ringtoneSelecter == null) {
                ringtoneSelecter = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                    Log.v(LOG_ID, "$alarmType result ${result.resultCode}: ${result.data}")
                    if (result.resultCode == Activity.RESULT_OK) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            setRingtoneResult(preference,  result.data!!.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java))
                        } else {
                            @Suppress("DEPRECATION")
                            setRingtoneResult(preference,  result.data!!.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI))
                        }
                    }
                }
            }
            pref.setOnPreferenceClickListener {
                val ringtoneIntent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                ringtoneIntent.putExtra(
                    RingtoneManager.EXTRA_RINGTONE_TYPE,
                    RingtoneManager.TYPE_ALL
                )
                ringtoneIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                ringtoneIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                ringtoneIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, curUri)
                ringtoneSelecter!!.launch(ringtoneIntent)
                true
            }
        }
    }

    private fun setRingtoneResult(preference: String, newUri: Uri?) {
        Log.i(LOG_ID, "Set custom ringtone for $preference: $newUri")
        with (preferenceManager.sharedPreferences!!.edit()) {
            putString(preference, newUri?.toString())
            apply()
        }
        setRingtoneSelect(preference, newUri)
        updateRingtoneSelectSummary()
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            Log.v(LOG_ID, "OnNotifyData called for $dataSource")
            update()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: " + exc.toString())
        }
    }

    private fun startTestSound() {
        val audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        Log.d(LOG_ID, "start test sound in mode ${audioManager.ringerMode}")
        if (audioManager.ringerMode >= AudioManager.RINGER_MODE_NORMAL) {
            if (curAlarmLevel < 0) {
                curAlarmLevel = AlarmNotification.getCurrentSoundLevel()
            }
            var soundLevel = preferenceManager!!.sharedPreferences!!.getInt(soundLevelPref, -1)
            if (soundLevel < 0)
                soundLevel = curAlarmLevel
            Log.d(LOG_ID, "Start test sound with level $soundLevel - current $curAlarmLevel")
            AlarmNotification.stopVibrationAndSound()
            AlarmNotification.setSoundLevel(soundLevel)
            if (!AlarmNotification.isRingtonePlaying()) {
                AlarmNotification.startSound(alarmType, requireContext(), false, forTest = true)
            }
        }
    }

    private fun stopTestSound() {
        Log.d(LOG_ID, "Stop test sound with current $curAlarmLevel")
        if(curAlarmLevel >= 0) {
            AlarmNotification.stopVibrationAndSound()
            AlarmNotification.setSoundLevel(curAlarmLevel)
            curAlarmLevel = -1
        }
    }


}
