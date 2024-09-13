package de.michelinside.glucodatahandler.settings

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import de.michelinside.glucodatahandler.AlarmNotificationWear
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.notification.AlarmHandler
import de.michelinside.glucodatahandler.common.notification.AlarmType

class AlarmsActivity : AppCompatActivity() {
    private val LOG_ID = "GDH.Main.Alarms"

    private lateinit var sharedPref: SharedPreferences
    private lateinit var switchNotification: SwitchCompat
    private lateinit var btnAlarmAdvancedSettings: Button
    private lateinit var snooze_30: Button
    private lateinit var snooze_60: Button
    private lateinit var snooze_90: Button
    private lateinit var snooze_120: Button
    private lateinit var btnStopSnooze: Button
    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            Log.v(LOG_ID, "onCreate called")
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_alarms)

            sharedPref = this.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)


            switchNotification = findViewById(R.id.switchNotification)
            btnAlarmAdvancedSettings = findViewById(R.id.btnAlarmAdvancedSettings)
            snooze_30 = findViewById(R.id.snooze_30)
            snooze_60 = findViewById(R.id.snooze_60)
            snooze_90 = findViewById(R.id.snooze_90)
            snooze_120 = findViewById(R.id.snooze_120)
            btnStopSnooze = findViewById(R.id.btnStopSnooze)

            switchNotification.isChecked = sharedPref.getBoolean(Constants.SHARED_PREF_ALARM_NOTIFICATION_ENABLED, false)
            switchNotification.setOnCheckedChangeListener { _, isChecked ->
                Log.d(LOG_ID, "Notification changed: " + isChecked.toString())
                try {
                    with (sharedPref.edit()) {
                        putBoolean(Constants.SHARED_PREF_ALARM_NOTIFICATION_ENABLED, isChecked)
                        apply()
                    }
                    update()
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Changing notification exception: " + exc.message.toString() )
                }
            }

            btnAlarmAdvancedSettings.setOnClickListener {
                Log.v(LOG_ID, "${btnAlarmAdvancedSettings.text} button clicked!")
                val intent = Intent(this, AlarmAdvancedActivity::class.java)
                startActivity(intent)
            }

            snooze_30.setOnClickListener {
                AlarmHandler.setSnooze(30)
                update()
            }

            snooze_60.setOnClickListener {
                AlarmHandler.setSnooze(60)
                update()
            }

            snooze_90.setOnClickListener {
                AlarmHandler.setSnooze(90)
                update()
            }

            snooze_120.setOnClickListener {
                AlarmHandler.setSnooze(120)
                update()
            }

            btnStopSnooze.setOnClickListener {
                AlarmHandler.setSnooze(0)
                update()
            }


            setAlarmButton(R.id.btnVeryLowAlarm, AlarmType.VERY_LOW)
            setAlarmButton(R.id.btnLowAlarm, AlarmType.LOW)
            setAlarmButton(R.id.btnHighAlarm, AlarmType.HIGH)
            setAlarmButton(R.id.btnVeryHighAlarm, AlarmType.VERY_HIGH)
            setAlarmButton(R.id.btnObsoleteAlarm,  AlarmType.OBSOLETE)

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
            update()
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

    private fun update() {
        snooze_30.isEnabled = AlarmNotificationWear.isEnabled()
        snooze_60.isEnabled = AlarmNotificationWear.isEnabled()
        snooze_90.isEnabled = AlarmNotificationWear.isEnabled()
        snooze_120.isEnabled = AlarmNotificationWear.isEnabled()
        btnStopSnooze.isEnabled = AlarmNotificationWear.isEnabled()
        btnStopSnooze.visibility = if(AlarmHandler.isSnoozeActive) View.VISIBLE else View.GONE
    }

    private fun setAlarmButton(btnRes: Int, alarmType: AlarmType) {
        val btnAlarm: Button = findViewById(btnRes)
        btnAlarm.setOnClickListener {
            Log.v(LOG_ID, "${btnAlarm.text} button clicked!")
            val intent = Intent(this, AlarmTypeActivity::class.java)
            intent.putExtra("title", btnAlarm.text)
            intent.putExtra("type", alarmType.ordinal)
            startActivity(intent)
        }
    }
}