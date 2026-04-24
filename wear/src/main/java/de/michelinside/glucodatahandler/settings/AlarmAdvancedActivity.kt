package de.michelinside.glucodatahandler.settings

import android.content.SharedPreferences
import android.os.Bundle
import de.michelinside.glucodatahandler.common.utils.Log
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.appcompat.widget.SwitchCompat
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.Constants
import androidx.core.content.edit
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifySource

class AlarmAdvancedActivity : AppCompatActivity() {
    private val LOG_ID = "GDH.Main.Alarms"

    private lateinit var sharedPref: SharedPreferences
    private lateinit var switchVibration: SwitchCompat
    private lateinit var switchForceSound: SwitchCompat
    private lateinit var switchUseAlarmSound: SwitchCompat
    private lateinit var switchNoAlarmCharging: SwitchCompat
    private lateinit var switchNoAlarmPhone: SwitchCompat
    private lateinit var txtStartDelayLevel: TextView
    private lateinit var seekStartDelay: AppCompatSeekBar
    private lateinit var switchEnableAlarmIcon: SwitchCompat
    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            Log.v(LOG_ID, "onCreate called")
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_alarm_advanced)

            sharedPref = this.getSharedPreferences(Constants.SHARED_PREF_TAG, MODE_PRIVATE)

            switchForceSound = findViewById(R.id.switchForceSound)
            switchVibration = findViewById(R.id.switchVibration)
            switchUseAlarmSound = findViewById(R.id.switchUseAlarmSound)
            txtStartDelayLevel = findViewById(R.id.txtStartDelayLevel)
            seekStartDelay = findViewById(R.id.seekStartDelay)
            switchNoAlarmCharging = findViewById(R.id.switchNoAlarmCharging)
            switchNoAlarmPhone = findViewById(R.id.switchNoAlarmPhone)
            switchEnableAlarmIcon = findViewById(R.id.switchEnableAlarmIcon)

            switchUseAlarmSound.isChecked = sharedPref.getBoolean(Constants.SHARED_PREF_NOTIFICATION_USE_ALARM_SOUND, true)
            switchUseAlarmSound.setOnCheckedChangeListener { _, isChecked ->
                Log.d(LOG_ID, "Use alarm changed: $isChecked")
                try {
                    sharedPref.edit {
                        putBoolean(Constants.SHARED_PREF_NOTIFICATION_USE_ALARM_SOUND, isChecked)
                    }
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Changing use alarm exception: " + exc.message.toString() )
                }
            }

            switchForceSound.isChecked = sharedPref.getBoolean(Constants.SHARED_PREF_ALARM_FORCE_SOUND, false)
            switchForceSound.setOnCheckedChangeListener { _, isChecked ->
                Log.d(LOG_ID, "Force sound changed: $isChecked")
                try {
                    sharedPref.edit {
                        putBoolean(Constants.SHARED_PREF_ALARM_FORCE_SOUND, isChecked)
                    }
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Changing force sound exception: " + exc.message.toString() )
                }
            }

            switchVibration.isChecked = sharedPref.getBoolean(Constants.SHARED_PREF_NOTIFICATION_VIBRATE, false)
            switchVibration.setOnCheckedChangeListener { _, isChecked ->
                Log.d(LOG_ID, "Vibration changed: $isChecked")
                try {
                    sharedPref.edit {
                        putBoolean(Constants.SHARED_PREF_NOTIFICATION_VIBRATE, isChecked)
                    }
                    switchForceSound.isEnabled = !isChecked
                    switchUseAlarmSound.isEnabled = !isChecked
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Changing vibration exception: " + exc.message.toString() )
                }
            }

            switchForceSound.isEnabled = !switchVibration.isChecked
            switchUseAlarmSound.isEnabled = !switchVibration.isChecked

            switchUseAlarmSound.isChecked = sharedPref.getBoolean(Constants.SHARED_PREF_NOTIFICATION_USE_ALARM_SOUND, true)
            switchUseAlarmSound.setOnCheckedChangeListener { _, isChecked ->
                Log.d(LOG_ID, "Use alarm changed: " + isChecked.toString())
                try {
                    sharedPref.edit {
                        putBoolean(Constants.SHARED_PREF_NOTIFICATION_USE_ALARM_SOUND, isChecked)
                    }
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Changing use alarm exception: " + exc.message.toString() )
                }
            }

            seekStartDelay.progress = sharedPref.getInt(Constants.SHARED_PREF_ALARM_START_DELAY, 1500)/500
            txtStartDelayLevel.text = getStartDelayString()
            seekStartDelay.setOnSeekBarChangeListener(SeekBarChangeListener(Constants.SHARED_PREF_ALARM_START_DELAY, txtStartDelayLevel))

            switchEnableAlarmIcon.isChecked = sharedPref.getBoolean(Constants.SHARED_PREF_ENABLE_ALARM_ICON_TOGGLE, true)
            switchEnableAlarmIcon.setOnCheckedChangeListener { _, isChecked ->
                Log.d(LOG_ID, "Enable Alarm Icon changed: $isChecked")
                try {
                    sharedPref.edit {
                        putBoolean(Constants.SHARED_PREF_ENABLE_ALARM_ICON_TOGGLE, isChecked)
                    }
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Changing alarm icon exception: " + exc.message.toString() )
                }
            }

            switchNoAlarmCharging.isChecked = sharedPref.getBoolean(Constants.SHARED_PREF_WEAR_NO_ALARM_WHILE_CHARGING, false)
            switchNoAlarmCharging.setOnCheckedChangeListener { _, isChecked ->
                Log.d(LOG_ID, "No alarm while charging connected changed: $isChecked")
                try {
                    sharedPref.edit {
                        putBoolean(
                            Constants.SHARED_PREF_WEAR_NO_ALARM_WHILE_CHARGING,
                            isChecked
                        )
                    }
                    InternalNotifier.notify(this, NotifySource.WATCH_SETTINGS, GlucoDataService.getSettings())
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "No alarm while charging connected exception: " + exc.message.toString() )
                }
            }

            switchNoAlarmPhone.isChecked = sharedPref.getBoolean(Constants.SHARED_PREF_WEAR_NO_ALARM_POPUP_PHONE_CONNECTED, false)
            switchNoAlarmPhone.setOnCheckedChangeListener { _, isChecked ->
                Log.d(LOG_ID, "No popup while phone connected changed: $isChecked")
                try {
                    sharedPref.edit {
                        putBoolean(
                            Constants.SHARED_PREF_WEAR_NO_ALARM_POPUP_PHONE_CONNECTED,
                            isChecked
                        )
                    }
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "No popup while phone connected exception: " + exc.message.toString() )
                }
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

    private fun getStartDelayString(): String {
       return "${seekStartDelay.progress.toFloat()*0.5F} s"
    }

    inner class SeekBarChangeListener(val preference: String, val txtLevel: TextView) : SeekBar.OnSeekBarChangeListener {
        private val LOG_ID = "GDH.Main.AlarmAdvancedSeekBar"
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            try {
                Log.v(LOG_ID, "onProgressChanged called for $preference with progress=$progress - fromUser=$fromUser")
                if(fromUser) {
                    txtLevel.text = getStartDelayString()
                    sharedPref.edit {
                        putInt(preference, progress * 500)
                    }
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
                    sharedPref.edit {
                        putInt(preference, seekBar.progress * 500)
                    }
                }
            } catch( exc: Exception ) {
                Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
            }
        }
    }
}