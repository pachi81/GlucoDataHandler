package de.michelinside.glucodatahandler.common

import android.content.Context
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.*
import kotlin.coroutines.cancellation.CancellationException


open class GlucoDataService : WearableListenerService(), MessageClient.OnMessageReceivedListener, CapabilityClient.OnCapabilityChangedListener, ReceiveDataInterface {
    private val LOG_ID = "GlucoDataHandler.GlucoDataService"

    override fun onCreate() {
        try {
            super.onCreate()
            Log.d(LOG_ID, "onCreate called")

            val sharedPref = this.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)

            ReceiveData.targetMin = sharedPref.getFloat(Constants.SHARED_PREF_TARGET_MIN, ReceiveData.targetMin)
            ReceiveData.targetMax = sharedPref.getFloat(Constants.SHARED_PREF_TARGET_MAX, ReceiveData.targetMax)
            Log.i(LOG_ID, "min/max set: " + ReceiveData.targetMin.toString() + "/" + ReceiveData.targetMax.toString())

            ReceiveData.addNotifier(this)
            Wearable.getMessageClient(this).addListener(this)
            Log.d(LOG_ID, "MessageClient added")
            Wearable.getCapabilityClient(this).addListener(this,
                Uri.parse("wear://"),
                CapabilityClient.FILTER_REACHABLE)
            Log.d(LOG_ID, "CapabilityClient added")
            Thread {
                ReceiveData.capabilityInfo = Tasks.await(
                    Wearable.getCapabilityClient(this)
                        .getCapability(Constants.CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                )
                Log.d(LOG_ID, ReceiveData.capabilityInfo!!.nodes.size.toString() + " nodes received")
            }.start()

            Log.d(LOG_ID, "Register Receiver")
            val intentFilter = IntentFilter()
            intentFilter.addAction("glucodata.Minute")
            registerReceiver(GlucoseDataReceiver(), intentFilter)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreate exception: " + exc.toString())
        }
    }

    override fun onDestroy() {
        try {
            Log.d(LOG_ID, "onDestroy called")
            ReceiveData.remNotifier(this)
            Wearable.getMessageClient(this).removeListener(this)
            Wearable.getCapabilityClient(this).removeListener(this)
            super.onDestroy()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onDestroy exception: " + exc.toString())
        }
    }

    override fun onMessageReceived(p0: MessageEvent) {
        try {
            Log.d(LOG_ID, "onMessageReceived called: " + p0.toString())
            ReceiveData.handleIntent(this, ReceiveDataSource.MESSAGECLIENT, Utils.bytesToBundle(p0.data))
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onMessageReceived exception: " + exc.message.toString() )
        }
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        try {
            Log.i(LOG_ID, "onCapabilityChanged called: " + capabilityInfo.toString())
            ReceiveData.capabilityInfo = capabilityInfo
            ReceiveData.notify(this, ReceiveDataSource.CAPILITY_INFO, ReceiveData.curExtraBundle)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCapabilityChanged exception: " + exc.toString())
        }
    }

    override fun OnReceiveData(context: Context, dataSource: ReceiveDataSource, extras: Bundle?) {
        try {
            Log.d(LOG_ID, "OnReceiveData for source " + dataSource.toString() + " and extras " + extras.toString())
            if (dataSource != ReceiveDataSource.MESSAGECLIENT && extras != null) {
                Thread {
                    SendMessage(context, Utils.bundleToBytes(extras))
                }.start()
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnReceiveData exception: " + exc.toString())
        }
    }

    fun SendMessage(context: Context, glucodataIntent: ByteArray?)
    {
        try {

            if (ReceiveData.capabilityInfo == null) {
                ReceiveData.capabilityInfo = Tasks.await(
                    Wearable.getCapabilityClient(context).getCapability(Constants.CAPABILITY, CapabilityClient.FILTER_REACHABLE))
                Log.d(LOG_ID, ReceiveData.capabilityInfo!!.nodes.size.toString() + " nodes received")
            }
            val nodes = ReceiveData.capabilityInfo!!.nodes
            //val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes)
            Log.d(LOG_ID, nodes.size.toString() + " nodes found")
            if( nodes.size > 0 ) {
                // Send a message to all nodes in parallel
                nodes.map { node ->
                    Wearable.getMessageClient(context).sendMessage(
                        node.id,
                        Constants.GLUCODATA_INTENT_MESSAGE_PATH,
                        glucodataIntent
                    ).apply {
                        addOnSuccessListener {
                            Log.i(
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