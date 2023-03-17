package de.michelinside.glucodatahandler.common

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.*
import kotlinx.coroutines.*
import java.util.*
import kotlin.coroutines.cancellation.CancellationException

class WearPhoneConnection : MessageClient.OnMessageReceivedListener, CapabilityClient.OnCapabilityChangedListener {
    private val LOG_ID = "GlucoDataHandler.WearPhoneConnection"
    private lateinit var context: Context

    companion object {
        private var connectedNodes = Collections.synchronizedMap(mutableMapOf<String,Node>())
        private val nodeBatteryLevel = Collections.synchronizedMap(mutableMapOf<String,Int>())
        val nodesConnected: Boolean get() = connectedNodes.size>0
        fun getBatterLevels(): List<Int> {
            val batterLevels = connectedNodes.map { node ->
                (if (nodeBatteryLevel.containsKey(node.key)) {
                    nodeBatteryLevel.getValue(node.key)
                }
                else {
                    -1
                })
            }.toList()
            return batterLevels
        }

        fun getBatterLevelsAsString(): String {
            if (nodesConnected)
                return getBatterLevels().joinToString { if (it > 0) it.toString() + "%" else "?%"}
            return "-"
        }
    }

    fun open(context: Context) {
        Log.d(LOG_ID, "open connection")
        this.context = context
        Wearable.getMessageClient(context).addListener(this)
        Log.d(LOG_ID, "MessageClient added")
        Wearable.getCapabilityClient(context).addListener(this,
            Uri.parse("wear://"),
            CapabilityClient.FILTER_REACHABLE)
        Log.d(LOG_ID, "CapabilityClient added")
        checkForConnectedNodes()
    }

    fun close() {
        Wearable.getMessageClient(context).removeListener(this)
        Wearable.getCapabilityClient(context).removeListener(this)
        Log.d(LOG_ID, "connection closed")
    }

