package de.michelinside.glucodatahandler

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.TextView

public interface NewIntentReceiver {
    public fun newIntent()
}

class MainActivity : AppCompatActivity(), NewIntentReceiver {
    private lateinit var txtLastValue: TextView
    private lateinit var txtVersion: TextView
    private val LOG_ID = "GlucoDataHandler.Main"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d(LOG_ID, "onCreate called")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent()
            val packageName = packageName
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }

        txtVersion = findViewById(R.id.txtVersion)
        txtVersion.text = BuildConfig.VERSION_NAME
    }

    override fun onPause() {
        super.onPause()
        GlucoseDataReceiver.notifier = null
        Log.d(LOG_ID, "onPause called")
    }

    override fun onResume() {
        super.onResume()
        Log.d(LOG_ID, "onResume called")
        update()
        GlucoseDataReceiver.notifier = this
    }

    private fun update() {
        txtLastValue = findViewById(R.id.txtLastValue)
        txtLastValue.text = ReceiveData.getAsString(this)
    }

    override fun newIntent() {
        Log.d(LOG_ID, "new intent received")
        update()
    }
}