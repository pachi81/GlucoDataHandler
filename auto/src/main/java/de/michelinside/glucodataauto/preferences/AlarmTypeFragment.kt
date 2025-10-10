package de.michelinside.glucodataauto.preferences

import android.os.Bundle
import de.michelinside.glucodatahandler.common.utils.Log
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import de.michelinside.glucodataauto.R
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.notification.AlarmHandler
import de.michelinside.glucodatahandler.common.notification.AlarmSetting
import de.michelinside.glucodatahandler.common.notification.AlarmType
import de.michelinside.glucodatahandler.common.preferences.PreferenceFragmentCompatBase
import de.michelinside.glucodatahandler.common.ui.GlucoseEditPreference
import de.michelinside.glucodatahandler.common.utils.Utils

class AlarmTypeFragment : PreferenceFragmentCompatBase() {
    private val LOG_ID = "GDH.AA.AlarmTypeFragment"
    private var alarmType = AlarmType.NONE

    private fun getPrefKey(suffix: String): String {
        return alarmType.setting!!.getSettingName(suffix)
    }

    private val enabledPref: String get() {
        return getPrefKey(Constants.SHARED_PREF_ALARM_SUFFIX_ENABLED)
    }
    private val intervalPref: String get() {
        return getPrefKey(Constants.SHARED_PREF_ALARM_SUFFIX_INTERVAL)
    }
    private val deltaPref: String get() {
        return getPrefKey(Constants.SHARED_PREF_ALARM_SUFFIX_DELTA)
    }
    private val occurrenceCountPref: String get() {
        return getPrefKey(Constants.SHARED_PREF_ALARM_SUFFIX_OCCURRENCE_COUNT)
    }
    private val borderPref: String get() {
        return getPrefKey(Constants.SHARED_PREF_ALARM_SUFFIX_BORDER)
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
            super.onResume()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onResume exception: " + exc.toString())
        }
    }

    override fun onPause() {
        Log.d(LOG_ID, "onPause called")
        try {
            super.onPause()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onPause exception: " + exc.toString())
        }
    }

    private fun createAlarmPrefSettings() {
        Log.v(LOG_ID, "createAlarmPrefSettings for alarm $alarmType")
        updatePreferenceKeys()
        updateData()
    }

    private fun updatePreferenceKeys() {
        for (i in 0 until preferenceScreen.preferenceCount) {
            val pref: Preference = preferenceScreen.getPreference(i)
            if(pref is PreferenceCategory) {
                updatePreferenceKeys(pref)
            } else if(pref.key.startsWith("_")) {
                val newKey = getPrefKey(pref.key)
                Log.v(LOG_ID, "Replace key ${pref.key} with $newKey")
                pref.key = newKey
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
        val prefEnabled = findPreference<SwitchPreferenceCompat>(enabledPref)
        prefEnabled!!.isChecked = preferenceManager.sharedPreferences!!.getBoolean(prefEnabled.key, alarmType.setting!!.enabled)

        val prefInterval = findPreference<SeekBarPreference>(intervalPref)
        prefInterval!!.value = preferenceManager.sharedPreferences!!.getInt(prefInterval.key, AlarmHandler.getDefaultIntervalMin(alarmType))
        prefInterval.summary = getIntervalSummary(alarmType)

        if (alarmType.setting!!.hasDelta()) {
            val alarmSettingsCat = findPreference<PreferenceCategory>(Constants.SHARED_PREF_ALARM_TYPE_SETTINGS_CAT)
            alarmSettingsCat!!.isVisible = true

            val prefDelta = findPreference<GlucoseEditPreference>(deltaPref)
            prefDelta!!.text = preferenceManager.sharedPreferences!!.getFloat(prefDelta.key, AlarmSetting.defaultDelta).toString()
            if(alarmType == AlarmType.FALLING_FAST)
                prefDelta.isNegative = true

            val prefOccurrenceCount = findPreference<SeekBarPreference>(occurrenceCountPref)
            prefOccurrenceCount!!.value = preferenceManager.sharedPreferences!!.getInt(prefOccurrenceCount.key, AlarmSetting.defaultDeltaCount)

            val prefBorder = findPreference<GlucoseEditPreference>(borderPref)
            prefBorder!!.text = preferenceManager.sharedPreferences!!.getFloat(prefBorder.key, AlarmSetting.defaultDeltaBorder).toString()
        }
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
}
