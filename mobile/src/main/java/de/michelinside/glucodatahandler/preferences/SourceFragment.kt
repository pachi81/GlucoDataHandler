package de.michelinside.glucodatahandler.preferences

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.util.Log
import androidx.preference.*
import de.michelinside.glucodatahandler.Dialogs
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.R as CR
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.tasks.DataSourceTask
import de.michelinside.glucodatahandler.common.tasks.LibreLinkSourceTask


class SourceFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener, NotifierInterface {
    private val LOG_ID = "GDH.SourceFragment"
    private var settingsChanged = false

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Log.d(LOG_ID, "onCreatePreferences called")
        try {
            settingsChanged = false
            preferenceManager.sharedPreferencesName = Constants.SHARED_PREF_TAG
            setPreferencesFromResource(R.xml.sources, rootKey)

            setPasswordPref(Constants.SHARED_PREF_LIBRE_PASSWORD)
            setPasswordPref(Constants.SHARED_PREF_DEXCOM_SHARE_PASSWORD)
            setPasswordPref(Constants.SHARED_PREF_NIGHTSCOUT_SECRET)

            setupLocalIobAction(findPreference(Constants.SHARED_PREF_SOURCE_JUGGLUCO_SET_NS_IOB_ACTION))
            setupLocalIobAction(findPreference(Constants.SHARED_PREF_SOURCE_XDRIP_SET_NS_IOB_ACTION))

            setupLibrePatientData()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreatePreferences exception: " + exc.toString())
        }
    }

    private fun setPasswordPref(prefName: String) {
        val pwdPref = findPreference<EditTextPreference>(prefName)
        pwdPref?.setOnBindEditTextListener {editText ->
            editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
    }

    override fun onDestroyView() {
        Log.d(LOG_ID, "onDestroyView called")
        try {
            if (settingsChanged) {
                InternalNotifier.notify(requireContext(), NotifySource.SOURCE_SETTINGS, DataSourceTask.getSettingsBundle(preferenceManager.sharedPreferences!!))
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
            updateEnableStates(preferenceManager.sharedPreferences!!)
            InternalNotifier.addNotifier(requireContext(), this, mutableSetOf(NotifySource.PATIENT_DATA_CHANGED))
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
            InternalNotifier.remNotifier(requireContext(), this)
            super.onPause()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onPause exception: " + exc.toString())
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        Log.d(LOG_ID, "onSharedPreferenceChanged called for " + key)
        try {
            if(DataSourceTask.preferencesToSend.contains(key))
                settingsChanged = true

            when(key) {
                Constants.SHARED_PREF_LIBRE_PASSWORD,
                Constants.SHARED_PREF_LIBRE_USER,
                Constants.SHARED_PREF_DEXCOM_SHARE_USER,
                Constants.SHARED_PREF_DEXCOM_SHARE_PASSWORD,
                Constants.SHARED_PREF_NIGHTSCOUT_URL -> {
                    updateEnableStates(sharedPreferences!!)
                    update()
                }
                Constants.SHARED_PREF_LIBRE_PATIENT_ID -> {
                    update()
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString())
        }
    }

    fun updateEnableStates(sharedPreferences: SharedPreferences) {
        try {
            val switchLibreSource = findPreference<SwitchPreferenceCompat>(Constants.SHARED_PREF_LIBRE_ENABLED)
            if (switchLibreSource != null) {
                val libreUser = sharedPreferences.getString(Constants.SHARED_PREF_LIBRE_USER, "")!!.trim()
                val librePassword = sharedPreferences.getString(Constants.SHARED_PREF_LIBRE_PASSWORD, "")!!.trim()
                switchLibreSource.isEnabled = libreUser.isNotEmpty() && librePassword.isNotEmpty()
                if(!switchLibreSource.isEnabled)
                    switchLibreSource.isChecked = false
            }

            val switchDexcomSource = findPreference<SwitchPreferenceCompat>(Constants.SHARED_PREF_DEXCOM_SHARE_ENABLED)
            if (switchDexcomSource != null) {
                val dexcomUser = sharedPreferences.getString(Constants.SHARED_PREF_DEXCOM_SHARE_USER, "")!!.trim()
                val dexcomPassword = sharedPreferences.getString(Constants.SHARED_PREF_DEXCOM_SHARE_PASSWORD, "")!!.trim()
                switchDexcomSource.isEnabled = dexcomUser.isNotEmpty() && dexcomPassword.isNotEmpty()
                if(!switchDexcomSource.isEnabled)
                    switchDexcomSource.isChecked = false
            }

            val switchNightscoutSource = findPreference<SwitchPreferenceCompat>(Constants.SHARED_PREF_NIGHTSCOUT_ENABLED)
            if (switchNightscoutSource != null) {
                val url = sharedPreferences.getString(Constants.SHARED_PREF_NIGHTSCOUT_URL, "")!!.trim()
                switchNightscoutSource.isEnabled = url.isNotEmpty() && url.isNotEmpty()
                if(!switchNightscoutSource.isEnabled)
                    switchNightscoutSource.isChecked = false
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateEnableStates exception: " + exc.toString())
        }
    }

    private fun setSummary(key: String, defaultResId: Int) {
        val pref = findPreference<Preference>(key)
        if(pref != null) {
            val value = preferenceManager.sharedPreferences!!.getString(key, "")!!.trim()
            pref.summary = if(value.isEmpty())
                 resources.getString(defaultResId)
            else
                value
        }
    }
    private fun update() {
        setSummary(Constants.SHARED_PREF_LIBRE_USER, CR.string.src_libre_user_summary)
        setSummary(Constants.SHARED_PREF_NIGHTSCOUT_URL, CR.string.src_ns_url_summary)
        setPatientSummary()
    }


    private fun setPatientSummary() {
        val listPreference = findPreference<ListPreference>(Constants.SHARED_PREF_LIBRE_PATIENT_ID)
        if(listPreference != null && listPreference.isVisible) {
            val pref = findPreference<Preference>(Constants.SHARED_PREF_LIBRE_PATIENT_ID)
            if (pref != null) {
                val value = preferenceManager.sharedPreferences!!.getString(
                    Constants.SHARED_PREF_LIBRE_PATIENT_ID,
                    ""
                )!!.trim()
                if (value.isEmpty() || !LibreLinkSourceTask.patientData.containsKey(value))
                    pref.summary = resources.getString(CR.string.src_libre_patient_summary)
                else {
                    pref.summary = LibreLinkSourceTask.patientData[value]
                }
            }
        }
    }
    private fun setupLibrePatientData() {
        try {
            val listPreference = findPreference<ListPreference>(Constants.SHARED_PREF_LIBRE_PATIENT_ID)
            // force "global broadcast" to be the first entry
            listPreference!!.entries = LibreLinkSourceTask.patientData.values.toTypedArray()
            listPreference.entryValues = LibreLinkSourceTask.patientData.keys.toTypedArray()
            listPreference.isVisible = LibreLinkSourceTask.patientData.size > 1
            if(listPreference.isVisible)
                setPatientSummary()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "setupLibrePatientData exception: $exc")
        }
    }

    private fun setupLocalIobAction(preference: Preference?) {
        if(preference != null) {
            preference.setOnPreferenceClickListener {
                Dialogs.showOkCancelDialog(requireContext(), CR.string.activate_local_nightscout_iob_title,
                    CR.string.activate_local_nightscout_iob_message) { _, _ ->
                    with(preferenceManager!!.sharedPreferences!!.edit()) {
                        putBoolean(Constants.SHARED_PREF_NIGHTSCOUT_IOB_COB, true)
                        putString(Constants.SHARED_PREF_NIGHTSCOUT_URL, "http://127.0.0.1:17580")
                        putString(Constants.SHARED_PREF_NIGHTSCOUT_SECRET, "")
                        putString(Constants.SHARED_PREF_NIGHTSCOUT_TOKEN, "")
                        putBoolean(Constants.SHARED_PREF_NIGHTSCOUT_ENABLED, true)
                        apply()
                    }
                    findPreference<SwitchPreferenceCompat>(Constants.SHARED_PREF_NIGHTSCOUT_IOB_COB)!!.isChecked = true
                    findPreference<EditTextPreference>(Constants.SHARED_PREF_NIGHTSCOUT_URL)!!.text = "http://127.0.0.1:17580"
                    findPreference<EditTextPreference>(Constants.SHARED_PREF_NIGHTSCOUT_SECRET)!!.text = ""
                    findPreference<EditTextPreference>(Constants.SHARED_PREF_NIGHTSCOUT_TOKEN)!!.text = ""
                    findPreference<SwitchPreferenceCompat>(Constants.SHARED_PREF_NIGHTSCOUT_ENABLED)!!.isChecked = true

                }
                true
            }
        }
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            Log.v(LOG_ID, "OnNotifyData called for source $dataSource")
            if (dataSource == NotifySource.PATIENT_DATA_CHANGED)
                setupLibrePatientData()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception for source $dataSource: $exc")
        }
    }

}