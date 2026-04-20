package de.michelinside.glucodatahandler.preferences

import android.os.Bundle
import de.michelinside.glucodatahandler.common.utils.Log
import androidx.fragment.app.DialogFragment
import androidx.preference.Preference
import com.takisoft.preferencex.TimePickerPreference
import com.takisoft.preferencex.TimePickerPreferenceDialogFragmentCompat
import de.michelinside.glucodatahandler.common.preferences.PreferenceFragmentCompatBase
import de.michelinside.glucodatahandler.common.ui.SelectReceiverPreference
import de.michelinside.glucodatahandler.common.ui.SelectReceiverPreferenceDialogFragmentCompat

abstract class SettingsFragmentCompatBase: PreferenceFragmentCompatBase() {
    private val LOG_ID = "GDH.SettingsFragmentCompatBase"

    override fun getDialogFragment(preference: Preference): DialogFragment? {
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
}