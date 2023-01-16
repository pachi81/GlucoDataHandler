package de.michelinside.glucodatahandler

import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.ReceiveDataInterface
import de.michelinside.glucodatahandler.common.GlucoDataService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.TextView
import com.google.android.gms.wearable.*


class MainActivity : AppCompatActivity(), ReceiveDataInterface, MessageClient.OnMessageReceivedListener, CapabilityClient.OnCapabilityChangedListener {
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

        var serviceIntent = Intent(this, GlucoDataService::class.java)
        this.startService(serviceIntent)

        txtVersion = findViewById(R.id.txtVersion)
        txtVersion.text = BuildConfig.VERSION_NAME
    }

    override fun onPause() {
        super.onPause()
        ReceiveData.remNotifier(this)
        Wearable.getMessageClient(this).removeListener(this)
        Wearable.getCapabilityClient(this).removeListener(this)
        Log.d(LOG_ID, "onPause called")
    }

    override fun onResume() {
        super.onResume()
        Log.d(LOG_ID, "onResume called")
        update()
        ReceiveData.addNotifier(this)
        Wearable.getMessageClient(this).addListener(this)
        Wearable.getCapabilityClient(this)
            .addListener(this, Uri.parse("wear://"), CapabilityClient.FILTER_REACHABLE)
    }

    private fun update() {
        txtLastValue = findViewById(R.id.txtLastValue)
        txtLastValue.text = ReceiveData.getAsString(this)
    }

    override fun OnReceiveData(context: Context) {
        Log.d(LOG_ID, "new intent received")
        update()
    }

    override fun onMessageReceived(p0: MessageEvent) {
        txtVersion = findViewById(R.id.txtVersion)
        txtVersion.text = "event!"
        Log.d(LOG_ID, "onMessageReceived called" + p0?.toString())
    }

    override fun onCapabilityChanged(p0: CapabilityInfo) {
        Log.d(LOG_ID, "onCapabilityChanged called" + p0?.toString())
    }
}