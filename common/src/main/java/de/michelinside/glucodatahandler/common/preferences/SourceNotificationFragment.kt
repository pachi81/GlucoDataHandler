package de.michelinside.glucodatahandler.common.preferences

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.DialogFragment
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.R
import de.michelinside.glucodatahandler.common.ui.SelectReceiverPreference
import de.michelinside.glucodatahandler.common.ui.SelectReceiverPreferenceDialogFragmentCompat
import de.michelinside.glucodatahandler.common.utils.PackageUtils

class SourceNotificationFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val LOG_ID = "GDH.NotificationFragment"
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Log.d(LOG_ID, "onCreatePreferences called")
        preferenceManager.sharedPreferencesName = Constants.SHARED_PREF_TAG
        setPreferencesFromResource(R.xml.sources_notification, rootKey)
        updateEnableStates()
    }

    override fun onResume() {
        Log.d(LOG_ID, "onResume called")
        try {
            super.onResume()
            preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
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

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
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
    }

    private fun updateEnableStates() {
        Log.d(LOG_ID, "updateEnableStates called")
        val enablePref = findPreference<SwitchPreferenceCompat>(Constants.SHARED_PREF_SOURCE_NOTIFICATION_ENABLED)
        if(enablePref!=null) {
            val glucoseApp = preferenceManager.sharedPreferences?.getString(Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_APP, "")
            val iobApp = preferenceManager.sharedPreferences?.getString(Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_IOB_APP, "")
            if(glucoseApp.isNullOrEmpty() && iobApp.isNullOrEmpty()) {
                enablePref.isChecked = false
                enablePref.isEnabled = false
            } else {
                enablePref.isEnabled = true
            }
            val glucoseCat = findPreference<PreferenceCategory>("notification_cat_glucose")
            val dexcomInfo = findPreference<Preference>("notification_dexcom_info")
            if(!glucoseApp.isNullOrEmpty() && PackageUtils.isDexcomG7App(glucoseApp)) {
                glucoseCat!!.initialExpandedChildrenCount = 3
                dexcomInfo!!.isVisible = true
            } else {
                glucoseCat!!.initialExpandedChildrenCount = 2
                dexcomInfo!!.isVisible = false
            }
        }
    }
}