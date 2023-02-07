package de.michelinside.glucodatahandler

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Switch
import android.widget.TextView
import de.michelinside.glucodatahandler.common.*


class MainActivity : AppCompatActivity(), ReceiveDataInterface {
    private lateinit var txtLastValue: TextView
    private lateinit var txtVersion: TextView
    private lateinit var txtWearInfo: TextView
    private lateinit var switchSendToAod: Switch
    private lateinit var sharedPref: SharedPreferences
    private val LOG_ID = "GlucoDataHandler.Main"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d(LOG_ID, "onCreate called")
        val intent = Intent()
        val packageName = packageName
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }

        sharedPref = this.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)

        if (!GlucoDataService.running) {
            val serviceIntent = Intent(this, GlucoDataServiceMobile::class.java)
            this.startService(serviceIntent)
        }

        txtVersion = findViewById(R.id.txtVersion)
        txtVersion.text = BuildConfig.VERSION_NAME

        switchSendToAod = findViewById(R.id.switchSendToAod)
        switchSendToAod.isChecked = sharedPref.getBoolean(Constants.SHARED_PREF_SEND_TO_GLUCODATA_AOD, false)
        switchSendToAod.setOnCheckedChangeListener { _, isChecked ->
            Log.d(LOG_ID, "Send to AOD changed: " + isChecked.toString())
            try {
                with (sharedPref.edit()) {
                    putBoolean(Constants.SHARED_PREF_SEND_TO_GLUCODATA_AOD, isChecked)
                    apply()
                }
            } catch (exc: Exception) {
                Log.e(LOG_ID, "Changing send to AOD exception: " + exc.message.toString() )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        ReceiveData.remNotifier(this)
        Log.d(LOG_ID, "onPause called")
    }

    override fun onResume() {
        super.onResume()
        Log.d(LOG_ID, "onResume called")
        update()
        ReceiveData.addNotifier(this)
    }

    private fun update() {
        txtLastValue = findViewById(R.id.txtLastValue)
        txtLastValue.text = ReceiveData.getAsString(this)
        txtWearInfo = findViewById(R.id.txtWearInfo)
        if (ReceiveData.capabilityInfo != null && ReceiveData.capabilityInfo!!.nodes.size > 0)
            txtWearInfo.text = String.format(resources.getText(R.string.activity_main_connected_label).toString(), ReceiveData.capabilityInfo!!.nodes.size)
        else
            txtWearInfo.text = resources.getText(R.string.activity_main_disconnected_label)
    }

    override fun OnReceiveData(context: Context, dataSource: ReceiveDataSource, extras: Bundle?) {
        Log.d(LOG_ID, "new intent received")
        update()
    }
}