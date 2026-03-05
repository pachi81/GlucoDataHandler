package de.michelinside.glucodatahandler.preferences

import android.content.SharedPreferences
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.R as CR
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.Intents
import de.michelinside.glucodatahandler.common.utils.Log
import de.michelinside.glucodatahandler.healthconnect.HealthConnectManager
import kotlinx.coroutines.launch

class TransferSettingsFragment: SettingsFragmentBase(R.xml.pref_transfer) {
    override fun onResume() {
        Log.d(LOG_ID, "onResume called")
        try {
            super.onResume()
            updateEnableStates()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onResume exception: " + exc.toString())
        }
    }

    private fun updateEnableStates() {
        try {
            if(preferenceManager.sharedPreferences == null)
                return
            val prefHealthConnect = findPreference<Preference>("transfer_healthconnect")
            if(prefHealthConnect != null ) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P && !HealthConnectManager.isHealthConnectAvailable(requireContext())) {
                    prefHealthConnect.isEnabled = false
                    prefHealthConnect.summary = resources.getString(CR.string.health_connect_not_supported_version)
                    setEnableState(prefHealthConnect, false)
                } else {
                    val enabled = preferenceManager.sharedPreferences!!.getBoolean(Constants.SHARED_PREF_SEND_TO_HEALTH_CONNECT, false)
                    setEnableState(prefHealthConnect, enabled)
                }
            }
            val prefLocalApps = findPreference<Preference>("transfer_localapps")
            if(prefLocalApps != null ) {
                val xdripEnabled = preferenceManager.sharedPreferences!!.getBoolean(Constants.SHARED_PREF_SEND_XDRIP_BROADCAST, false)
                val toXdripEnabled = preferenceManager.sharedPreferences!!.getBoolean(Constants.SHARED_PREF_SEND_TO_XDRIP, false)
                val toGlucodataEnabled = preferenceManager.sharedPreferences!!.getBoolean(Constants.SHARED_PREF_SEND_TO_GLUCODATA_AOD, false)
                setEnableState(prefLocalApps, xdripEnabled || toXdripEnabled || toGlucodataEnabled)
            }
            val prefNsUpload = findPreference<Preference>("transfer_nightscout")
            if(prefNsUpload != null ) {
                val enabled = preferenceManager.sharedPreferences!!.getBoolean(Constants.SHARED_PREF_NIGHTSCOUT_UPLOAD_ENABLED, false)
                setEnableState(prefNsUpload, enabled)
            }
            val prefLocalWebsrv = findPreference<Preference>("local_webservice")
            if(prefLocalWebsrv != null ) {
                val enabled = preferenceManager.sharedPreferences!!.getBoolean(Constants.SHARED_PREF_XDRIP_SERVER, false)
                setEnableState(prefLocalWebsrv, enabled)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateEnableStates exception: " + exc.toString())
        }
    }

    private fun setEnableState(pref: Preference, enable: Boolean) {
        Log.d(LOG_ID, "setEnableState called for ${pref.key}: $enable")
        if(enable) {
            pref.icon = ContextCompat.getDrawable(requireContext(), CR.drawable.switch_on)
        } else {
            pref.icon = ContextCompat.getDrawable(requireContext(), CR.drawable.switch_off)
        }
    }

}

class TransferHealthConnectFragment: SettingsFragmentBase(R.xml.pref_transfer_healthconnect) {
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<Set<String>>
    private var requestStartTime: Long = 0 // Track when the request started
    private var mustCheckPermission = false
    private var openHealthSettings = false

    override fun initPreferences() {
        Log.v(LOG_ID, "initPreferences called")
        super.initPreferences()
        setupHealthConnect()
    }

