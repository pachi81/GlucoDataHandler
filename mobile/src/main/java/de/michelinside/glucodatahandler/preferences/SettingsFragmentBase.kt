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
import de.michelinside.glucodatahandler.Dialogs
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.android_auto.CarModeReceiver
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.PackageUtils
import de.michelinside.glucodatahandler.common.R as CR


abstract class SettingsFragmentBase(private val prefResId: Int) : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {
    protected val LOG_ID = "GDH.SettingsFragmentBase"
    private val updateEnablePrefs = mutableSetOf<String>()
    private lateinit var activityResultOverlayLauncher: ActivityResultLauncher<Intent>

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Log.d(LOG_ID, "onCreatePreferences called")
        try {
            preferenceManager.sharedPreferencesName = Constants.SHARED_PREF_TAG
            setPreferencesFromResource(prefResId, rootKey)

            initPreferences()

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

    open fun initPreferences() {
    }

    override fun onResume() {
        Log.d(LOG_ID, "onResume called")
        try {
            preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
            updateEnablePrefs.clear()
            update()
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
            if(updateEnablePrefs.isEmpty() || updateEnablePrefs.contains(key!!)) {
                updateEnableStates(sharedPreferences!!)
            }
            when(key) {
                Constants.SHARED_PREF_FLOATING_WIDGET -> {
                    if (sharedPreferences!!.getBoolean(Constants.SHARED_PREF_FLOATING_WIDGET, false) && !Settings.canDrawOverlays(requireContext())) {
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
                Constants.SHARED_PREF_APP_COLOR_SCHEME -> {
                    update()
                    Dialogs.updateColorScheme(requireContext())
                }
                Constants.SHARED_PREF_LARGE_ARROW_ICON -> {
                    InternalNotifier.notify(GlucoDataService.context!!, NotifySource.SETTINGS, null)
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
            intent.putExtra(Settings.EXTRA_APP_PACKAGE,requireContext().packageName)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activityResultOverlayLauncher.launch(intent)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "requestOverlayPermission exception: " + exc.toString())
        }
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        Log.d(LOG_ID, "onDisplayPreferenceDialog called for " + preference.javaClass)
        try {
            if (preference is TapActionPreference) {
                Log.d(LOG_ID, "Show SelectReceiver Dialog")
                val dialogFragment = TapActionPreferenceDialogFragmentCompat.initial(preference.key)
                dialogFragment.setTargetFragment(this, 0)
                dialogFragment.show(parentFragmentManager, "androidx.preference.PreferenceFragment.DIALOG")
            } else {
                super.onDisplayPreferenceDialog(preference)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onDisplayPreferenceDialog exception: " + exc.toString())
        }
    }

    fun <T : Preference?> setEnableState(sharedPreferences: SharedPreferences, key: String, enableKey: String, secondEnableKey: String? = null, defValue: Boolean = false, invert: Boolean = false) {
        val pref = findPreference<T>(key)
        if (pref != null) {
            pref.isEnabled = invert != (sharedPreferences.getBoolean(enableKey, defValue) && (if (secondEnableKey != null) sharedPreferences.getBoolean(secondEnableKey, defValue) else true))

            if(!updateEnablePrefs.contains(enableKey)) {
                Log.v(LOG_ID, "Add update enable pref $enableKey")
                updateEnablePrefs.add(enableKey)
            }
            if (secondEnableKey != null && !updateEnablePrefs.contains(secondEnableKey)) {
                Log.v(LOG_ID, "Add update second enable pref $secondEnableKey")
                updateEnablePrefs.add(secondEnableKey)
            }
        }
    }

    private fun update() {
        val sharedPref = preferenceManager.sharedPreferences!!
        updateEnableStates(sharedPref)
        val colorSchemePref = findPreference<ListPreference>(Constants.SHARED_PREF_APP_COLOR_SCHEME)
        if(colorSchemePref != null) {
            colorSchemePref.summary = resources.getString(when (sharedPref.getString(Constants.SHARED_PREF_APP_COLOR_SCHEME, "")) {
                "dark" -> CR.string.application_color_scheme_dark
                "light" -> CR.string.application_color_scheme_light
                else -> CR.string.application_color_scheme_system
            })
        }
    }

    fun updateEnableStates(sharedPreferences: SharedPreferences) {
        try {
            Log.d(LOG_ID, "updateEnableStates called")
            setEnableState<MultiSelectListPreference>(sharedPreferences, Constants.SHARED_PREF_GLUCODATA_RECEIVERS, Constants.SHARED_PREF_SEND_TO_GLUCODATA_AOD)
            setEnableState<MultiSelectListPreference>(sharedPreferences, Constants.SHARED_PREF_XDRIP_RECEIVERS, Constants.SHARED_PREF_SEND_TO_XDRIP)
            setEnableState<MultiSelectListPreference>(sharedPreferences, Constants.SHARED_PREF_XDRIP_BROADCAST_RECEIVERS, Constants.SHARED_PREF_SEND_XDRIP_BROADCAST)
            /*setEnableState<ListPreference>(sharedPreferences, Constants.SHARED_PREF_PERMANENT_NOTIFICATION_ICON, Constants.SHARED_PREF_PERMANENT_NOTIFICATION, defValue = true)
            setEnableState<SwitchPreferenceCompat>(sharedPreferences, Constants.SHARED_PREF_PERMANENT_NOTIFICATION_EMPTY, Constants.SHARED_PREF_PERMANENT_NOTIFICATION, defValue = true)
            setEnableState<SwitchPreferenceCompat>(sharedPreferences, Constants.SHARED_PREF_PERMANENT_NOTIFICATION_USE_BIG_ICON, Constants.SHARED_PREF_PERMANENT_NOTIFICATION, defValue = true)
            setEnableState<SwitchPreferenceCompat>(sharedPreferences, Constants.SHARED_PREF_SECOND_PERMANENT_NOTIFICATION, Constants.SHARED_PREF_PERMANENT_NOTIFICATION, defValue = true)*/
            setEnableState<SwitchPreferenceCompat>(sharedPreferences, Constants.SHARED_PREF_PERMANENT_NOTIFICATION_CUSTOM_LAYOUT, Constants.SHARED_PREF_PERMANENT_NOTIFICATION_EMPTY, defValue = true, invert = true)
            setEnableState<ListPreference>(sharedPreferences, Constants.SHARED_PREF_SECOND_PERMANENT_NOTIFICATION_ICON, /*Constants.SHARED_PREF_PERMANENT_NOTIFICATION,*/Constants.SHARED_PREF_SECOND_PERMANENT_NOTIFICATION, defValue = false)
            setEnableState<SeekBarPreference>(sharedPreferences, Constants.SHARED_PREF_FLOATING_WIDGET_SIZE, Constants.SHARED_PREF_FLOATING_WIDGET)
            setEnableState<ListPreference>(sharedPreferences, Constants.SHARED_PREF_FLOATING_WIDGET_STYLE, Constants.SHARED_PREF_FLOATING_WIDGET)
            setEnableState<ListPreference>(sharedPreferences, Constants.SHARED_PREF_FLOATING_WIDGET_TRANSPARENCY, Constants.SHARED_PREF_FLOATING_WIDGET)
            setEnableState<SwitchPreferenceCompat>(sharedPreferences, Constants.SHARED_PREF_SEND_PREF_TO_GLUCODATAAUTO, Constants.SHARED_PREF_SEND_TO_GLUCODATAAUTO, defValue = true)
            setEnableState<SeekBarPreference>(sharedPreferences, Constants.SHARED_PREF_LOCKSCREEN_WP_Y_POS, Constants.SHARED_PREF_LOCKSCREEN_WP_ENABLED)
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


    protected fun setupReceivers(broadcastAction: String, multiSelectPrefKey: String) {
        val selectTargets = findPreference<MultiSelectListPreference>(multiSelectPrefKey)
        if(selectTargets != null) {
            val receivers = getReceivers(broadcastAction)
            // force "global broadcast" to be the first entry
            selectTargets.entries =
                arrayOf<CharSequence>(resources.getString(CR.string.pref_global_broadcast)) + receivers.keys.toTypedArray()
            selectTargets.entryValues = arrayOf<CharSequence>("") + receivers.values.toTypedArray()
        }
    }
}

class GeneralSettingsFragment: SettingsFragmentBase(R.xml.pref_general) {

    override fun initPreferences() {
        Log.v(LOG_ID, "initPreferences called")
        super.initPreferences()
        updateSummary()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        try {
            super.onSharedPreferenceChanged(sharedPreferences, key)
            when (key) {
                Constants.SHARED_PREF_USE_MMOL -> updateSummary()
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString())
        }
    }

    private fun updateSummary() {
        val useMmol = findPreference<SwitchPreferenceCompat>(Constants.SHARED_PREF_USE_MMOL)
        val otherUnitPref = findPreference<SwitchPreferenceCompat>(Constants.SHARED_PREF_SHOW_OTHER_UNIT)
        if(otherUnitPref != null && useMmol != null) {
            val otherUnit = if(useMmol.isChecked) "mg/dl" else "mmol/l"
            otherUnitPref.summary = requireContext().resources.getString(CR.string.pref_show_other_unit_summary).format(otherUnit)
        }
    }
}

class RangeSettingsFragment: SettingsFragmentBase(R.xml.pref_target_range) {}
class UiSettingsFragment: SettingsFragmentBase(R.xml.pref_ui) {}
class WidgetSettingsFragment: SettingsFragmentBase(R.xml.pref_widgets) {

    override fun initPreferences() {
        Log.v(LOG_ID, "initPreferences called")
        super.initPreferences()
        updateStyleSummary()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            Constants.SHARED_PREF_FLOATING_WIDGET_STYLE -> updateStyleSummary()
        }
    }

    private fun updateStyleSummary() {
        val stylePref = findPreference<ListPreference>(Constants.SHARED_PREF_FLOATING_WIDGET_STYLE)
        if(stylePref != null) {
            stylePref.summary = stylePref.entry
        }
    }
}

class NotificaitonSettingsFragment: SettingsFragmentBase(R.xml.pref_notification) {}
class LockscreenSettingsFragment: SettingsFragmentBase(R.xml.pref_lockscreen) {}
class WatchSettingsFragment: SettingsFragmentBase(R.xml.pref_watch) {
    override fun initPreferences() {
        Log.v(LOG_ID, "initPreferences called")
        val prefCheckWearOS = findPreference<Preference>(Constants.SHARED_PREF_CHECK_WEAR_OS_CONNECTION)
        prefCheckWearOS!!.setOnPreferenceClickListener {
            GlucoDataService.checkForConnectedNodes()
            true
        }

        val prefWatchDripLink = findPreference<Preference>(Constants.SHARED_PREF_OPEN_WATCH_DRIP_LINK)
        prefWatchDripLink!!.setOnPreferenceClickListener {
            val browserIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(resources.getText(CR.string.watchdrip_link).toString())
            )
            startActivity(browserIntent)
            true
        }
    }
}
class TransferSettingsFragment: SettingsFragmentBase(R.xml.pref_transfer) {
    override fun initPreferences() {
        Log.v(LOG_ID, "initPreferences called")
        super.initPreferences()
        setupReceivers(Constants.GLUCODATA_BROADCAST_ACTION, Constants.SHARED_PREF_GLUCODATA_RECEIVERS)
        setupReceivers(Constants.XDRIP_ACTION_GLUCOSE_READING, Constants.SHARED_PREF_XDRIP_RECEIVERS)
        setupReceivers(Constants.XDRIP_BROADCAST_ACTION, Constants.SHARED_PREF_XDRIP_BROADCAST_RECEIVERS)
    }

}
class GDASettingsFragment: SettingsFragmentBase(R.xml.pref_gda) {
    override fun initPreferences() {
        Log.v(LOG_ID, "initPreferences called")
        super.initPreferences()
        if (PackageUtils.isGlucoDataAutoAvailable(requireContext())) {
            val sendToGDA = findPreference<SwitchPreferenceCompat>(Constants.SHARED_PREF_SEND_TO_GLUCODATAAUTO)
            sendToGDA!!.isVisible = true
            val sendPrefToGDA = findPreference<SwitchPreferenceCompat>(Constants.SHARED_PREF_SEND_PREF_TO_GLUCODATAAUTO)
            sendPrefToGDA!!.isVisible = true
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
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when(key) {
            Constants.SHARED_PREF_SEND_TO_GLUCODATAAUTO,
            Constants.SHARED_PREF_SEND_PREF_TO_GLUCODATAAUTO -> {
                if(CarModeReceiver.AA_connected) {
                    val extras = ReceiveData.createExtras()
                    if (extras != null)
                        CarModeReceiver.sendToGlucoDataAuto(requireContext(), extras, true)
                }
            }
        }
    }

}