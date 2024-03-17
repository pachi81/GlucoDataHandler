package de.michelinside.glucodatahandler.preferences

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.util.Log
import androidx.preference.*
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.tasks.DataSourceTask
import de.michelinside.glucodatahandler.common.tasks.LibreViewSourceTask


class SourceFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener, NotifierInterface {
    private val LOG_ID = "GDH.SourceFragment"
    private var settingsChanged = false

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Log.d(LOG_ID, "onCreatePreferences called")
        try {
            settingsChanged = false
            preferenceManager.sharedPreferencesName = Constants.SHARED_PREF_TAG
            setPreferencesFromResource(R.xml.sources, rootKey)

            val librePassword = findPreference<EditTextPreference>(Constants.SHARED_PREF_LIBRE_PASSWORD)
            librePassword?.setOnBindEditTextListener {editText ->
                editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }

            val nightscoutSecret = findPreference<EditTextPreference>(Constants.SHARED_PREF_NIGHTSCOUT_SECRET)
            nightscoutSecret?.setOnBindEditTextListener {editText ->
                editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }

            setupLibrePatientData()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreatePreferences exception: " + exc.toString())
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

    override fun onDisplayPreferenceDialog(preference: Preference) {
        Log.d(LOG_ID, "onDisplayPreferenceDialog called for " + preference.javaClass)
        try {
            if (preference is SelectReceiverPreference) {
                Log.d(LOG_ID, "Show SelectReceiver Dialog")
                val dialogFragment = SelectReceiverPreferenceDialogFragmentCompat.initial(preference.key)
                dialogFragment.setTargetFragment(this, 0)
                dialogFragment.show(parentFragmentManager, "androidx.preference.PreferenceFragment.DIALOG")
            } else {
                super.onDisplayPreferenceDialog(preference)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onDisplayPreferenceDialog exception: " + exc.toString())
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
                Constants.SHARED_PREF_NIGHTSCOUT_URL -> {
                    updateEnableStates(sharedPreferences!!)
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

    private fun setupLibrePatientData() {
        try {
            val listPreference = findPreference<ListPreference>(Constants.SHARED_PREF_LIBRE_PATIENT_ID)
            // force "global broadcast" to be the first entry
            listPreference!!.entries = LibreViewSourceTask.patientData.values.toTypedArray()
            listPreference.entryValues = LibreViewSourceTask.patientData.keys.toTypedArray()
            listPreference.isVisible = LibreViewSourceTask.patientData.size > 1
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