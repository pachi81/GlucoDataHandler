package de.michelinside.glucodatahandler.preferences

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.os.Bundle
import de.michelinside.glucodatahandler.common.utils.Log
import androidx.preference.MultiSelectListPreference
import androidx.preference.SwitchPreferenceCompat
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.R as CR
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.notification.AlarmHandler
import de.michelinside.glucodatahandler.common.notification.AlarmSetting
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.Utils
import de.michelinside.glucodatahandler.notification.AlarmNotification
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

class AlarmGeneralFragment : SettingsFragmentCompatBase(), SharedPreferences.OnSharedPreferenceChangeListener {
    companion object {
        private val LOG_ID = "GDH.AlarmGeneralFragment"
        var settingsChanged = false

        fun requestFullScreenPermission(context: Context) {
            try {
                Log.v(LOG_ID, "requestFullScreenPermission called")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        Uri.parse("package:" + context.packageName)
                    )
                    intent.putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            } catch (exc: Exception) {
                Log.e(LOG_ID, "requestOverlayPermission exception: " + exc.toString())
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Log.d(LOG_ID, "onCreatePreferences called")
        try {
            settingsChanged = false
            preferenceManager.sharedPreferencesName = Constants.SHARED_PREF_TAG
            setPreferencesFromResource(R.xml.alarm_general, rootKey)
            if(!AlarmNotification.channelActive(requireContext())) {
                Log.e(LOG_ID, "Notification disabled!!!")
            }

            val prefWeekdays = findPreference<MultiSelectListPreference>(Constants.SHARED_PREF_ALARM_INACTIVE_WEEKDAYS)
            prefWeekdays!!.entries = DayOfWeek.entries.map { it.getDisplayName(TextStyle.FULL, Locale.getDefault()) }.toTypedArray()
            prefWeekdays.entryValues = DayOfWeek.entries.map { it.value.toString() }.toTypedArray()
            prefWeekdays.values = preferenceManager.sharedPreferences!!.getStringSet(prefWeekdays.key, AlarmSetting.defaultWeekdays)!!
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreatePreferences exception: " + exc.toString())
        }
    }

    override fun onDestroyView() {
        Log.d(LOG_ID, "onDestroyView called")
        try {
            if (settingsChanged) {
                Log.v(LOG_ID, "Notify alarm_settings change")
                InternalNotifier.notify(requireContext(), NotifySource.ALARM_SETTINGS, AlarmHandler.getSettings())
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onDestroyView exception: " + exc.toString())
        }
        super.onDestroyView()
    }


    override fun onResume() {
        Log.d(LOG_ID, "onResume called")
        try {
            super.onResume()
            preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
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

    @SuppressLint("InlinedApi")
    private fun update() {
        Log.d(LOG_ID, "update called")
        try {
            if (!AlarmNotification.hasFullscreenPermission(requireContext())) {
                disableSwitch(Constants.SHARED_PREF_ALARM_FULLSCREEN_NOTIFICATION_ENABLED)
            }

            updateInactiveTime()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onPause exception: " + exc.toString())
        }
    }

    private fun updateInactiveTime() {
        val prefWeekdays = findPreference<MultiSelectListPreference>(Constants.SHARED_PREF_ALARM_INACTIVE_WEEKDAYS)
        prefWeekdays!!.summary = resources.getString(CR.string.alarm_inactive_weekdays_summary) + "\n" + prefWeekdays.values.joinToString(
            ", "
        ) { DayOfWeek.of(it.toInt()).getDisplayName(TextStyle.SHORT, Locale.getDefault()) }

        val inactivePref = findPreference<SwitchPreferenceCompat>(Constants.SHARED_PREF_ALARM_INACTIVE_ENABLED)
        if (inactivePref != null) {
            val prefStart = findPreference<MyTimeTickerPreference>(Constants.SHARED_PREF_ALARM_INACTIVE_START_TIME)
            val prefEnd = findPreference<MyTimeTickerPreference>(Constants.SHARED_PREF_ALARM_INACTIVE_END_TIME)
            inactivePref.isEnabled = Utils.isValidTime(prefStart!!.getTimeString()) && Utils.isValidTime(prefEnd!!.getTimeString()) && prefWeekdays.values.isNotEmpty()
        }
    }

    private fun disableSwitch(prefname: String) {
        val pref =
            findPreference<SwitchPreferenceCompat>(prefname)
        if (pref != null && pref.isChecked) {
            Log.i(LOG_ID, "Disable preference $prefname as there is no permission!")
            pref.isChecked = false
            with(preferenceManager.sharedPreferences!!.edit()) {
                putBoolean(prefname, false)
                apply()
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        Log.d(LOG_ID, "onSharedPreferenceChanged called for " + key)
        try {
            when(key) {
                Constants.SHARED_PREF_ALARM_FULLSCREEN_NOTIFICATION_ENABLED -> {
                    if (sharedPreferences.getBoolean(Constants.SHARED_PREF_ALARM_FULLSCREEN_NOTIFICATION_ENABLED, false) && !AlarmNotification.hasFullscreenPermission(requireContext())) {
                        requestFullScreenPermission(requireContext())
                    }
                }
                Constants.SHARED_PREF_ALARM_INACTIVE_WEEKDAYS,
                Constants.SHARED_PREF_ALARM_INACTIVE_START_TIME,
                Constants.SHARED_PREF_ALARM_INACTIVE_END_TIME -> updateInactiveTime()
            }
            if(AlarmHandler.isAlarmSettingToShare(key))
                settingsChanged = true
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString())
        }
    }
}