    private suspend fun getConnectedNodes() = coroutineScope {
        Log.d(LOG_ID, "getting connected nodes")
        supervisorScope {
            val nodes = async(Dispatchers.IO) {
                Tasks.await(
                    Wearable.getCapabilityClient(context)
                        .getCapability(
                            Constants.CAPABILITY,
                            CapabilityClient.FILTER_REACHABLE
                        )
                ).nodes
            }
            withContext(Dispatchers.Main) {
                try {
                    setConnectedNodes(nodes.await())
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "getConnectedNodes exception: " + exc.toString())
                }
           }
        }
    }

    fun checkForConnectedNodes() {
        Log.d(LOG_ID, "check for connected nodes")
        GlobalScope.launch {
            try {
                getConnectedNodes()
            } catch (exc: Exception) {
                Log.e(LOG_ID, "launch getConnectedNodes exception: " + exc.toString())
            }
        }
    }

    private fun setConnectedNodes(nodes: MutableSet<Node>) {
        val curNodes = connectedNodes.keys.toSortedSet()
        val newNodes = nodes.map { it.id }.toSortedSet()
        Log.d(LOG_ID, "Check node change, current: " + curNodes.toString() + " - new: " + newNodes.toString())
        if (curNodes.size != newNodes.size || curNodes != newNodes ) {
            connectedNodes = nodes.associateBy({it.id}, {it})
            Log.i(LOG_ID, "Connected nodes changed: " + connectedNodes.toString())
            ReceiveData.notify(context, ReceiveDataSource.CAPILITY_INFO, ReceiveData.curExtraBundle)
        }
    }

    fun checkConnectedNode(nodeId: String) {
        if (!connectedNodes.containsKey(nodeId)) {
            Log.i(LOG_ID, "Node with id " + nodeId + " not yet connected, check connection!")
            checkForConnectedNodes()
        }
    }

    private fun setNodeBatteryLevel(nodeId: String, level: Int) {
        if (level >= 0)
            nodeBatteryLevel[nodeId] = level
    }

    private fun getPath(dataSource: ReceiveDataSource) =
        when(dataSource) {
            ReceiveDataSource.BATTERY_LEVEL -> Constants.BATTERY_INTENT_MESSAGE_PATH
            ReceiveDataSource.CAPILITY_INFO -> Constants.REQUEST_DATA_MESSAGE_PATH
            else -> Constants.GLUCODATA_INTENT_MESSAGE_PATH
        }

    fun sendMessage(context: Context, dataSource: ReceiveDataSource, extras: Bundle?)
    {
        try {
            if( nodesConnected ) {
                Log.d(LOG_ID, connectedNodes.size.toString() + " nodes found for sending message to")
                if (extras != null && dataSource != ReceiveDataSource.BATTERY_LEVEL && BatteryReceiver.batteryPercentage > 0) {
                    extras.putInt("level", BatteryReceiver.batteryPercentage)
                }
                // Send a message to all nodes in parallel
                connectedNodes.forEach { node ->
                    Thread {
                        try {
                            if (dataSource == ReceiveDataSource.CAPILITY_INFO)
                                Thread.sleep(1000)  // wait a bit after the connection has changed
                            var retryCount = 0
                            do {
                                var sendFailure = false
                                Wearable.getMessageClient(context).sendMessage(
                                    node.key,
                                    getPath(dataSource),
                                    Utils.bundleToBytes(extras)
                                ).apply {
                                    addOnSuccessListener {
                                        Log.i(
                                            LOG_ID,
                                            dataSource.toString() + " data send to node " + node.value.toString()
                                        )
                                    }
                                    addOnFailureListener {
                                        retryCount++
                                        Log.w(
                                            LOG_ID,
                                            "Failed " + retryCount.toString() + ". time to send " + dataSource.toString() + " data to node " + node.value.toString()
                                        )
                                        sendFailure = true
                                    }
                                }
                            } while(sendFailure && retryCount < 3)
                        } catch (exc: Exception) {
                            Log.e(LOG_ID, "sendMessage to " + node.value.toString() + " exception: " + exc.toString())
                        }
                    }.start()
                }
            }
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (exception: Exception) {
            Log.e(LOG_ID, "Sending message failed: $exception")
        }
    }

    override fun onMessageReceived(p0: MessageEvent) {
        try {
            Log.i(LOG_ID, "onMessageReceived from " + p0.sourceNodeId + " with path " + p0.path)
            checkConnectedNode(p0.sourceNodeId)
            val extras = Utils.bytesToBundle(p0.data)
            if(extras!= null) {
                if (extras.containsKey(BatteryReceiver.LEVEL)) {
                    val level = extras.getInt(BatteryReceiver.LEVEL, -1)
                    Log.d(LOG_ID, "Battery level received for node " + p0.sourceNodeId + ": " + level + "%")
                    setNodeBatteryLevel(p0.sourceNodeId, level)
                }

                if(p0.path == Constants.GLUCODATA_INTENT_MESSAGE_PATH || extras.containsKey(ReceiveData.SERIAL)) {
                    Log.d(LOG_ID, "Glucodata values receceived from " + p0.sourceNodeId + ": " + extras.toString())
                    ReceiveData.handleIntent(context, ReceiveDataSource.MESSAGECLIENT, extras)
                }

                if(p0.path == Constants.REQUEST_DATA_MESSAGE_PATH) {
                    Log.d(LOG_ID, "Data request received from " + p0.sourceNodeId)
                    if (ReceiveData.curExtraBundle != null)
                        sendMessage(context, ReceiveDataSource.BROADCAST, ReceiveData.curExtraBundle)
                    else if (BatteryReceiver.batteryPercentage > 0) {
                        sendMessage(context, ReceiveDataSource.BATTERY_LEVEL, BatteryReceiver.batteryBundle)
                    }

                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onMessageReceived exception: " + exc.message.toString() )
        }
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        try {
            Log.i(LOG_ID, "onCapabilityChanged called: " + capabilityInfo.toString())
            setConnectedNodes(capabilityInfo.nodes)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCapabilityChanged exception: " + exc.toString())
        }
    }
}