package de.michelinside.glucodatahandler

import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.ReceiveDataInterface
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import de.michelinside.glucodatahandler.databinding.ActivityWaerBinding
import java.util.*


class WaerActivity : Activity(), ReceiveDataInterface {

    private val LOG_ID = "GlucoDataWear.Main"
    private lateinit var binding: ActivityWaerBinding
    private lateinit var txtBgValue: TextView
    private lateinit var txtVersion: TextView
    private lateinit var txtValueInfo: TextView

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
        if(ReceiveData.time > 0) {
            txtBgValue = findViewById(R.id.txtBgValue)
            txtBgValue.text = ReceiveData.glucose.toString() + " " + ReceiveData.getRateSymbol()
            if (ReceiveData.alarm != 0)
                txtBgValue.setTextColor(Color.RED)
            else
                txtBgValue.setTextColor(Color.WHITE)
            txtValueInfo = findViewById(R.id.txtValueInfo)
            txtValueInfo.text =
                getString(de.michelinside.glucodatahandler.common.R.string.info_label_delta) + ": " + ReceiveData.delta + " " + ReceiveData.getUnit() +
                        "\n" + ReceiveData.dateformat.format(Date(ReceiveData.time))
        }
    }

    override fun OnReceiveData(context: Context) {
        Log.d(LOG_ID, "new intent received")
        update()
    }
}