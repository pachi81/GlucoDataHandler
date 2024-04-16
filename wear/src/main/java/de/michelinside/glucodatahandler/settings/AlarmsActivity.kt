package de.michelinside.glucodatahandler.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.Constants

class AlarmsActivity : AppCompatActivity() {
    private val LOG_ID = "GDH.Main.Alarms"

    private lateinit var sharedPref: SharedPreferences
    private lateinit var switchVibration: SwitchCompat
    private lateinit var switchNotification: SwitchCompat
    private lateinit var switchForceSound: SwitchCompat
    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            Log.v(LOG_ID, "onCreate called")
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_alarms)

            sharedPref = this.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)


            switchForceSound = findViewById(R.id.switchForceSound)
            switchVibration = findViewById(R.id.switchVibration)
            switchNotification = findViewById(R.id.switchNotification)


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
                    if(isChecked)
                        switchNotification.isChecked = false
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Changing notification exception: " + exc.message.toString() )
                }
            }

            switchNotification.isChecked = sharedPref.getBoolean(Constants.SHARED_PREF_ALARM_NOTIFICATION_ENABLED, false)
            switchNotification.setOnCheckedChangeListener { _, isChecked ->
                Log.d(LOG_ID, "Notification changed: " + isChecked.toString())
                try {
                    with (sharedPref.edit()) {
                        putBoolean(Constants.SHARED_PREF_ALARM_NOTIFICATION_ENABLED, isChecked)
                        apply()
                    }
                    if(isChecked)
                        switchVibration.isChecked = false
                    switchForceSound.isEnabled = isChecked
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Changing notification exception: " + exc.message.toString() )
                }
            }

            if(switchNotification.isChecked && switchVibration.isChecked) {
                switchVibration.isChecked = false
            }

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
}