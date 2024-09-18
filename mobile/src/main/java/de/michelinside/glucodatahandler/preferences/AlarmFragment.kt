package de.michelinside.glucodatahandler.preferences

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import de.michelinside.glucodatahandler.Dialogs
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.R as CR
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.notification.AlarmHandler
import de.michelinside.glucodatahandler.common.notification.AlarmType
import de.michelinside.glucodatahandler.common.notification.SoundMode
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.Utils
import de.michelinside.glucodatahandler.notification.AlarmNotification

class AlarmFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {
    companion object {
        private val LOG_ID = "GDH.AlarmFragment"
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
            setPreferencesFromResource(R.xml.alarms, rootKey)
            if(!AlarmNotification.channelActive(requireContext())) {
                Log.e(LOG_ID, "Notification disabled!!!")
            }
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
            preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
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

    @SuppressLint("InlinedApi")
    private fun update() {
        Log.d(LOG_ID, "update called")
        try {
            if(!AlarmNotification.channelActive(requireContext())) {
                disableSwitch(Constants.SHARED_PREF_ALARM_NOTIFICATION_ENABLED)
            }

            if (!AlarmNotification.hasFullscreenPermission()) {
                disableSwitch(Constants.SHARED_PREF_ALARM_FULLSCREEN_NOTIFICATION_ENABLED)
            }

            updateAlarmCat(Constants.SHARED_PREF_ALARM_VERY_LOW)
            updateAlarmCat(Constants.SHARED_PREF_ALARM_LOW)
            updateAlarmCat(Constants.SHARED_PREF_ALARM_HIGH)
            updateAlarmCat(Constants.SHARED_PREF_ALARM_VERY_HIGH)
            updateAlarmCat(Constants.SHARED_PREF_ALARM_OBSOLETE)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onPause exception: " + exc.toString())
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

    private fun updateAlarmCat(key: String) {
        val pref = findPreference<Preference>(key) ?: return
        val alarmType = AlarmType.fromIndex(pref.extras.getInt("type"))
        pref.icon = ContextCompat.getDrawable(requireContext(), getAlarmCatIcon(alarmType, requireContext()))
        pref.summary = getAlarmCatSummary(alarmType)
    }

    private fun getAlarmCatIcon(alarmType: AlarmType, context: Context): Int {
        if(alarmType.setting == null || !alarmType.setting!!.isActive) {
            if (alarmType.setting?.isTempInactive == true)
                return CR.drawable.icon_clock_snooze_com
            return SoundMode.OFF.icon
        }
        return AlarmNotification.getSoundMode(alarmType, context).icon
    }

    private fun getAlarmCatSummary(alarmType: AlarmType): String {
        return when(alarmType) {
            AlarmType.VERY_LOW,
            AlarmType.LOW,
            AlarmType.HIGH,
            AlarmType.VERY_HIGH -> resources.getString(CR.string.alarm_type_summary, getBorderText(alarmType))
            AlarmType.OBSOLETE -> resources.getString(CR.string.alarm_obsolete_summary, getBorderText(alarmType))
            else -> ""
        }
    }

    private fun getBorderText(alarmType: AlarmType): String {
        var value = when(alarmType) {
            AlarmType.VERY_LOW -> ReceiveData.low
            AlarmType.LOW -> ReceiveData.targetMin
            AlarmType.HIGH -> ReceiveData.targetMax
            AlarmType.VERY_HIGH -> ReceiveData.high
            AlarmType.OBSOLETE -> AlarmType.OBSOLETE.setting!!.intervalMin.toFloat()
            else -> 0F
        }

        if (alarmType == AlarmType.OBSOLETE) {
            return "${value.toInt()}"
        }

        if (alarmType == AlarmType.LOW) {
            if(ReceiveData.isMmol)
                value = Utils.round(value-0.1F, 1)
            else
                value -= 1F
        }

        if (alarmType == AlarmType.HIGH) {
            if(ReceiveData.isMmol)
                value = Utils.round(value+0.1F, 1)
            else
                value += 1F
        }
        return "$value ${ReceiveData.getUnit()}"
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        Log.d(LOG_ID, "onSharedPreferenceChanged called for " + key)
        try {
            when(key) {
                Constants.SHARED_PREF_ALARM_NOTIFICATION_ENABLED -> {
                    if (sharedPreferences.getBoolean(Constants.SHARED_PREF_ALARM_NOTIFICATION_ENABLED, false) && !AlarmNotification.channelActive(requireContext())) {
                        requestChannelActivation()
                    }
                }
                Constants.SHARED_PREF_ALARM_FULLSCREEN_NOTIFICATION_ENABLED -> {
                    if (sharedPreferences.getBoolean(Constants.SHARED_PREF_ALARM_FULLSCREEN_NOTIFICATION_ENABLED, false) && !AlarmNotification.hasFullscreenPermission()) {
                        requestFullScreenPermission(requireContext())
                    }
                }
            }
            if(AlarmHandler.isAlarmSettingToShare(key))
                settingsChanged = true
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString())
        }
    }
    private fun requestChannelActivation() {
        Dialogs.showOkDialog(requireContext(),CR.string.permission_alarm_notification_title, CR.string.permission_alarm_notification_message) { _, _ ->
            val intent: Intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
            startActivity(intent)
        }
    }
}

