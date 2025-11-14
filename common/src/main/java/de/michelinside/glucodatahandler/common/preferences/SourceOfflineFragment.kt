package de.michelinside.glucodatahandler.common.preferences

import android.content.SharedPreferences
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.SwitchPreferenceCompat
import de.michelinside.glucodatahandler.common.AppSource
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.R
import de.michelinside.glucodatahandler.common.receiver.GlucoseDataReceiver
import de.michelinside.glucodatahandler.common.ui.Dialogs
import de.michelinside.glucodatahandler.common.utils.Log


class SourceOfflineFragment : PreferenceFragmentCompatBase() {
    private val LOG_ID = "GDH.SourceOfflineFragment"

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Log.d(LOG_ID, "onCreatePreferences called")
        try {
            preferenceManager.sharedPreferencesName = Constants.SHARED_PREF_TAG
            setPreferencesFromResource(R.xml.sources_offline, rootKey)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreatePreferences exception: " + exc.toString())
        }
    }

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
            val prefSources = findPreference<PreferenceCategory>("pref_cat_sources")
            if(prefSources != null && preferenceManager.sharedPreferences != null) {
                Log.d(LOG_ID, "Update enable icons for ${prefSources.preferenceCount} sources")
                for (i in 0 until prefSources.preferenceCount) {
                    val pref: Preference = prefSources.getPreference(i)
                    if(!pref.key.isNullOrEmpty()) {
                        if(preferenceManager.sharedPreferences!!.getBoolean(pref.key, false)) {
                            pref.icon = ContextCompat.getDrawable(requireContext(), R.drawable.switch_on)
                        } else {
                            pref.icon = ContextCompat.getDrawable(requireContext(), R.drawable.switch_off)
                        }
                    }
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateEnableStates exception: " + exc.toString())
        }
    }
}

abstract class SourceOfflineFragmentBase(val preferenceResId: Int) : PreferenceFragmentCompatBase(), SharedPreferences.OnSharedPreferenceChangeListener {
    protected val LOG_ID = "GDH.SourceOfflineFragmentBase"
    private val updateEnablePrefs = mutableSetOf<String>()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Log.d(LOG_ID, "onCreatePreferences called")
        try {
            preferenceManager.sharedPreferencesName = Constants.SHARED_PREF_TAG
            setPreferencesFromResource(preferenceResId, rootKey)

            if(GlucoDataService.appSource == AppSource.AUTO_APP) {
                val prefGDH = findPreference<PreferenceCategory>("cat_gdh_info")
                prefGDH?.isVisible = true
            }

            initPreferences()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreatePreferences exception: " + exc.toString())
        }
    }

    protected open fun initPreferences() {}

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

    protected open fun update() {}

    protected open fun updateEnableStates(sharedPreferences: SharedPreferences) {}


    protected fun setupLocalIobAction(preference: Preference?) {
        preference?.setOnPreferenceClickListener {
            Dialogs.showOkCancelDialog(requireContext(),
                R.string.activate_local_nightscout_iob_title,
                R.string.activate_local_nightscout_iob_message,
                { _, _ -> with(preferenceManager!!.sharedPreferences!!.edit()) {
                    putString(Constants.SHARED_PREF_NIGHTSCOUT_URL, "http://127.0.0.1:17580")
                    putString(Constants.SHARED_PREF_NIGHTSCOUT_SECRET, "")
                    putString(Constants.SHARED_PREF_NIGHTSCOUT_TOKEN, "")
                    apply()
                }
                    with(preferenceManager!!.sharedPreferences!!.edit()) {
                        putBoolean(Constants.SHARED_PREF_NIGHTSCOUT_IOB_COB, true)
                        putBoolean(Constants.SHARED_PREF_NIGHTSCOUT_ENABLED, true)
                        apply()
                    }
                })
            true
        }
    }
}

class SourceJuggluco: SourceOfflineFragmentBase(R.xml.source_juggluco) {
    override fun initPreferences() {
        super.initPreferences()
        PreferenceHelper.setLinkOnClick(findPreference("source_juggluco_video"), R.string.video_tutorial_juggluco, requireContext())
    }

    override fun update() {
        val sharedPref = preferenceManager.sharedPreferences!!
        updateEnableStates(sharedPref)
    }

    override fun updateEnableStates(sharedPreferences: SharedPreferences) {
        try {
            Log.d(LOG_ID, "updateEnableStates called")
            setEnableState<SwitchPreferenceCompat>(sharedPreferences, Constants.SHARED_PREF_SOURCE_JUGGLUCO_WEBSERVER_ENABLED, Constants.SHARED_PREF_SOURCE_JUGGLUCO_ENABLED)
            setEnableState<SwitchPreferenceCompat>(sharedPreferences, Constants.SHARED_PREF_SOURCE_JUGGLUCO_WEBSERVER_IOB_SUPPORT, Constants.SHARED_PREF_SOURCE_JUGGLUCO_WEBSERVER_ENABLED, Constants.SHARED_PREF_SOURCE_JUGGLUCO_ENABLED)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateEnableStates exception: " + exc.toString())
        }
    }
}

class SourceXDripBroadcastAPI: SourceOfflineFragmentBase(R.xml.source_xdrip_broadcast_api)
class SourceXDrip: SourceOfflineFragmentBase(R.xml.source_xdrip) {
    override fun initPreferences() {
        super.initPreferences()
        setupLocalIobAction(findPreference(Constants.SHARED_PREF_SOURCE_XDRIP_SET_NS_IOB_ACTION))
    }
}
class SourceAaps: SourceOfflineFragmentBase(R.xml.source_aaps)
class SourceEversense: SourceOfflineFragmentBase(R.xml.source_eversense) {
    override fun initPreferences() {
        super.initPreferences()
        PreferenceHelper.setLinkOnClick(findPreference(Constants.SHARED_PREF_EVERSENSE_ESEL_INFO), R.string.esel_link, requireContext())
    }
}
class SourceDiabox: SourceOfflineFragmentBase(R.xml.source_diabox)
class SourceLibrePatched: SourceOfflineFragmentBase(R.xml.source_libre_patched)
class SourceDexcomByodaAaps: SourceOfflineFragmentBase(R.xml.source_dexcom_byoda_aaps)