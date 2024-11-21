package de.michelinside.glucodatahandler.settings

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import de.michelinside.glucodatahandler.ActiveComplicationHandler
import de.michelinside.glucodatahandler.GlucoDataServiceWear
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.notifier.NotifySource

class SettingsActivity : AppCompatActivity() {
    private val LOG_ID = "GDH.Main.Settings"

    private lateinit var sharedPref: SharedPreferences
    private lateinit var switchColoredAod: SwitchCompat
    private lateinit var switchLargeTrendArrow: SwitchCompat
    private lateinit var switchForground: SwitchCompat
    private lateinit var switchRelativeTime: SwitchCompat
    private lateinit var switchBatteryLevel: SwitchCompat
    private lateinit var btnComplicationTapAction: Button
    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            Log.v(LOG_ID, "onCreate called")
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_settings)

            sharedPref = this.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)

            btnComplicationTapAction = findViewById(R.id.btnComplicationTapAction)


            btnComplicationTapAction.setOnClickListener {
                Log.v(LOG_ID, "${btnComplicationTapAction.text} button clicked!")
                val intent = Intent(this, ComplicationsTapActionActivity::class.java)
                startActivity(intent)
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
                Log.d(LOG_ID, "Batter level changed: " + isChecked.toString())
                try {
                    with (sharedPref.edit()) {
                        putBoolean(Constants.SHARED_PREF_BATTERY_RECEIVER_ENABLED, isChecked)
                        apply()
                    }
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Changing battery level exception: " + exc.message.toString() )
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
}