    private fun setupHealthConnect() {
        mustCheckPermission = false
        openHealthSettings = false
        val pref = findPreference<SwitchPreferenceCompat>(Constants.SHARED_PREF_SEND_TO_HEALTH_CONNECT)
        requestPermissionLauncher = registerForActivityResult(HealthConnectManager.getPermissionRequestContract()) { grantedPermissions ->
            if (grantedPermissions.containsAll(HealthConnectManager.WRITE_GLUCOSE_PERMISSIONS)) {
                Log.i(LOG_ID, "Health Connect permissions granted by user.")
                // Berechtigungen erteilt, UI aktualisieren oder weitere Aktionen ausführen
                HealthConnectManager.writeLastValues(requireContext())
            } else {
                val duration = System.currentTimeMillis() - requestStartTime
                Log.w(LOG_ID, "Health Connect permissions were not fully granted. Duration: $duration ms")
                // If duration is very short, the system likely suppressed the dialog
                if (duration < 1000) {
                    Log.i(LOG_ID, "Permission dialog suppressed, opening settings.")
                    HealthConnectManager.openHealthConnectSettings(requireContext())
                    openHealthSettings = true
                } else {
                    pref?.isChecked = false
                }
            }
        }
        val prefOpenHealthConnect = findPreference<Preference>("health_connect_settings")
        if(prefOpenHealthConnect != null) {
            prefOpenHealthConnect.setOnPreferenceClickListener {
                HealthConnectManager.openHealthConnectSettings(requireContext())
                true
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if(openHealthSettings) {
            mustCheckPermission = true
            openHealthSettings = false
        }
    }

    override fun onResume() {
        super.onResume()
        if(mustCheckPermission)
            checkPermissions()
    }

    private fun checkPermissions() {
        val pref = findPreference<SwitchPreferenceCompat>(Constants.SHARED_PREF_SEND_TO_HEALTH_CONNECT)
        // Check permissions only if the user actually wants to use Health Connect
        if (pref?.isChecked == true) {
            lifecycleScope.launch {
                val granted = HealthConnectManager.checkRequirements(requireContext())
                if (!granted) {
                    Log.w(LOG_ID, "Permissions lost while in background, disabling switch.")
                    pref.isChecked = false
                } else {
                    HealthConnectManager.writeLastValues(requireContext())
                }
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        try {
            if(key == Constants.SHARED_PREF_SEND_TO_HEALTH_CONNECT) {
                mustCheckPermission = false
                openHealthSettings = false
                if(sharedPreferences!!.getBoolean(Constants.SHARED_PREF_SEND_TO_HEALTH_CONNECT, false)) {
                    lifecycleScope.launch {
                        requestStartTime = System.currentTimeMillis()  // set start time to check, if the dialog was not opened...
                        val isReady = HealthConnectManager.checkAndEnsureRequirements(requireContext(), requestPermissionLauncher)
                        if (isReady) {
                            Log.d(LOG_ID, "Health Connect ready to use.")
                        } else {
                            Log.d(LOG_ID, "Health Connect requirements not met, actions initiated.")
                        }
                    }
                }
            } else
                super.onSharedPreferenceChanged(sharedPreferences, key)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString())
        }
    }
}

class TransferBroadcastFragement: SettingsFragmentBase(R.xml.pref_transfer_localapps) {
    override fun initPreferences() {
        Log.v(LOG_ID, "initPreferences called")
        super.initPreferences()
        setupReceivers(Constants.GLUCODATA_BROADCAST_ACTION, Constants.SHARED_PREF_GLUCODATA_RECEIVERS)
        setupReceivers(Constants.XDRIP_ACTION_GLUCOSE_READING, Constants.SHARED_PREF_XDRIP_RECEIVERS)
        setupReceivers(Intents.XDRIP_BROADCAST_ACTION, Constants.SHARED_PREF_XDRIP_BROADCAST_RECEIVERS)
    }
}

class TransferNightscoutFragment: SettingsFragmentBase(R.xml.pref_transfer_nightscout) {

    override fun initPreferences() {
        Log.v(LOG_ID, "initPreferences called")
        super.initPreferences()
        setPasswordPref(Constants.SHARED_PREF_NIGHTSCOUT_UPLOAD_SECRET)
    }

    override fun onResume() {
        super.onResume()
        update()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        try {
            super.onSharedPreferenceChanged(sharedPreferences, key)
            if(key == Constants.SHARED_PREF_NIGHTSCOUT_UPLOAD_URL)
                update()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString())
        }
    }

    private fun update() {
        try {
            val sharedPref = preferenceManager.sharedPreferences!!
            val switchNightscout = findPreference<SwitchPreferenceCompat>(Constants.SHARED_PREF_NIGHTSCOUT_UPLOAD_ENABLED)
            if (switchNightscout != null) {
                val url = sharedPref.getString(Constants.SHARED_PREF_NIGHTSCOUT_UPLOAD_URL, "")!!.trim()
                switchNightscout.isEnabled = url.isNotEmpty() && url.isNotEmpty()
                if(!switchNightscout.isEnabled)
                    switchNightscout.isChecked = false
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateEnableStates exception: " + exc.toString())
        }
    }
}