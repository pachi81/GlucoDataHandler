package de.michelinside.glucodatahandler.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import de.michelinside.glucodatahandler.BuildConfig
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.Constants

class SourcesActivity : AppCompatActivity() {
    private val LOG_ID = "GDH.Main.Sources"

    private lateinit var sharedPref: SharedPreferences
    private lateinit var switchLibreSource: SwitchCompat
    private lateinit var switchDexcomShareSource: SwitchCompat
    private lateinit var switchNightscoutSource: SwitchCompat
    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            Log.v(LOG_ID, "onCreate called")
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_sources)

            sharedPref = this.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)

            switchLibreSource = findViewById(R.id.switchLibreSource)
            switchLibreSource.isChecked = sharedPref.getBoolean(Constants.SHARED_PREF_LIBRE_ENABLED, false)
            switchLibreSource.setOnCheckedChangeListener { _, isChecked ->
                Log.d(LOG_ID, "Libre view changed: " + isChecked.toString())
                try {
                    with (sharedPref.edit()) {
                        putBoolean(Constants.SHARED_PREF_LIBRE_ENABLED, isChecked)
                        apply()
                    }
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Changing Libre view exception: " + exc.message.toString() )
                }
            }

            switchDexcomShareSource = findViewById(R.id.switchDexcomShareSource)
            switchDexcomShareSource.isChecked = sharedPref.getBoolean(Constants.SHARED_PREF_DEXCOM_SHARE_ENABLED, false)
            switchDexcomShareSource.setOnCheckedChangeListener { _, isChecked ->
                Log.d(LOG_ID, "Libre view changed: " + isChecked.toString())
                try {
                    with (sharedPref.edit()) {
                        putBoolean(Constants.SHARED_PREF_DEXCOM_SHARE_ENABLED, isChecked)
                        apply()
                    }
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Changing Libre view exception: " + exc.message.toString() )
                }
            }

            switchNightscoutSource = findViewById(R.id.switchNightscoutSource)
            switchNightscoutSource.isChecked = sharedPref.getBoolean(Constants.SHARED_PREF_NIGHTSCOUT_ENABLED, false)
            switchNightscoutSource.setOnCheckedChangeListener { _, isChecked ->
                Log.d(LOG_ID, "Nightscout changed: " + isChecked.toString())
                try {
                    with (sharedPref.edit()) {
                        putBoolean(Constants.SHARED_PREF_NIGHTSCOUT_ENABLED, isChecked)
                        apply()
                    }
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Changing Nightscout exception: " + exc.message.toString() )
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
            update()
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

    private fun update() {
        try {
            val libreUser = sharedPref.getString(Constants.SHARED_PREF_LIBRE_USER, "")!!.trim()
            val librePwd = sharedPref.getString(Constants.SHARED_PREF_LIBRE_PASSWORD, "")!!.trim()
            if (!BuildConfig.DEBUG)
                switchLibreSource.isEnabled = libreUser.isNotEmpty() && librePwd.isNotEmpty()
            if(!switchLibreSource.isEnabled) {
                switchLibreSource.isChecked = false
            }

            val dexUser = sharedPref.getString(Constants.SHARED_PREF_DEXCOM_SHARE_USER, "")!!.trim()
            val dexPwd = sharedPref.getString(Constants.SHARED_PREF_DEXCOM_SHARE_PASSWORD, "")!!.trim()
            if (!BuildConfig.DEBUG)
                switchDexcomShareSource.isEnabled = dexUser.isNotEmpty() && dexPwd.isNotEmpty()
            if(!switchDexcomShareSource.isEnabled) {
                switchDexcomShareSource.isChecked = false
            }

            val url = sharedPref.getString(Constants.SHARED_PREF_NIGHTSCOUT_URL, "")!!.trim()
            if (!BuildConfig.DEBUG)
                switchNightscoutSource.isEnabled = url.isNotEmpty()
            if(!switchNightscoutSource.isEnabled) {
                switchNightscoutSource.isChecked = false
            }

        } catch( exc: Exception ) {
            Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
        }
    }
}