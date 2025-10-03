package de.michelinside.glucodatahandler.preferences

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import de.michelinside.glucodatahandler.AODAccessibilityService
import de.michelinside.glucodatahandler.healthconnect.HealthConnectManager
import de.michelinside.glucodatahandler.common.ui.Dialogs
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.android_auto.CarModeReceiver
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.Intents
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.preferences.PreferenceHelper
import de.michelinside.glucodatahandler.common.receiver.GlucoseDataReceiver
import de.michelinside.glucodatahandler.common.ui.SelectReceiverPreference
import de.michelinside.glucodatahandler.common.utils.GlucoseStatistics
import de.michelinside.glucodatahandler.common.utils.PackageUtils
import kotlinx.coroutines.launch
import kotlin.collections.HashMap
import kotlin.collections.List
import kotlin.collections.mutableSetOf
import kotlin.collections.plus
import kotlin.collections.set
import kotlin.collections.toTypedArray
import de.michelinside.glucodatahandler.common.R as CR

abstract class SettingsFragmentBase(private val prefResId: Int) : SettingsFragmentCompatBase(), SharedPreferences.OnSharedPreferenceChangeListener {
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
            super.onResume()
            preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
            updateEnablePrefs.clear()
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
                Constants.SHARED_PREF_SOURCE_JUGGLUCO_WEBSERVER_ENABLED -> {
                    // update last 24 hours to fill data
                    GlucoseDataReceiver.resetLastServerTime()
                    GlucoseDataReceiver.checkHandleWebServerRequests(requireContext(), true)
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

    fun <T : Preference?> setEnableState(sharedPreferences: SharedPreferences, key: String, enableKey: String, secondEnableKey: String? = null, defValue: Boolean = false, invert: Boolean = false, secondDevValue: Boolean = false) {
        val pref = findPreference<T>(key)
        if (pref != null) {
            pref.isEnabled = invert != sharedPreferences.getBoolean(enableKey, defValue) && (if (secondEnableKey != null) sharedPreferences.getBoolean(secondEnableKey, secondDevValue) else true)

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
            setEnableState<SwitchPreferenceCompat>(sharedPreferences, Constants.SHARED_PREF_PERMANENT_NOTIFICATION_CUSTOM_LAYOUT, Constants.SHARED_PREF_PERMANENT_NOTIFICATION_EMPTY, defValue = false, invert = true)
            setEnableState<SwitchPreferenceCompat>(sharedPreferences, Constants.SHARED_PREF_PERMANENT_NOTIFICATION_SHOW_GRAPH, Constants.SHARED_PREF_PERMANENT_NOTIFICATION_EMPTY,  Constants.SHARED_PREF_PERMANENT_NOTIFICATION_CUSTOM_LAYOUT, defValue = false, invert = true, secondDevValue = true)
            setEnableState<SeekBarPreference>(sharedPreferences, Constants.SHARED_PREF_FLOATING_WIDGET_SIZE, Constants.SHARED_PREF_FLOATING_WIDGET)
            setEnableState<ListPreference>(sharedPreferences, Constants.SHARED_PREF_FLOATING_WIDGET_STYLE, Constants.SHARED_PREF_FLOATING_WIDGET)
            setEnableState<ListPreference>(sharedPreferences, Constants.SHARED_PREF_FLOATING_WIDGET_TRANSPARENCY, Constants.SHARED_PREF_FLOATING_WIDGET)
            setEnableState<ListPreference>(sharedPreferences, Constants.SHARED_PREF_FLOATING_WIDGET_TIME_TO_CLOSE, Constants.SHARED_PREF_FLOATING_WIDGET)
            setEnableState<ListPreference>(sharedPreferences, Constants.SHARED_PREF_FLOATING_WIDGET_LOCK_POSITION, Constants.SHARED_PREF_FLOATING_WIDGET)
            setEnableState<SelectReceiverPreference>(sharedPreferences, Constants.SHARED_PREF_FLOATING_WIDGET_TAP_ACTION, Constants.SHARED_PREF_FLOATING_WIDGET)
            setEnableState<SwitchPreferenceCompat>(sharedPreferences, Constants.SHARED_PREF_SEND_PREF_TO_GLUCODATAAUTO, Constants.SHARED_PREF_SEND_TO_GLUCODATAAUTO, defValue = true)
            setEnableState<SeekBarPreference>(sharedPreferences, Constants.SHARED_PREF_LOCKSCREEN_WP_Y_POS, Constants.SHARED_PREF_LOCKSCREEN_WP_ENABLED)
            setEnableState<SeekBarPreference>(sharedPreferences, Constants.SHARED_PREF_LOCKSCREEN_WP_STYLE, Constants.SHARED_PREF_LOCKSCREEN_WP_ENABLED)
            setEnableState<SeekBarPreference>(sharedPreferences, Constants.SHARED_PREF_LOCKSCREEN_WP_SIZE, Constants.SHARED_PREF_LOCKSCREEN_WP_ENABLED)

            setEnableState<SeekBarPreference>(sharedPreferences, Constants.SHARED_PREF_AOD_WP_Y_POS, Constants.SHARED_PREF_AOD_WP_ENABLED)
            setEnableState<SeekBarPreference>(sharedPreferences, Constants.SHARED_PREF_AOD_WP_STYLE, Constants.SHARED_PREF_AOD_WP_ENABLED)
            setEnableState<SeekBarPreference>(sharedPreferences, Constants.SHARED_PREF_AOD_WP_SIZE, Constants.SHARED_PREF_AOD_WP_ENABLED)
            setEnableState<SwitchPreferenceCompat>(sharedPreferences, Constants.SHARED_PREF_AOD_WP_COLOURED, Constants.SHARED_PREF_AOD_WP_ENABLED)

            setEnableState<SwitchPreferenceCompat>(sharedPreferences, Constants.SHARED_PREF_SOURCE_JUGGLUCO_WEBSERVER_ENABLED, Constants.SHARED_PREF_SOURCE_JUGGLUCO_ENABLED)
            setEnableState<SwitchPreferenceCompat>(sharedPreferences, Constants.SHARED_PREF_SOURCE_JUGGLUCO_WEBSERVER_IOB_SUPPORT, Constants.SHARED_PREF_SOURCE_JUGGLUCO_WEBSERVER_ENABLED, Constants.SHARED_PREF_SOURCE_JUGGLUCO_ENABLED)
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
        val prefSensorRuntime = findPreference<EditTextPreference>(Constants.SHARED_PREF_SENSOR_RUNTIME)
        if(prefSensorRuntime != null) {
            prefSensorRuntime.isVisible = ReceiveData.sensorStartTime > 0
            prefSensorRuntime.setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            }
        }
        updateSummary()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        try {
            super.onSharedPreferenceChanged(sharedPreferences, key)
            when (key) {
                Constants.SHARED_PREF_USE_MMOL -> updateSummary()
                Constants.SHARED_PREF_SENSOR_RUNTIME -> {
                    InternalNotifier.notify(GlucoDataService.context!!, NotifySource.SETTINGS, null)
                }
                Constants.SHARED_PREF_STANDARD_STATISTICS -> {
                    GlucoseStatistics.reset()
                }
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
            Constants.SHARED_PREF_FLOATING_WIDGET_STYLE,
            Constants.SHARED_PREF_FLOATING_WIDGET -> updateStyleSummary()
        }
    }

    private fun updateStyleSummary() {
        val widgetStylePref = findPreference<ListPreference>(Constants.SHARED_PREF_FLOATING_WIDGET_STYLE)
        if(widgetStylePref != null) {
            widgetStylePref.summary = widgetStylePref.entry
        }
    }
}

class LockscreenSettingsFragment: SettingsFragmentBase(R.xml.pref_lockscreen)  {
    companion object {
        fun requestAccessibilitySettings(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                intent.putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
    }
    override fun initPreferences() {
        Log.v(LOG_ID, "initPreferences called")
        super.initPreferences()
        updateStyleSummary()
        updateEnabledInitial()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        Log.v(LOG_ID, "onSharedPreferenceChanged called with key $key")
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            Constants.SHARED_PREF_LOCKSCREEN_WP_STYLE -> updateStyleSummary()
            Constants.SHARED_PREF_AOD_WP_STYLE -> updateStyleSummary()
            Constants.SHARED_PREF_AOD_WP_ENABLED -> {
                checkAccesibilityService()
            }
        }
    }

    private fun updateStyleSummary() {
        val lockscreenStylePref = findPreference<ListPreference>(Constants.SHARED_PREF_LOCKSCREEN_WP_STYLE)
        if(lockscreenStylePref != null) {
            lockscreenStylePref.summary = lockscreenStylePref.entry
        }
        val aodStylePref = findPreference<ListPreference>(Constants.SHARED_PREF_AOD_WP_STYLE)
        if(aodStylePref != null) {
            aodStylePref.summary = aodStylePref.entry
        }
    }
    private val accessibilitySettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val enabled = (AODAccessibilityService.isAccessibilitySettingsEnabled(requireContext()))
            Log.i(LOG_ID, "Accessibility permission: $enabled")
            preferenceManager.sharedPreferences?.edit()?.putBoolean(Constants.SHARED_PREF_AOD_WP_ENABLED, enabled)?.apply()
            val pref = findPreference<SwitchPreferenceCompat>(Constants.SHARED_PREF_AOD_WP_ENABLED)
            if (pref != null)
                pref.isChecked = enabled
        }

    private fun updateEnabledInitial() {
        val pref = findPreference<SwitchPreferenceCompat>(Constants.SHARED_PREF_AOD_WP_ENABLED)
        if (pref != null && pref.isChecked) {
            if (!AODAccessibilityService.isAccessibilitySettingsEnabled(requireContext())) {
                preferenceManager.sharedPreferences?.edit()
                    ?.putBoolean(Constants.SHARED_PREF_AOD_WP_ENABLED, false)?.apply()
                pref.isChecked = false
            }
        }
    }

    private fun checkAccesibilityService() {
        if(preferenceManager.sharedPreferences!!.getBoolean(Constants.SHARED_PREF_AOD_WP_ENABLED, false)) {
            val enabled = AODAccessibilityService.isAccessibilitySettingsEnabled(requireContext())
            if (!enabled) {
                Dialogs.showOkCancelDialog(requireContext(),
                    resources.getString(CR.string.permission_missing_title),
                    resources.getString(CR.string.aod_permission_info),
                    { _, _ ->
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        accessibilitySettingsLauncher.launch(intent)
                    },
                    { _, _ ->
                        Log.v(LOG_ID, "Accessibility permission canceled!")
                        updateEnabledInitial()
                    }
                )
            }
        }
    }
}

class NotificaitonSettingsFragment: SettingsFragmentBase(R.xml.pref_notification) {}

class WatchSettingsFragment: SettingsFragmentBase(R.xml.pref_watch) {
    override fun initPreferences() {
        Log.v(LOG_ID, "initPreferences called")
        val prefCheckWearOS = findPreference<Preference>(Constants.SHARED_PREF_CHECK_WEAR_OS_CONNECTION)
        prefCheckWearOS!!.setOnPreferenceClickListener {
            GlucoDataService.checkForConnectedNodes()
            true
        }

        val prefResetWearOS = findPreference<Preference>(Constants.SHARED_PREF_RESET_WEAR_OS_CONNECTION)
        prefResetWearOS!!.setOnPreferenceClickListener {
            GlucoDataService.resetWearPhoneConnection()
            true
        }

        PreferenceHelper.setLinkOnClick(findPreference(Constants.SHARED_PREF_OPEN_WATCH_DRIP_LINK), CR.string.watchdrip_link, requireContext())
    }
}

class WatchFaceFragment: SettingsFragmentBase(R.xml.pref_watchfaces) {
    override fun initPreferences() {
        Log.v(LOG_ID, "initPreferences called")

        PreferenceHelper.setLinkOnClick(findPreference(Constants.SHARED_PREF_WATCHFACES_PUJIE), CR.string.playstore_pujie_watchfaces, requireContext())
        PreferenceHelper.setLinkOnClick(findPreference(Constants.SHARED_PREF_WATCHFACES_DMM), CR.string.playstore_dmm_watchfaces, requireContext())
        PreferenceHelper.setLinkOnClick(findPreference(Constants.SHARED_PREF_WATCHFACES_GDC), CR.string.playstore_gdc_watchfaces, requireContext())

    }
}

class TransferSettingsFragment: SettingsFragmentBase(R.xml.pref_transfer) {
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<Set<String>>
    override fun initPreferences() {
        Log.v(LOG_ID, "initPreferences called")
        super.initPreferences()
        setupReceivers(Constants.GLUCODATA_BROADCAST_ACTION, Constants.SHARED_PREF_GLUCODATA_RECEIVERS)
        setupReceivers(Constants.XDRIP_ACTION_GLUCOSE_READING, Constants.SHARED_PREF_XDRIP_RECEIVERS)
        setupReceivers(Intents.XDRIP_BROADCAST_ACTION, Constants.SHARED_PREF_XDRIP_BROADCAST_RECEIVERS)
        setupHealthConnect()
    }

