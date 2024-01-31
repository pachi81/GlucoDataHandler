package de.michelinside.glucodatahandler.preferences

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.*
import de.michelinside.glucodatahandler.BuildConfig
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.utils.Utils
import de.michelinside.glucodatahandler.common.R as CR


class SettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val LOG_ID = "GDH.SettingsFragment"
    private lateinit var activityResultOverlayLauncher: ActivityResultLauncher<Intent>

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Log.d(LOG_ID, "onCreatePreferences called")
        try {
            preferenceManager.sharedPreferencesName = Constants.SHARED_PREF_TAG
            setPreferencesFromResource(R.xml.preferences, rootKey)

            if (BuildConfig.DEBUG) {
                val notifySwitch =
                    findPreference<SwitchPreferenceCompat>(Constants.SHARED_PREF_DUMMY_VALUES)
                notifySwitch!!.isVisible = true
            }

            setupReceivers(Constants.GLUCODATA_BROADCAST_ACTION, Constants.SHARED_PREF_GLUCODATA_RECEIVERS)
            setupReceivers(Constants.XDRIP_ACTION_GLUCOSE_READING, Constants.SHARED_PREF_XDRIP_RECEIVERS)
            setupReceivers(Constants.XDRIP_BROADCAST_ACTION, Constants.SHARED_PREF_XDRIP_BROADCAST_RECEIVERS)

            if (Utils.isPackageAvailable(requireContext(), Constants.PACKAGE_GLUCODATAAUTO)) {
                val sendToGDA = findPreference<SwitchPreferenceCompat>(Constants.SHARED_PREF_SEND_TO_GLUCODATAAUTO)
                sendToGDA!!.isVisible = true
            } else {
                val no_gda_info = findPreference<Preference>(Constants.SHARED_PREF_NO_GLUCODATAAUTO)
                if (no_gda_info != null) {
                    no_gda_info.isVisible = true
                    no_gda_info.setOnPreferenceClickListener {
                        val browserIntent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(resources.getText(CR.string.glucodataauto_link).toString())
                        )
                        startActivity(browserIntent)
                        true
                    }
                }
            }

            activityResultOverlayLauncher = registerForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) {
                if (!Settings.canDrawOverlays(requireContext())) {
                    Log.w(LOG_ID, "Overlay permission denied!")
                } else {
                    // setting to true
                    Log.i(LOG_ID, "Overlay permission granted!")
                    val pref = findPreference<SwitchPreferenceCompat>(Constants.SHARED_PREF_FLOATING_WIDGET)
                    if (pref != null) {
                        pref.isChecked = true
                    }
                    with(preferenceManager.sharedPreferences!!.edit()) {
                        putBoolean(Constants.SHARED_PREF_FLOATING_WIDGET, true)
                        apply()
                    }
                }
            }
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
                //Constants.SHARED_PREF_PERMANENT_NOTIFICATION,
                Constants.SHARED_PREF_SECOND_PERMANENT_NOTIFICATION,
                Constants.SHARED_PREF_SEND_TO_GLUCODATA_AOD,
                Constants.SHARED_PREF_SEND_TO_XDRIP,
                Constants.SHARED_PREF_SEND_XDRIP_BROADCAST -> {
                    updateEnableStates(sharedPreferences!!)
                }
                Constants.SHARED_PREF_FLOATING_WIDGET -> {
                    updateEnableStates(sharedPreferences!!)
                    if (sharedPreferences.getBoolean(Constants.SHARED_PREF_FLOATING_WIDGET, false) && !Settings.canDrawOverlays(requireContext())) {
                        // as long as permission is not granted, disable immediately
                        val pref = findPreference<SwitchPreferenceCompat>(Constants.SHARED_PREF_FLOATING_WIDGET)
                        if (pref != null) {
                            pref.isChecked = false
                        }
                        with(preferenceManager.sharedPreferences!!.edit()) {
                            putBoolean(Constants.SHARED_PREF_FLOATING_WIDGET, false)
                            apply()
                        }
                        requestOverlayPermission()
                    } else if (!sharedPreferences.getBoolean(Constants.SHARED_PREF_FLOATING_WIDGET, false)) {
                        val floatingWidget = findPreference<SwitchPreferenceCompat>(Constants.SHARED_PREF_FLOATING_WIDGET)
                        if (floatingWidget != null)
                            floatingWidget.isChecked = false
                    }
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString())
        }
    }

    private fun requestOverlayPermission() {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + requireContext().packageName)
            )
            activityResultOverlayLauncher.launch(intent)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "requestOverlayPermission exception: " + exc.toString())
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
            setEnableState<MultiSelectListPreference>(sharedPreferences, Constants.SHARED_PREF_GLUCODATA_RECEIVERS, Constants.SHARED_PREF_SEND_TO_GLUCODATA_AOD)
            setEnableState<MultiSelectListPreference>(sharedPreferences, Constants.SHARED_PREF_XDRIP_RECEIVERS, Constants.SHARED_PREF_SEND_TO_XDRIP)
            setEnableState<MultiSelectListPreference>(sharedPreferences, Constants.SHARED_PREF_XDRIP_BROADCAST_RECEIVERS, Constants.SHARED_PREF_SEND_XDRIP_BROADCAST)
            /*setEnableState<ListPreference>(sharedPreferences, Constants.SHARED_PREF_PERMANENT_NOTIFICATION_ICON, Constants.SHARED_PREF_PERMANENT_NOTIFICATION, defValue = true)
            setEnableState<SwitchPreferenceCompat>(sharedPreferences, Constants.SHARED_PREF_PERMANENT_NOTIFICATION_EMPTY, Constants.SHARED_PREF_PERMANENT_NOTIFICATION, defValue = true)
            setEnableState<SwitchPreferenceCompat>(sharedPreferences, Constants.SHARED_PREF_PERMANENT_NOTIFICATION_USE_BIG_ICON, Constants.SHARED_PREF_PERMANENT_NOTIFICATION, defValue = true)
            setEnableState<SwitchPreferenceCompat>(sharedPreferences, Constants.SHARED_PREF_SECOND_PERMANENT_NOTIFICATION, Constants.SHARED_PREF_PERMANENT_NOTIFICATION, defValue = true)*/
            setEnableState<ListPreference>(sharedPreferences, Constants.SHARED_PREF_SECOND_PERMANENT_NOTIFICATION_ICON, /*Constants.SHARED_PREF_PERMANENT_NOTIFICATION,*/Constants.SHARED_PREF_SECOND_PERMANENT_NOTIFICATION, defValue = false)
            setEnableState<SeekBarPreference>(sharedPreferences, Constants.SHARED_PREF_FLOATING_WIDGET_SIZE, Constants.SHARED_PREF_FLOATING_WIDGET)
            setEnableState<ListPreference>(sharedPreferences, Constants.SHARED_PREF_FLOATING_WIDGET_STYLE, Constants.SHARED_PREF_FLOATING_WIDGET)
            setEnableState<ListPreference>(sharedPreferences, Constants.SHARED_PREF_FLOATING_WIDGET_TRANSPARENCY, Constants.SHARED_PREF_FLOATING_WIDGET)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateEnableStates exception: " + exc.toString())
        }
    }

    private fun getReceivers(broadcastAction: String): HashMap<String, String> {
        val names = HashMap<String, String>()
        try {
            val receivers: List<ResolveInfo>
            val intent = Intent(broadcastAction)
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


    private fun setupReceivers(broadcastAction: String, multiSelectPrefKey: String) {
        val selectTargets = findPreference<MultiSelectListPreference>(multiSelectPrefKey)
        val receivers = getReceivers(broadcastAction)
        // force "global broadcast" to be the first entry
        selectTargets!!.entries = arrayOf<CharSequence>(resources.getString(CR.string.pref_global_broadcast)) + receivers.keys.toTypedArray()
        selectTargets.entryValues = arrayOf<CharSequence>("") + receivers.values.toTypedArray()
    }
}
