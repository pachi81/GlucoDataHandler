package de.michelinside.glucodatahandler.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.appcompat.widget.SwitchCompat
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.Constants

class AlarmAdvancedActivity : AppCompatActivity() {
    private val LOG_ID = "GDH.Main.Alarms"

    private lateinit var sharedPref: SharedPreferences
    private lateinit var switchVibration: SwitchCompat
    private lateinit var switchForceSound: SwitchCompat
    private lateinit var switchUseAlarmSound: SwitchCompat
    private lateinit var txtStartDelayLevel: TextView
    private lateinit var seekStartDelay: AppCompatSeekBar
    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            Log.v(LOG_ID, "onCreate called")
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_alarm_advanced)

            sharedPref = this.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)

            switchForceSound = findViewById(R.id.switchForceSound)
            switchVibration = findViewById(R.id.switchVibration)
            switchUseAlarmSound = findViewById(R.id.switchUseAlarmSound)
            txtStartDelayLevel = findViewById(R.id.txtStartDelayLevel)
            seekStartDelay = findViewById(R.id.seekStartDelay)

            switchUseAlarmSound.isChecked = sharedPref.getBoolean(Constants.SHARED_PREF_NOTIFICATION_USE_ALARM_SOUND, true)
            switchUseAlarmSound.setOnCheckedChangeListener { _, isChecked ->
                Log.d(LOG_ID, "Force sound changed: " + isChecked.toString())
                try {
                    with (sharedPref.edit()) {
                        putBoolean(Constants.SHARED_PREF_NOTIFICATION_USE_ALARM_SOUND, isChecked)
                        apply()
                    }
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Changing notification exception: " + exc.message.toString() )
                }
            }

            switchForceSound.isChecked = sharedPref.getBoolean(Constants.SHARED_PREF_ALARM_FORCE_SOUND, false)
            switchForceSound.setOnCheckedChangeListener { _, isChecked ->
                Log.d(LOG_ID, "Force sound changed: " + isChecked.toString())
                try {
                    with (sharedPref.edit()) {
                        putBoolean(Constants.SHARED_PREF_ALARM_FORCE_SOUND, isChecked)
                        apply()
                    }
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Changing notification exception: " + exc.message.toString() )
                }
            }

            switchVibration.isChecked = sharedPref.getBoolean(Constants.SHARED_PREF_NOTIFICATION_VIBRATE, false)
            switchVibration.setOnCheckedChangeListener { _, isChecked ->
                Log.d(LOG_ID, "Vibration changed: " + isChecked.toString())
                try {
                    with (sharedPref.edit()) {
                        putBoolean(Constants.SHARED_PREF_NOTIFICATION_VIBRATE, isChecked)
                        apply()
                    }
                    switchForceSound.isEnabled = !isChecked
                    switchUseAlarmSound.isEnabled = !isChecked
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Changing notification exception: " + exc.message.toString() )
                }
            }

            switchForceSound.isEnabled = !switchVibration.isChecked
            switchUseAlarmSound.isEnabled = !switchVibration.isChecked

            seekStartDelay.progress = sharedPref.getInt(Constants.SHARED_PREF_ALARM_START_DELAY, 0)
            txtStartDelayLevel.text = getStartDelayString()
            seekStartDelay.setOnSeekBarChangeListener(SeekBarChangeListener(Constants.SHARED_PREF_ALARM_START_DELAY, txtStartDelayLevel))


        } catch( exc: Exception ) {
            Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
        }
    }

    override fun onPause() {
        try {
            Log.v(LOG_ID, "onPause called")
            super.onPause()
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

    private fun getStartDelayString(): String {
       return "${seekStartDelay.progress.toFloat()*0.5F}s"
    }

    inner class SeekBarChangeListener(val preference: String, val txtLevel: TextView) : SeekBar.OnSeekBarChangeListener {
        private val LOG_ID = "GDH.Main.AlarmAdvancedSeekBar"
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            try {
                Log.v(LOG_ID, "onProgressChanged called for $preference with progress=$progress - fromUser=$fromUser")
                if(fromUser) {
                    txtLevel.text = getStartDelayString()
                }
            } catch( exc: Exception ) {
                Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
            }
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {
            try {
                Log.v(LOG_ID, "onStartTrackingTouch called")
            } catch( exc: Exception ) {
                Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
            }
        }

        override fun onStopTrackingTouch(seekBar: SeekBar?) {
            try {
                if(seekBar != null) {
                    Log.v(LOG_ID, "onStopTrackingTouch called with current progress: ${seekBar.progress}")
                    with(sharedPref.edit()) {
                        putInt(preference, seekBar.progress*500)
                        apply()
                    }
                }
            } catch( exc: Exception ) {
                Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
            }
        }
    }
}