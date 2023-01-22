package de.michelinside.glucodatahandler

import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.ReceiveDataInterface
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.annotation.RequiresApi
import de.michelinside.glucodatahandler.common.ReceiveDataSource
import de.michelinside.glucodatahandler.databinding.ActivityWaerBinding
import java.util.*
import kotlin.coroutines.cancellation.CancellationException


class WaerActivity : Activity(), ReceiveDataInterface {

    private val LOG_ID = "GlucoDataHandler.Main"
    private lateinit var binding: ActivityWaerBinding
    private lateinit var txtBgValue: TextView
    private lateinit var txtVersion: TextView
    private lateinit var txtValueInfo: TextView
    private lateinit var txtConnInfo: TextView

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityWaerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        var serviceIntent = Intent(this, GlucoDataServiceWear::class.java)
        this.startService(serviceIntent)

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
            txtBgValue.text = ReceiveData.getClucoseAsString() + " " + ReceiveData.getRateSymbol()
            txtBgValue.setTextColor(ReceiveData.getClucoseColor())
            txtValueInfo = findViewById(R.id.txtValueInfo)
            txtValueInfo.text =
                "Delta: " + ReceiveData.delta + " " + ReceiveData.getUnit() +
                        "\n" + ReceiveData.dateformat.format(Date(ReceiveData.time))
            txtConnInfo = findViewById(R.id.txtConnInfo)
            if (ReceiveData.capabilityInfo != null && ReceiveData.capabilityInfo!!.nodes.size > 0)
                txtConnInfo.text = resources.getText(R.string.activity_connected_label)
            else
                txtConnInfo.text = resources.getText(R.string.activity_disconnected_label)
        }
    }

    override fun OnReceiveData(context: Context, dataSource: ReceiveDataSource, extras: Bundle?) {
        Log.d(LOG_ID, "new intent received from: " + dataSource.toString())
        update()
    }
}