package de.michelinside.glucodatahandler.common.preferences

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import androidx.core.content.ContextCompat
import de.michelinside.glucodatahandler.common.utils.Log
import androidx.preference.*
import de.michelinside.glucodatahandler.common.R
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.tasks.DataSourceTask
import de.michelinside.glucodatahandler.common.tasks.DexcomShareSourceTask
import de.michelinside.glucodatahandler.common.tasks.LibreLinkSourceTask
import de.michelinside.glucodatahandler.common.tasks.MedtrumSourceTask
import de.michelinside.glucodatahandler.common.ui.Dialogs

class SourceOnlineFragment : PreferenceFragmentCompatBase(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val LOG_ID = "GDH.SourceOnlineFragment"
    private var settingsChanged = false

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Log.d(LOG_ID, "onCreatePreferences called")
        try {
            preferenceManager.sharedPreferencesName = Constants.SHARED_PREF_TAG
            setPreferencesFromResource(R.xml.sources_online, rootKey)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreatePreferences exception: " + exc.toString())
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

    private fun updateIntervalSummary() {
        val listPref = findPreference<ListPreference>(Constants.SHARED_PREF_SOURCE_INTERVAL)
        if(listPref != null) {
            val curValue = preferenceManager.sharedPreferences?.getString(Constants.SHARED_PREF_SOURCE_INTERVAL, "")
            if(!curValue.isNullOrEmpty()) {
                Log.d(LOG_ID, "Update interval summary to $curValue")
                listPref.value = curValue
                setListSummary(Constants.SHARED_PREF_SOURCE_INTERVAL, R.string.source_interval_summary)
            }
        }
    }
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        Log.d(LOG_ID, "onSharedPreferenceChanged called for " + key)
        try {
            if(DataSourceTask.preferencesToSend.contains(key))
                settingsChanged = true

            when(key) {
                Constants.SHARED_PREF_SOURCE_INTERVAL -> {
                    updateIntervalSummary()
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString())
        }
    }
    override fun onResume() {
        Log.d(LOG_ID, "onResume called")
        try {
            super.onResume()
            preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
            updateIntervalSummary()
            updateEnableStates()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onResume exception: " + exc.toString())
        }
    }

    private fun updateEnableStates() {
        try {
            val prefSources = findPreference<PreferenceCategory>("pref_cat_sources")
            if(prefSources != null && preferenceManager.sharedPreferences != null) {
                Log.d(LOG_ID, "Update enable icons for ${prefSources.preferenceCount} sources")
                for (i in 0 until prefSources.preferenceCount) {
                    val pref: Preference = prefSources.getPreference(i)
                    if(!pref.key.isNullOrEmpty()) {
                        if(preferenceManager.sharedPreferences!!.getBoolean(pref.key, false)) {
                            pref.icon = ContextCompat.getDrawable(requireContext(), R.drawable.switch_on)
                        } else {
                            pref.icon = ContextCompat.getDrawable(requireContext(), R.drawable.switch_off)
                        }
                    }
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateEnableStates exception: " + exc.toString())
        }
    }

    override fun onPause() {
        Log.d(LOG_ID, "onPause called")
        try {
            super.onPause()
            preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onPause exception: " + exc.toString())
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
}

abstract class SourceOnlineFragmentBase(val preferenceResId: Int) : PreferenceFragmentCompatBase(), SharedPreferences.OnSharedPreferenceChangeListener, NotifierInterface {
    protected val LOG_ID = "GDH.SourceOnlineFragment"
    private var settingsChanged = false
    protected open val patientIdKey = ""
    private var patientId = ""


    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Log.d(LOG_ID, "onCreatePreferences called")
        try {
            settingsChanged = false
            preferenceManager.sharedPreferencesName = Constants.SHARED_PREF_TAG
            setPreferencesFromResource(preferenceResId, rootKey)
            setPasswordPref(getPasswordPref())

            PreferenceHelper.setLinkOnClick(findPreference("source_librelinkup_video"), R.string.video_tutorial_librelinkup, requireContext())

            setupPatientData()
            if(patientIdKey.isNotEmpty()) {
                patientId = preferenceManager.sharedPreferences!!.getString(patientIdKey, "")!!
                Log.d(LOG_ID, "Using patientId: $patientId")
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreatePreferences exception: " + exc.toString())
        }
    }

    abstract fun getPasswordPref(): String

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

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        Log.d(LOG_ID, "onSharedPreferenceChanged called for " + key)
        try {
            if(DataSourceTask.preferencesToSend.contains(key))
                settingsChanged = true

            when(key) {
                Constants.SHARED_PREF_LIBRE_PASSWORD,
                Constants.SHARED_PREF_LIBRE_USER,
                Constants.SHARED_PREF_MEDTRUM_PASSWORD,
                Constants.SHARED_PREF_MEDTRUM_USER,
                Constants.SHARED_PREF_DEXCOM_SHARE_USER,
                Constants.SHARED_PREF_DEXCOM_SHARE_PASSWORD,
                Constants.SHARED_PREF_NIGHTSCOUT_URL -> {
                    updateEnableStates(sharedPreferences)
                    update()
                }
                Constants.SHARED_PREF_LIBRE_PATIENT_ID,
                Constants.SHARED_PREF_LIBRE_SERVER,
                Constants.SHARED_PREF_DEXCOM_SHARE_SERVER,
                Constants.SHARED_PREF_MEDTRUM_PATIENT_ID,
                Constants.SHARED_PREF_MEDTRUM_SERVER -> {
                    update()
                }
                Constants.SHARED_PREF_DEXCOM_SHARE_ENABLED,
                Constants.SHARED_PREF_LIBRE_ENABLED,
                Constants.SHARED_PREF_MEDTRUM_ENABLED -> {
                    checkIntervalOnEnableChange(sharedPreferences, key)
                }
            }

            if(key == patientIdKey) {
                val listPreference = findPreference<ListPreference>(patientIdKey)
                if(listPreference != null) {
                    if(patientId != listPreference.value) {
                        val shouldReset = patientId.isNotEmpty() && listPreference.isVisible
                        patientId = listPreference.value
                        Log.i(LOG_ID, "PatientID changed to $patientId - shouldReset: $shouldReset")
                        if(shouldReset) {
                            Dialogs.showYesNoDialog(requireContext(), resources.getString(R.string.patient_changed), resources.getString(R.string.patient_changed_reset_db),
                                { _, _ ->
                                    GlucoDataService.resetDB()
                                }
                            )
                        }
                    }
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString())
        }
    }

    private fun checkIntervalOnEnableChange(sharedPreferences: SharedPreferences, key: String) {
        Log.v(LOG_ID, "checkIntervalOnEnableChange called for $key - contain key: ${sharedPreferences.contains(Constants.SHARED_PREF_SOURCE_INTERVAL)}")
        if(sharedPreferences.getBoolean(key, false)) {
            val interval = when(key) {
                Constants.SHARED_PREF_DEXCOM_SHARE_ENABLED -> 5
                Constants.SHARED_PREF_MEDTRUM_ENABLED -> 2
                else -> 1
            }
            val curInterval = sharedPreferences.getString(Constants.SHARED_PREF_SOURCE_INTERVAL, "1")?.toLong() ?: 1L
            if(interval > curInterval) {
                Log.i(LOG_ID, "Change interval from $curInterval to $interval for $key")
                with(sharedPreferences.edit()) {
                    putString(Constants.SHARED_PREF_SOURCE_INTERVAL, interval.toString())
                    apply()
                }
            }
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

            val switchMedtrumSource = findPreference<SwitchPreferenceCompat>(Constants.SHARED_PREF_MEDTRUM_ENABLED)
            if (switchMedtrumSource != null) {
                val user = sharedPreferences.getString(Constants.SHARED_PREF_MEDTRUM_USER, "")!!.trim()
                val password = sharedPreferences.getString(Constants.SHARED_PREF_MEDTRUM_PASSWORD, "")!!.trim()
                switchMedtrumSource.isEnabled = user.isNotEmpty() && password.isNotEmpty()
                if(!switchMedtrumSource.isEnabled)
                    switchMedtrumSource.isChecked = false
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateEnableStates exception: " + exc.toString())
        }
    }

    protected fun setSummary(key: String, defaultResId: Int) {
        val pref = findPreference<Preference>(key)
        if(pref != null) {
            val value = preferenceManager.sharedPreferences!!.getString(key, "")!!.trim()
            pref.summary = value.ifEmpty { resources.getString(defaultResId) }
        }
    }

    protected fun setListSummary(key: String, defaultResId: Int) {
        val listPref = findPreference<ListPreference>(key)
        if(listPref != null) {
            val value = listPref.entry
            listPref.summary = if(value.isNullOrEmpty())
                resources.getString(defaultResId)
            else
                value
        }
    }

    protected abstract fun update()

    open fun setupPatientData() {}

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            Log.v(LOG_ID, "OnNotifyData called for source $dataSource")
            if (dataSource == NotifySource.PATIENT_DATA_CHANGED) {
                setupPatientData()
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception for source $dataSource: $exc")
        }
    }
}

//**************************************************************************************************

class SourceLibreLinkUp: SourceOnlineFragmentBase(R.xml.source_librelinkup) {
    override val patientIdKey = Constants.SHARED_PREF_LIBRE_PATIENT_ID
    override fun getPasswordPref(): String = Constants.SHARED_PREF_LIBRE_PASSWORD

    override fun setupPatientData() {
        try {
            val listPreference = findPreference<ListPreference>(Constants.SHARED_PREF_LIBRE_PATIENT_ID)
            if(listPreference != null) {
                // force "global broadcast" to be the first entry
                listPreference.entries = LibreLinkSourceTask.patientData.values.toTypedArray()
                listPreference.entryValues = LibreLinkSourceTask.patientData.keys.toTypedArray()
                listPreference.isVisible = LibreLinkSourceTask.patientData.size > 1
                if(listPreference.isVisible)
                    setLibrePatientSummary()
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "setupLibrePatientData exception: $exc")
        }
    }

    override fun update() {
        setSummary(Constants.SHARED_PREF_LIBRE_USER, R.string.src_libre_user_summary)
        setListSummary(Constants.SHARED_PREF_LIBRE_SERVER, R.string.source_libre_server_default)
        setLibrePatientSummary()
    }


    private fun setLibrePatientSummary() {
        val listPreference = findPreference<ListPreference>(Constants.SHARED_PREF_LIBRE_PATIENT_ID)
        if(listPreference != null && listPreference.isVisible) {
            val pref = findPreference<Preference>(Constants.SHARED_PREF_LIBRE_PATIENT_ID)
            if (pref != null) {
                val value = preferenceManager.sharedPreferences!!.getString(
                    Constants.SHARED_PREF_LIBRE_PATIENT_ID,
                    ""
                )!!.trim()
                if (value.isEmpty() || !LibreLinkSourceTask.patientData.containsKey(value))
                    pref.summary = resources.getString(R.string.src_libre_patient_summary)
                else {
                    pref.summary = LibreLinkSourceTask.patientData[value]
                }
            }
        }
    }
}


//**************************************************************************************************

class SourceDexcomShare: SourceOnlineFragmentBase(R.xml.source_dexcom_share) {
    override fun getPasswordPref(): String = Constants.SHARED_PREF_DEXCOM_SHARE_PASSWORD
    override fun update() {
        setSummary(Constants.SHARED_PREF_DEXCOM_SHARE_USER, R.string.src_dexcom_share_user_summary)
        updateDexcomAccountLink()
    }

    private fun updateDexcomAccountLink() {
        try {
            val prefServer = findPreference<ListPreference>(Constants.SHARED_PREF_DEXCOM_SHARE_SERVER)
            val prefLink = findPreference<Preference>(Constants.SHARED_PREF_DEXCOM_SHARE_ACCOUNT_LINK)
            if(prefServer != null && prefLink != null) {
                val server = prefServer.value
                prefServer.summary = prefServer.entry
                prefLink.summary = resources.getString(DexcomShareSourceTask.getClarityUrlSummaryRes(server))
                PreferenceHelper.setLinkOnClick(prefLink, DexcomShareSourceTask.getClarityUrlRes(server), requireContext())
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateDexcomAccountLink exception: $exc")
        }
    }
}

//**************************************************************************************************

class SourceMedtrum: SourceOnlineFragmentBase(R.xml.source_medtrum) {
    override val patientIdKey = Constants.SHARED_PREF_MEDTRUM_PATIENT_ID
    override fun getPasswordPref(): String = Constants.SHARED_PREF_MEDTRUM_PASSWORD

    override fun update() {
        setSummary(Constants.SHARED_PREF_MEDTRUM_USER, R.string.src_medtrum_user_summary)
        setListSummary(Constants.SHARED_PREF_MEDTRUM_SERVER, R.string.source_medtrum_server_default)
        setMedtrumPatientSummary()
    }

    private fun setMedtrumPatientSummary() {
        Log.v(LOG_ID, "setMedtrumPatientSummary called")
        val listPreference = findPreference<ListPreference>(Constants.SHARED_PREF_MEDTRUM_PATIENT_ID)
        if(listPreference != null && listPreference.isVisible) {
            val pref = findPreference<Preference>(Constants.SHARED_PREF_MEDTRUM_PATIENT_ID)
            if (pref != null) {
                val value = preferenceManager.sharedPreferences!!.getString(
                    Constants.SHARED_PREF_MEDTRUM_PATIENT_ID,
                    ""
                )!!.trim()
                if (value.isEmpty() || !MedtrumSourceTask.patientData.containsKey(value))
                    pref.summary = resources.getString(R.string.src_medtrum_patient_summary)
                else {
                    pref.summary = MedtrumSourceTask.patientData[value]
                }
            }
        }
    }

    override fun setupPatientData() {
        try {
            val listPreference = findPreference<ListPreference>(Constants.SHARED_PREF_MEDTRUM_PATIENT_ID)
            if(listPreference != null) {
                Log.d(LOG_ID, "setupMedtrumPatientData called for ${MedtrumSourceTask.patientData.size} patients")
                // force "global broadcast" to be the first entry
                listPreference.entries = MedtrumSourceTask.patientData.values.toTypedArray()
                listPreference.entryValues = MedtrumSourceTask.patientData.keys.toTypedArray()
                listPreference.isVisible = MedtrumSourceTask.patientData.size > 1
                if(listPreference.isVisible)
                    setMedtrumPatientSummary()
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "setupMedtrumPatientData exception: $exc\n${exc.stackTrace}")
        }
    }
}

class SourceNightscout : SourceOnlineFragmentBase(R.xml.source_nightscout) {
    override fun getPasswordPref(): String = Constants.SHARED_PREF_NIGHTSCOUT_SECRET

    override fun update() {
        setSummary(Constants.SHARED_PREF_NIGHTSCOUT_URL, R.string.src_ns_url_summary)
    }

}
