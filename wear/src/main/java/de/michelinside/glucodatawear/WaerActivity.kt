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
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.Wearable
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.databinding.ActivityWaerBinding
import java.util.*
import kotlin.coroutines.cancellation.CancellationException


class WaerActivity : Activity(), ReceiveDataInterface {

    private val LOG_ID = "GlucoDataHandler.Main"
    private lateinit var binding: ActivityWaerBinding
    private lateinit var txtBgValue: TextView
    private lateinit var txtVersion: TextView
    private lateinit var txtValueInfo: TextView

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityWaerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        var serviceIntent = Intent(this, GlucoDataService::class.java)
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
        /*(Runnable {
            SendMessage(this, "Hallo Welt!".toByteArray())
        }).start()*/
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
                "Delta: " + ReceiveData.delta + " " + ReceiveData.getUnit() +
                        "\n" + ReceiveData.dateformat.format(Date(ReceiveData.time))
        }
    }

    override fun OnReceiveData(context: Context) {
        Log.d(LOG_ID, "new intent received")
        update()
    }

    val GLUCODATA_INTENT_MESSAGE_PATH = "/glucodata_intent"
    val MOBILE_CAPABILITY = "glucodata_intent_mobile"
    fun SendMessage(context: Context, glucodataIntent: ByteArray)
    {
        Log.d(LOG_ID, "SendMessage called")
        try {
            val capabilityInfo: CapabilityInfo = Tasks.await(
                   Wearable.getCapabilityClient(context).getCapability(MOBILE_CAPABILITY, CapabilityClient.FILTER_REACHABLE))
            Log.d(LOG_ID, "nodes received")
            val nodes = capabilityInfo.nodes
            //val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes) //capabilityInfo.nodes
            Log.d(LOG_ID, nodes.size.toString() + " nodes found")
            if( nodes.size > 0 ) {
                // Send a message to all nodes in parallel
                nodes.map { node ->
                    val sendTask: Task<*> = Wearable.getMessageClient(context).sendMessage(
                        node.id,
                        GLUCODATA_INTENT_MESSAGE_PATH,
                        glucodataIntent
                    ).apply {
                        addOnSuccessListener {
                            Log.d(
                                LOG_ID,
                                "Data send to node " + node.toString()
                            )
                        }
                        addOnFailureListener {
                            Log.e(
                                LOG_ID,
                                "Failed to send data to node " + node.toString()
                            )
                        }
                    }
                }
            }
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (exception: Exception) {
            Log.e(LOG_ID, "Sending message failed: $exception")
        }
    }
}