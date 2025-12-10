package de.michelinside.glucodatahandler.common.preferences

import android.content.SharedPreferences
import android.os.Bundle
import androidx.core.content.ContextCompat
import de.michelinside.glucodatahandler.common.utils.Log
import androidx.fragment.app.DialogFragment
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.R
import de.michelinside.glucodatahandler.common.receiver.NotificationReceiver
import de.michelinside.glucodatahandler.common.ui.SelectReceiverPreference
import de.michelinside.glucodatahandler.common.ui.SelectReceiverPreferenceDialogFragmentCompat

class SourceNotificationFragment : PreferenceFragmentCompatBase(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val LOG_ID = "GDH.NotificationFragment"
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        try {
            Log.d(LOG_ID, "onCreatePreferences called")
            preferenceManager.sharedPreferencesName = Constants.SHARED_PREF_TAG
            setPreferencesFromResource(R.xml.sources_notification, rootKey)
            updateEnableStates()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreatePreferences exception: " + exc.toString())
        }
    }

    override fun onResume() {
        Log.d(LOG_ID, "onResume called")
        try {
            super.onResume()
            preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
            checkPermission()
            updateEnableStates()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onResume exception: " + exc.toString())
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

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        try {
            Log.d(LOG_ID, "onSharedPreferenceChanged called for key $key")
            if(key == Constants.SHARED_PREF_SOURCE_NOTIFICATION_ENABLED) {
                val enabled = sharedPreferences.getBoolean(key, false)
                val pref = findPreference<SwitchPreferenceCompat>(key)
                if(pref != null)
                    pref.isChecked = enabled
                if(enabled)
                    updateEnableStates()
            }
            if(key == Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_APP || key == Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_IOB_APP) {
                updateEnableStates()
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString())
        }
    }

    private fun updateEnableStates() {
        Log.d(LOG_ID, "updateEnableStates called")
        val enablePref = findPreference<SwitchPreferenceCompat>(Constants.SHARED_PREF_SOURCE_NOTIFICATION_ENABLED)
        if(enablePref!=null) {
            val glucoseApp = preferenceManager.sharedPreferences?.getString(Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_APP, "")
            val iobApp = preferenceManager.sharedPreferences?.getString(Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_IOB_APP, "")
            val iobEnabled = preferenceManager.sharedPreferences?.getBoolean(Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_IOB_ENABLED, true)
            val cobEnabeld = preferenceManager.sharedPreferences?.getBoolean(Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_COB_ENABLED, false)
            val supportsIobCob = !iobApp.isNullOrEmpty() && (iobEnabled == true || cobEnabeld == true)
            if(glucoseApp.isNullOrEmpty() && !supportsIobCob) {
                enablePref.isChecked = false
                enablePref.isEnabled = false
            } else {
                enablePref.isEnabled = true
            }

            val valuePref = findPreference<Preference>("source_notification_value_enabled")
            valuePref?.icon = ContextCompat.getDrawable(requireContext(), if(!glucoseApp.isNullOrEmpty()) R.drawable.switch_on else R.drawable.switch_off)
            val iobCobPref = findPreference<Preference>("source_notification_iob_cob_enabled")
            iobCobPref?.icon = ContextCompat.getDrawable(requireContext(), if(supportsIobCob) R.drawable.switch_on else R.drawable.switch_off)
        }
    }

    private fun checkPermission() {
        Log.d(LOG_ID, "checkPermission called")
        val enablePref = findPreference<SwitchPreferenceCompat>(Constants.SHARED_PREF_SOURCE_NOTIFICATION_ENABLED)
        if(enablePref!=null) {
            if(enablePref.isChecked && !GlucoDataService.checkNotificationReceiverPermission(requireContext(), false)) {
                Log.w(LOG_ID, "Disable notification receiver as permission not granted, yet!")
                enablePref.isChecked = false
            }
        }
    }
}

open class SourceNotificationBase(val resourceId: Int) : PreferenceFragmentCompatBase() {
    private val LOG_ID = "GDH.SourceNotificationBase"

    open fun initPreferences() {}

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Log.d(LOG_ID, "onCreatePreferences called")
        try {
            preferenceManager.sharedPreferencesName = Constants.SHARED_PREF_TAG
            setPreferencesFromResource(resourceId, rootKey)

            initPreferences()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreatePreferences exception: " + exc.toString())
        }
    }

    private fun getDialogFragment(preference: Preference): DialogFragment? {
        var dialogFragment: DialogFragment? = null
        if (preference is SelectReceiverPreference) {
            Log.d(LOG_ID, "Show SelectReceiver Dialog")
            dialogFragment = SelectReceiverPreferenceDialogFragmentCompat.initial(preference.key)
        }
        return dialogFragment
    }

    private fun showDialogFragment(preference: Preference): Boolean {
        val dialogFragment = getDialogFragment(preference)
        if (dialogFragment != null) {
            dialogFragment.setTargetFragment(this, 0)
            dialogFragment.show(parentFragmentManager, "androidx.preference.PreferenceFragment.DIALOG")
            return true
        }
        return false
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        Log.d(LOG_ID, "onDisplayPreferenceDialog called for " + preference.javaClass)
        try {
            if (!showDialogFragment(preference)) {
                super.onDisplayPreferenceDialog(preference)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onDisplayPreferenceDialog exception: " + exc.toString())
        }
    }
}


class SourceNotificationValue : SourceNotificationBase(R.xml.source_notification_value) {
    override fun initPreferences() {
        super.initPreferences()
        val regexPref = findPreference<EditTextPreference>(Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_APP_REGEX)
        regexPref?.setDefaultValue(NotificationReceiver.defaultGlucoseRegex)
    }
}

class SourceNotificationIobCob : SourceNotificationBase(R.xml.source_notification_iob_cob) {
    override fun initPreferences() {
        super.initPreferences()
        val iobRegexPref = findPreference<EditTextPreference>(Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_IOB_APP_REGEX)
        iobRegexPref?.setDefaultValue(NotificationReceiver.defaultIobRegex)
        val cobRegexPref = findPreference<EditTextPreference>(Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_COB_APP_REGEX)
        cobRegexPref?.setDefaultValue(NotificationReceiver.defaultCobRegex)
    }
}