package de.michelinside.glucodatahandler.preferences

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.util.Log
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import de.michelinside.glucodatahandler.BuildConfig
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.android_auto.CarModeReceiver
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifyDataSource

class SettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val LOG_ID = "GlucoDataHandler.SettingsFragment"

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Log.d(LOG_ID, "onCreatePreferences called")
        try {
            preferenceManager.sharedPreferencesName = Constants.SHARED_PREF_TAG
            setPreferencesFromResource(R.xml.preferences, rootKey)

            if (BuildConfig.DEBUG) {
                val notifySwitch =
                    findPreference<SwitchPreferenceCompat>(Constants.SHARED_PREF_NOTIFICATION)
                notifySwitch!!.isVisible = true
            }

            val selectTargets = findPreference<MultiSelectListPreference>(Constants.SHARED_PREF_GLUCODATA_RECEIVERS)
            val receivers = getReceivers()
            // force "global broadcast" to be the first entry
            selectTargets!!.entries = arrayOf<CharSequence>(resources.getString(R.string.pref_global_broadcast)) + receivers.keys.toTypedArray()
            selectTargets.entryValues = arrayOf<CharSequence>("") + receivers.values.toTypedArray()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreatePreferences exception: " + exc.toString())
        }
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
                Constants.SHARED_PREF_USE_MMOL,
                Constants.SHARED_PREF_TARGET_MIN,
                Constants.SHARED_PREF_TARGET_MAX,
                Constants.SHARED_PREF_LOW_GLUCOSE,
                Constants.SHARED_PREF_HIGH_GLUCOSE,
                Constants.SHARED_PREF_FIVE_MINUTE_DELTA,
                Constants.SHARED_PREF_COLOR_ALARM,
                Constants.SHARED_PREF_COLOR_OUT_OF_RANGE,
                Constants.SHARED_PREF_COLOR_OK -> {
                    ReceiveData.updateSettings(sharedPreferences!!)
                    InternalNotifier.notify(requireContext(), NotifyDataSource.SETTINGS, ReceiveData.getSettingsBundle())
                }
                Constants.SHARED_PREF_CAR_NOTIFICATION -> {
                    CarModeReceiver.updateSettings(sharedPreferences!!)
                }
                Constants.SHARED_PREF_SEND_TO_GLUCODATA_AOD -> {
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

    fun updateEnableStates(sharedPreferences: SharedPreferences) {
        val pref = findPreference<MultiSelectListPreference>(Constants.SHARED_PREF_GLUCODATA_RECEIVERS)
        if (pref != null)
            pref.isEnabled = sharedPreferences.getBoolean(Constants.SHARED_PREF_SEND_TO_GLUCODATA_AOD, false)
    }

    private fun getReceivers(): HashMap<String, String> {
        val names = HashMap<String, String>()
        try {
            val receivers: List<ResolveInfo>
            val intent = Intent(Constants.GLUCODATA_BROADCAST_ACTION)
            receivers = requireContext().packageManager.queryBroadcastReceivers(
                intent,
                PackageManager.GET_META_DATA
            )
            for (resolveInfo in receivers) {
                val pkgName = resolveInfo.activityInfo.packageName
                val name =
                    resolveInfo.activityInfo.loadLabel(requireContext().packageManager).toString()
                if (pkgName != null && pkgName != requireContext().packageName) {
                    names[name] = pkgName
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "getReceivers exception: " + exc.toString())
        }
        return names
    }
}