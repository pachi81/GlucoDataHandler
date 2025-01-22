package de.michelinside.glucodatahandler.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import de.michelinside.glucodatahandler.AlarmNotificationWear
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.notification.AlarmType
import de.michelinside.glucodatahandler.common.notification.VibratePattern
import de.michelinside.glucodatahandler.common.notification.Vibrator
import de.michelinside.glucodatahandler.common.utils.Utils

class VibrationPatternActivity : AppCompatActivity() {
    private val LOG_ID = "GDH.Main.ComplicationsTapActionActivity"

    private lateinit var sharedPref: SharedPreferences
    private var hasChanged = false
    private var currentPattern = ""
    private var alarmType = AlarmType.NONE


    private val vibratePatternPref: String get() {
        return alarmType.setting!!.getSettingName(Constants.SHARED_PREF_ALARM_SUFFIX_VIBRATE_PATTERN)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            Log.v(LOG_ID, "onCreate called")
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_vibration_pattern)

            if(intent.extras == null || !intent.extras!!.containsKey("type")) {
                Log.e(LOG_ID, "Missing extras: ${Utils.dumpBundle(intent.extras)}")
                finish()
            }

            sharedPref = this.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)

            alarmType = AlarmType.fromIndex(intent.extras!!.getInt("type"))
            if (alarmType.setting == null) {
                Log.e(LOG_ID, "Unsupported alarm type for creating activity: $alarmType!")
                finish()
            }
            currentPattern = alarmType.setting!!.vibratePatternKey
            updatePattern()
        } catch( exc: Exception ) {
            Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
        }
    }

    override fun onPause() {
        try {
            Log.v(LOG_ID, "onPause called")
            super.onPause()
            Vibrator.cancel()
            if(hasChanged) {
                hasChanged = false
            }
        } catch( exc: Exception ) {
            Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
        }
    }

    override fun onResume() {
        try {
            Log.v(LOG_ID, "onResume called")
            super.onResume()
        } catch( exc: Exception ) {
            Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
        }
    }

    override fun onDestroy() {
        try {
            Log.v(LOG_ID, "onDestroy called")
            super.onDestroy()
        } catch( exc: Exception ) {
            Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
        }
    }


    private fun createRadioButtons(group: RadioGroup): RadioButton? {
        var current: RadioButton? = null
        VibratePattern.entries.forEach {
            val ch = RadioButton(this)
            ch.text = resources.getString(it.resId)
            ch.hint = it.key
            if (currentPattern == ch.hint) {
                current = ch
            }
            group.addView(ch)
        }
        return current
    }

    private fun updatePattern() {
        try {
            val receiverLayout = findViewById<LinearLayout>(R.id.linearLayout)
            receiverLayout.removeAllViews()

            val group = RadioGroup(this)
            val current = createRadioButtons(group)
            receiverLayout.addView(group)
            if (current != null) {
                current.isChecked = true
                Log.v(LOG_ID, "Current currentPattern set for ${current.text}")
            } else {
                currentPattern = ""
            }
            group.setOnCheckedChangeListener { _, checkedId ->
                Log.i(LOG_ID, "OnCheckedChangeListener called with checkedId: $checkedId")
                try {
                    group.findViewById<RadioButton>(checkedId).let {
                        val buttonView = it as RadioButton
                        currentPattern = buttonView.hint.toString()
                        Log.v(LOG_ID, "Set currentPattern: $currentPattern")
                        with(sharedPref.edit()) {
                            putString(vibratePatternPref, currentPattern)
                            apply()
                        }
                        hasChanged = true
                        val currentPattern = VibratePattern.getByKey(currentPattern).pattern
                        if(currentPattern != null) {
                            Vibrator.vibrate(currentPattern, -1, alarmType.setting!!.vibrateAmplitude, AlarmNotificationWear.useAlarmStream)
                        } else {
                            Vibrator.cancel()
                        }
                    }
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "OnCheckedChangeListener exception: " + exc.toString())
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updatePattern exception: " + exc.message.toString() )
        }
    }

}