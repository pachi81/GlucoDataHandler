package de.michelinside.glucodatahandler.preferences

import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.util.Log
import androidx.preference.*
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.tasks.DataSourceTask


class SourceFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val LOG_ID = "GlucoDataHandler.SourceFragment"

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Log.d(LOG_ID, "onCreatePreferences called")
        try {
            DataSourceTask.editSettings = true
            preferenceManager.sharedPreferencesName = Constants.SHARED_PREF_TAG
            setPreferencesFromResource(R.xml.sources, rootKey)

            val librePassword = findPreference<EditTextPreference>(Constants.SHARED_PREF_LIBRE_PASSWORD)
            librePassword?.setOnBindEditTextListener {editText ->
                editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }

        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreatePreferences exception: " + exc.toString())
        }
    }

    override fun onDestroyView() {
        Log.d(LOG_ID, "onDestroyView called")
        super.onDestroyView()
        DataSourceTask.editSettings = false
    }

    override fun onResume() {
        Log.d(LOG_ID, "onResume called")
        try {
            preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
            updateEnableStates(preferenceManager.sharedPreferences!!)
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

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        Log.d(LOG_ID, "onSharedPreferenceChanged called for " + key)
        try {
            when(key) {
                Constants.SHARED_PREF_LIBRE_ENABLED -> {
                    updateEnableStates(sharedPreferences!!)
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString())
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

    fun <T : Preference?> setEnableState(sharedPreferences: SharedPreferences, key: String, enableKey: String, secondEnableKey: String? = null, defValue: Boolean = false) {
        val pref = findPreference<T>(key)
        if (pref != null)
            pref.isEnabled = sharedPreferences.getBoolean(enableKey, defValue) && (if (secondEnableKey != null) sharedPreferences.getBoolean(secondEnableKey, defValue) else true)
    }

    fun updateEnableStates(sharedPreferences: SharedPreferences) {
        try {
            setEnableState<EditTextPreference>(sharedPreferences, Constants.SHARED_PREF_LIBRE_USER, Constants.SHARED_PREF_LIBRE_ENABLED)
            setEnableState<EditTextPreference>(sharedPreferences, Constants.SHARED_PREF_LIBRE_PASSWORD, Constants.SHARED_PREF_LIBRE_ENABLED)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateEnableStates exception: " + exc.toString())
        }
    }
}