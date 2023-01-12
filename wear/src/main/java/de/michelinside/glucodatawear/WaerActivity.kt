package de.michelinside.glucodatahandler

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import de.michelinside.glucodatahandler.databinding.ActivityWaerBinding

public interface NewIntentReceiver {
    public fun newIntent()
}

class WaerActivity : Activity(), NewIntentReceiver {

    private val LOG_ID = "GlucoDataWear.Main"
    private lateinit var binding: ActivityWaerBinding
    private lateinit var txtLastValue: TextView
    private lateinit var txtVersion: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityWaerBinding.inflate(layoutInflater)
        setContentView(binding.root)


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