package de.michelinside.glucodatahandler.settings

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.appcompat.widget.SwitchCompat
import de.michelinside.glucodatahandler.ActiveComplicationHandler
import de.michelinside.glucodatahandler.BatteryLevelComplicationUpdater
import de.michelinside.glucodatahandler.ChartComplicationUpdater
import de.michelinside.glucodatahandler.GlucoDataServiceWear
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.receiver.BatteryReceiver

class SettingsActivity : AppCompatActivity() {
    private val LOG_ID = "GDH.Main.Settings"

    private lateinit var sharedPref: SharedPreferences
    private lateinit var switchColoredAod: SwitchCompat
    private lateinit var switchLargeTrendArrow: SwitchCompat
    private lateinit var switchForground: SwitchCompat
    private lateinit var switchRelativeTime: SwitchCompat
    private lateinit var switchBatteryLevel: SwitchCompat
    private lateinit var showBatteryPercent: SwitchCompat
    private lateinit var btnComplicationTapAction: Button
    private lateinit var seekGraphDuration: AppCompatSeekBar
    private lateinit var txtGraphDurationLevel: TextView
    private lateinit var seekLogDuration: AppCompatSeekBar
    private lateinit var txtLogDurationLevel: TextView
    private lateinit var switchEnableDebugLog: SwitchCompat
    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            Log.v(LOG_ID, "onCreate called")
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_settings)

            sharedPref = this.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)

            btnComplicationTapAction = findViewById(R.id.btnComplicationTapAction)


            btnComplicationTapAction.setOnClickListener {
                try {
                    Log.v(LOG_ID, "${btnComplicationTapAction.text} button clicked!")
                    val intent = Intent(this, ComplicationsTapActionActivity::class.java)
                    startActivity(intent)
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Complication tap action exception: " + exc.message.toString())
                }
            }

            switchForground = findViewById(R.id.switchForground)
            switchForground.isChecked = sharedPref.getBoolean(Constants.SHARED_PREF_FOREGROUND_SERVICE, true)
            switchForground.setOnCheckedChangeListener { _, isChecked ->
                Log.d(LOG_ID, "Foreground service changed: " + isChecked.toString())
                try {
                    with (sharedPref.edit()) {
                        putBoolean(Constants.SHARED_PREF_FOREGROUND_SERVICE, isChecked)
                        apply()
                    }
                    val serviceIntent = Intent(this, GlucoDataServiceWear::class.java)
                    if (isChecked) {
                        serviceIntent.putExtra(Constants.SHARED_PREF_FOREGROUND_SERVICE, true)
                        this.startService(serviceIntent)
                    } else {
                        serviceIntent.putExtra(Constants.ACTION_STOP_FOREGROUND, true)
                        this.startService(serviceIntent)
                    }
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Changing foreground service exception: " + exc.message.toString() )
                }
            }

            switchRelativeTime = findViewById(R.id.switchRelativeTime)
            switchRelativeTime.isChecked = sharedPref.getBoolean(Constants.SHARED_PREF_RELATIVE_TIME, true)
            switchRelativeTime.setOnCheckedChangeListener { _, isChecked ->
                Log.d(LOG_ID, "Relative time changed: " + isChecked.toString())
                try {
                    with (sharedPref.edit()) {
                        putBoolean(Constants.SHARED_PREF_RELATIVE_TIME, isChecked)
                        apply()
                    }
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Changing relative time exception: " + exc.message.toString() )
                }
            }

            switchColoredAod = findViewById(R.id.switchColoredAod)
            switchColoredAod.isChecked = sharedPref.getBoolean(Constants.SHARED_PREF_WEAR_COLORED_AOD, false)
            switchColoredAod.setOnCheckedChangeListener { _, isChecked ->
                Log.d(LOG_ID, "Colored AOD changed: " + isChecked.toString())
                try {
                    with (sharedPref.edit()) {
                        putBoolean(Constants.SHARED_PREF_WEAR_COLORED_AOD, isChecked)
                        apply()
                    }
                    // trigger update of each complication on change
                    ActiveComplicationHandler.OnNotifyData(this, NotifySource.SETTINGS, null)
                    ChartComplicationUpdater.OnNotifyData(this, NotifySource.SETTINGS, null)
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Changing colored AOD exception: " + exc.message.toString() )
                }
            }

            switchLargeTrendArrow = findViewById(R.id.switchLargeTrendArrow)
            switchLargeTrendArrow.isChecked = sharedPref.getBoolean(Constants.SHARED_PREF_LARGE_ARROW_ICON, true)
            switchLargeTrendArrow.setOnCheckedChangeListener { _, isChecked ->
                Log.d(LOG_ID, "Large arrow icon changed: " + isChecked.toString())
                try {
                    with (sharedPref.edit()) {
                        putBoolean(Constants.SHARED_PREF_LARGE_ARROW_ICON, isChecked)
                        apply()
                    }
                    // trigger update of each complication on change
                    ActiveComplicationHandler.OnNotifyData(this, NotifySource.SETTINGS, null)
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Changing large arrow icon exception: " + exc.message.toString() )
                }
            }

            switchBatteryLevel = findViewById(R.id.switchBatteryLevel)
            switchBatteryLevel.isChecked = sharedPref.getBoolean(Constants.SHARED_PREF_BATTERY_RECEIVER_ENABLED, true)
            switchBatteryLevel.setOnCheckedChangeListener { _, isChecked ->
                Log.d(LOG_ID, "Battery level changed: " + isChecked.toString())
                try {
                    with (sharedPref.edit()) {
                        putBoolean(Constants.SHARED_PREF_BATTERY_RECEIVER_ENABLED, isChecked)
                        apply()
                    }
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Changing battery level exception: " + exc.message.toString() )
                }
            }

            showBatteryPercent = findViewById(R.id.showBatteryPercent)
            showBatteryPercent.isChecked = sharedPref.getBoolean(Constants.SHARED_PREF_SHOW_BATTERY_PERCENT, true)
            showBatteryPercent.setOnCheckedChangeListener { _, isChecked ->
                Log.d(LOG_ID, "Battery percent changed: " + isChecked.toString())
                try {
                    with (sharedPref.edit()) {
                        putBoolean(Constants.SHARED_PREF_SHOW_BATTERY_PERCENT, isChecked)
                        apply()
                    }
                    BatteryLevelComplicationUpdater.OnNotifyData(this, NotifySource.BATTERY_LEVEL, BatteryReceiver.batteryBundle)
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Changing battery level exception: " + exc.message.toString() )
                }
            }

            seekGraphDuration = findViewById(R.id.seekGraphDuration)
            txtGraphDurationLevel = findViewById(R.id.txtGraphDurationLevel)

            seekGraphDuration.progress = sharedPref.getInt(Constants.SHARED_PREF_GRAPH_BITMAP_DURATION, 2)
            txtGraphDurationLevel.text = getProgressHoursString(seekGraphDuration.progress)
            seekGraphDuration.setOnSeekBarChangeListener(SeekBarChangeListener(Constants.SHARED_PREF_GRAPH_BITMAP_DURATION, txtGraphDurationLevel))

            seekLogDuration = findViewById(R.id.seekLogDuration)
            txtLogDurationLevel = findViewById(R.id.txtLogDurationLevel)

            seekLogDuration.progress = sharedPref.getInt(Constants.SHARED_PREF_LOG_DURATION, 0)
            txtLogDurationLevel.text = getProgressHoursString(seekLogDuration.progress)
            seekLogDuration.setOnSeekBarChangeListener(SeekBarChangeListener(Constants.SHARED_PREF_LOG_DURATION, txtLogDurationLevel))

            switchEnableDebugLog = findViewById(R.id.switchEnableDebugLog)
            switchEnableDebugLog.isChecked = sharedPref.getBoolean(Constants.SHARED_PREF_LOG_DEBUG, false)
            switchEnableDebugLog.setOnCheckedChangeListener { _, isChecked ->
                Log.d(LOG_ID, "Debug logging changed: " + isChecked.toString())
                try {
                    with (sharedPref.edit()) {
                        putBoolean(Constants.SHARED_PREF_LOG_DEBUG, isChecked)
                        apply()
                    }
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Changing debug logging exception: " + exc.message.toString() )
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

    private fun getProgressHoursString(progress: Int): String {
        return "${progress}h"
    }

    inner class SeekBarChangeListener(val preference: String, val txtLevel: TextView) : SeekBar.OnSeekBarChangeListener {
        private val LOG_ID = "GDH.Main.Settings.SeekBar"
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            try {
                Log.v(LOG_ID, "onProgressChanged called for $preference with progress=$progress - fromUser=$fromUser")
                if(fromUser) {
                    txtLevel.text = getProgressHoursString(progress)
                    with(sharedPref.edit()) {
                        putInt(preference, progress)
                        apply()
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
                    with(sharedPref.edit()) {
                        putInt(preference, seekBar.progress)
                        apply()
                    }
                }
            } catch( exc: Exception ) {
                Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
            }
        }
    }

}