    private fun setupHealthConnect() {
        val pref = findPreference<SwitchPreferenceCompat>(Constants.SHARED_PREF_SEND_TO_HEALTH_CONNECT)
        if (Build.VERSION.SDK_INT < 28 && !HealthConnectManager.isHealthConnectAvailable(requireContext().applicationContext)) {
            pref?.isVisible = false
        } else {
            requestPermissionLauncher = registerForActivityResult(HealthConnectManager.getPermissionRequestContract()) { grantedPermissions ->
                if (grantedPermissions.containsAll(HealthConnectManager.WRITE_GLUCOSE_PERMISSIONS)) {
                    Log.i(LOG_ID, "Health Connect permissions granted by user.")
                    // Berechtigungen erteilt, UI aktualisieren oder weitere Aktionen ausführen
                } else {
                    Log.w(LOG_ID, "Health Connect permissions were not fully granted.")
                    // Berechtigungen nicht (vollständig) erteilt
                    pref?.isChecked = false
                }
            }
        }
    }
/*
    override fun onResume() {
        super.onResume()
        val pref = findPreference<SwitchPreferenceCompat>(Constants.SHARED_PREF_SEND_TO_HEALTH_CONNECT)
        if(pref?.isChecked == true) {
            lifecycleScope.launch { // Changed from GlobalScope
                if(!HealthConnectManager.isHealthConnectAvailable(requireContext().applicationContext) || !HealthConnectManager.hasAllPermissions(requireContext().applicationContext)) {
                    withContext(Dispatchers.Main) {
                        pref.isChecked = false
                    }
                }
            }
        }
    }
*/
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if(key == Constants.SHARED_PREF_SEND_TO_HEALTH_CONNECT) {
            if(sharedPreferences!!.getBoolean(Constants.SHARED_PREF_SEND_TO_HEALTH_CONNECT, false)) {
                lifecycleScope.launch { // Changed from GlobalScope
                    val isReady = HealthConnectManager.checkAndEnsureRequirements(requireContext().applicationContext, requestPermissionLauncher)
                    if (isReady) {
                        // Health Connect ist sofort bereit
                        Log.d(LOG_ID, "Health Connect ready to use.")
                    } else {
                        // Maßnahmen wurden eingeleitet (Play Store / Berechtigungsdialog)
                        // Die Activity wartet auf das Ergebnis des Launchers oder auf Nutzerinteraktion
                        Log.d(LOG_ID, "Health Connect requirements not met, actions initiated.")

                    }
                }
            }
        } else
            super.onSharedPreferenceChanged(sharedPreferences, key)
    }

}
class GDASettingsFragment: SettingsFragmentBase(R.xml.pref_gda) {
    override fun initPreferences() {
        Log.v(LOG_ID, "initPreferences called")
        super.initPreferences()
        if (PackageUtils.isGlucoDataAutoAvailable(requireContext())) {
            val sendToGDA = findPreference<SwitchPreferenceCompat>(Constants.SHARED_PREF_SEND_TO_GLUCODATAAUTO)
            PreferenceHelper.replaceSecondTitle(sendToGDA)
            PreferenceHelper.replaceSecondSummary(sendToGDA)
            sendToGDA!!.isVisible = true
            val sendPrefToGDA = findPreference<SwitchPreferenceCompat>(Constants.SHARED_PREF_SEND_PREF_TO_GLUCODATAAUTO)
            PreferenceHelper.replaceSecondSummary(sendPrefToGDA)
            sendPrefToGDA!!.isVisible = true
        } else {
            val no_gda_info = findPreference<Preference>(Constants.SHARED_PREF_NO_GLUCODATAAUTO)
            if (no_gda_info != null) {
                PreferenceHelper.replaceSecondTitle(no_gda_info)
                PreferenceHelper.replaceSecondSummary(no_gda_info)
                no_gda_info.isVisible = true
                PreferenceHelper.setLinkOnClick(no_gda_info, CR.string.glucodataauto_link, requireContext())
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when(key) {
            Constants.SHARED_PREF_SEND_TO_GLUCODATAAUTO,
            Constants.SHARED_PREF_SEND_PREF_TO_GLUCODATAAUTO -> {
                if(CarModeReceiver.AA_connected) {
                    CarModeReceiver.sendToGlucoDataAuto(requireContext(), true)
                }
            }
        }
    }

}
