package de.michelinside.glucodatahandler.preferences

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.DialogFragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.takisoft.preferencex.TimePickerPreference
import com.takisoft.preferencex.TimePickerPreferenceDialogFragmentCompat
import de.michelinside.glucodatahandler.common.ui.SelectReceiverPreference
import de.michelinside.glucodatahandler.common.ui.SelectReceiverPreferenceDialogFragmentCompat

abstract class SettingsFragmentCompatBase: PreferenceFragmentCompat() {
    private val LOG_ID = "GDH.SettingsFragmentCompatBase"

    open fun getDialogFragment(preference: Preference): DialogFragment? {
        var dialogFragment: DialogFragment? = null
        if (preference is TimePickerPreference) {
            dialogFragment = TimePickerPreferenceDialogFragmentCompat()
            val bundle = Bundle(1)
            bundle.putString("key", preference.key)
            dialogFragment.arguments = bundle
        } else if (preference is SelectReceiverPreference) {
            Log.d(LOG_ID, "Show SelectReceiver Dialog")
            dialogFragment = SelectReceiverPreferenceDialogFragmentCompat.initial(preference.key)
        }
        return dialogFragment
    }

    open fun showDialogFragment(preference: Preference): Boolean {
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