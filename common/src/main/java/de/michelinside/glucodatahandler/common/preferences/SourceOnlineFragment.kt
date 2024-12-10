package de.michelinside.glucodatahandler.common.preferences

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.Log
import androidx.preference.*
import de.michelinside.glucodatahandler.common.R as CR
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.tasks.DataSourceTask
import de.michelinside.glucodatahandler.common.tasks.LibreLinkSourceTask


class SourceOnlineFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener, NotifierInterface {
    private val LOG_ID = "GDH.SourceFragment"
    private var settingsChanged = false

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Log.d(LOG_ID, "onCreatePreferences called")
        try {
            settingsChanged = false
            preferenceManager.sharedPreferencesName = Constants.SHARED_PREF_TAG
            setPreferencesFromResource(CR.xml.sources_online, rootKey)

            setPasswordPref(Constants.SHARED_PREF_LIBRE_PASSWORD)
            setPasswordPref(Constants.SHARED_PREF_DEXCOM_SHARE_PASSWORD)
            setPasswordPref(Constants.SHARED_PREF_NIGHTSCOUT_SECRET)

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
            super.onResume()
            preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
            updateEnableStates(preferenceManager.sharedPreferences!!)
            InternalNotifier.addNotifier(requireContext(), this, mutableSetOf(NotifySource.PATIENT_DATA_CHANGED))
            update()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onResume exception: " + exc.toString())
        }
    }

    override fun onPause() {
        Log.d(LOG_ID, "onPause called")
        try {
            super.onPause()
            preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
            InternalNotifier.remNotifier(requireContext(), this)
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
                Constants.SHARED_PREF_LIBRE_PATIENT_ID,
                Constants.SHARED_PREF_LIBRE_SERVER -> {
                    update()
                }
                Constants.SHARED_PREF_DEXCOM_SHARE_USE_US_URL -> {
                    updateDexcomAccountLink()
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

    private fun setListSummary(key: String, defaultResId: Int) {
        val listPref = findPreference<ListPreference>(key)
        if(listPref != null) {
            val value = listPref.entry
            listPref.summary = if(value.isNullOrEmpty())
                resources.getString(defaultResId)
            else
                value
        }
    }

    private fun update() {
        setSummary(Constants.SHARED_PREF_LIBRE_USER, CR.string.src_libre_user_summary)
        setListSummary(Constants.SHARED_PREF_LIBRE_SERVER, CR.string.source_libre_server_default)
        setSummary(Constants.SHARED_PREF_NIGHTSCOUT_URL, CR.string.src_ns_url_summary)
        setSummary(Constants.SHARED_PREF_DEXCOM_SHARE_USER, CR.string.src_dexcom_share_user_summary)
        updateDexcomAccountLink()
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

    private fun updateDexcomAccountLink() {
        try {
            val prefUseUsAccount = findPreference<SwitchPreferenceCompat>(Constants.SHARED_PREF_DEXCOM_SHARE_USE_US_URL)
            if(prefUseUsAccount != null) {
                val us_account = prefUseUsAccount.isChecked
                val prefLink = findPreference<Preference>(Constants.SHARED_PREF_DEXCOM_SHARE_ACCOUNT_LINK)
                if (prefLink != null) {
                    prefLink.summary = resources.getString(if(us_account) CR.string.dexcom_share_check_us_account else CR.string.dexcom_share_check_non_us_account)
                    prefLink.setOnPreferenceClickListener {
                        try {
                            val browserIntent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(resources.getString(if(us_account)CR.string.dexcom_account_us_url else CR.string.dexcom_account_non_us_url))
                            )
                            startActivity(browserIntent)
                        } catch (exc: Exception) {
                            Log.e(LOG_ID, "setLinkOnClick exception for key ${prefLink.key}" + exc.toString())
                        }
                        true
                    }
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "setupLibrePatientData exception: $exc")
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