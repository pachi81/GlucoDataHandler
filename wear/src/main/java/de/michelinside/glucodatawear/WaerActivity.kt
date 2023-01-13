package de.michelinside.glucodatahandler

import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.ReceiveDataInterface
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import de.michelinside.glucodatahandler.databinding.ActivityWaerBinding


class WaerActivity : Activity(), ReceiveDataInterface {

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
    }

    override fun OnReceiveData(context: Context) {
        Log.d(LOG_ID, "new intent received")
        update()
    }
}