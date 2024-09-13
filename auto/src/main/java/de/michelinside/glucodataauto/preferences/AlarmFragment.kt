package de.michelinside.glucodataauto.preferences

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import de.michelinside.glucodataauto.R
import de.michelinside.glucodatahandler.common.R as CR
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.notification.AlarmHandler
import de.michelinside.glucodatahandler.common.notification.AlarmType
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.Utils

class AlarmFragment : PreferenceFragmentCompat() {
    private val LOG_ID = "GDH.AA.AlarmFragment"
    companion object {
        var settingsChanged = false
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Log.d(LOG_ID, "onCreatePreferences called")
        try {
            settingsChanged = false
            preferenceManager.sharedPreferencesName = Constants.SHARED_PREF_TAG
            setPreferencesFromResource(R.xml.alarms, rootKey)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreatePreferences exception: " + exc.toString())
        }
    }

    override fun onDestroyView() {
        Log.d(LOG_ID, "onDestroyView called")
        try {
            if (settingsChanged) {
                Log.v(LOG_ID, "Notify alarm_settings change")
                InternalNotifier.notify(requireContext(), NotifySource.ALARM_SETTINGS, AlarmHandler.getSettings(true))
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onDestroyView exception: " + exc.toString())
        }
        super.onDestroyView()
    }


    override fun onResume() {
        Log.d(LOG_ID, "onResume called")
        try {
            update()
            super.onResume()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onResume exception: " + exc.toString())
        }
    }

    override fun onPause() {
        Log.d(LOG_ID, "onPause called")
        try {
            super.onPause()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onPause exception: " + exc.toString())
        }
    }

    @SuppressLint("InlinedApi")
    private fun update() {
        Log.d(LOG_ID, "update called")
        try {
            updateAlarmCat(Constants.SHARED_PREF_ALARM_LOW)
            updateAlarmCat(Constants.SHARED_PREF_ALARM_HIGH)
            updateAlarmCat(Constants.SHARED_PREF_ALARM_VERY_HIGH)
            updateAlarmCat(Constants.SHARED_PREF_ALARM_OBSOLETE)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onPause exception: " + exc.toString())
        }
    }

    private fun updateAlarmCat(key: String) {
        val pref = findPreference<Preference>(key) ?: return
        val alarmType = AlarmType.fromIndex(pref.extras.getInt("type"))
        pref.summary = getAlarmCatSummary(alarmType)
        pref.icon = ContextCompat.getDrawable(requireContext(), getAlarmCatIcon(alarmType, key + "_enabled"))
    }

    private fun getAlarmCatIcon(alarmType: AlarmType, enableKey: String): Int {
        if(alarmType != AlarmType.VERY_LOW && !preferenceManager.sharedPreferences!!.getBoolean(enableKey, true)) {
            return R.drawable.icon_popup_off
        }
        return R.drawable.icon_popup
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

}

