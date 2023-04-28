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
            if(newNodes.isEmpty()) {
                Log.d(LOG_ID, "Clear battery levels for " + nodeBatteryLevel.keys.toString())
                nodeBatteryLevel.clear()
            } else if (curNodes.isNotEmpty() && nodeBatteryLevel.isNotEmpty()) {
                curNodes.removeAll(newNodes)  // remove not change ids from curNodes
                curNodes.forEach {
                    if (nodeBatteryLevel.containsKey(it)) {
                        Log.d(LOG_ID, "Remove battery level for id " + it)
                        nodeBatteryLevel.remove(it)// remove all battery levels from not connected nodes
                    }
                }
            }
            ReceiveData.notify(context, ReceiveDataSource.CAPILITY_INFO, ReceiveData.createExtras())
        }
    }

    fun checkConnectedNode(nodeId: String) {
        if (!connectedNodes.containsKey(nodeId)) {
            Log.i(LOG_ID, "Node with id " + nodeId + " not yet connected, check connection!")
            checkForConnectedNodes()
        }
    }

    private fun setNodeBatteryLevel(nodeId: String, level: Int) {
        if (level >= 0 && (!nodeBatteryLevel.containsKey(nodeId) || nodeBatteryLevel.getValue(nodeId) != level )) {
            Log.d(LOG_ID, "Setting new battery level for node " + nodeId + ": " + level + "%")
            nodeBatteryLevel[nodeId] = level
            val extra = Bundle()
            extra.putInt(BatteryReceiver.LEVEL, level)
            ReceiveData.notify(context, ReceiveDataSource.NODE_BATTERY_LEVEL, extra)
        }
    }

    private fun getPath(dataSource: ReceiveDataSource) =
        when(dataSource) {
            ReceiveDataSource.BATTERY_LEVEL -> Constants.BATTERY_INTENT_MESSAGE_PATH
            ReceiveDataSource.CAPILITY_INFO -> Constants.REQUEST_DATA_MESSAGE_PATH
            ReceiveDataSource.SETTINGS -> Constants.SETTINGS_INTENT_MESSAGE_PATH
            else -> Constants.GLUCODATA_INTENT_MESSAGE_PATH
        }

    fun sendMessage(dataSource: ReceiveDataSource, extras: Bundle?)
    {
        try {
            if( nodesConnected && dataSource != ReceiveDataSource.NODE_BATTERY_LEVEL ) {
                Log.d(LOG_ID, connectedNodes.size.toString() + " nodes found for sending message to")
                if (extras != null && dataSource != ReceiveDataSource.BATTERY_LEVEL && BatteryReceiver.batteryPercentage > 0) {
                    extras.putInt(BatteryReceiver.LEVEL, BatteryReceiver.batteryPercentage)
                }
                // Send a message to all nodes in parallel
                connectedNodes.forEach { node ->
                    Thread {
                        try {
                            if (dataSource == ReceiveDataSource.CAPILITY_INFO)
                                Thread.sleep(1000)  // wait a bit after the connection has changed
                            sendMessage(node.value, getPath(dataSource), Utils.bundleToBytes(extras), dataSource)
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

    private fun sendMessage(node: Node, path: String, data: ByteArray?, dataSource: ReceiveDataSource, retryCount: Long = 0L) {
        if (retryCount > 0) {
            Log.i(LOG_ID, "Sleep " + (retryCount).toString() + " seconds, before retry sending.")
            Thread.sleep(retryCount * 1000)
        }
        if (connectedNodes.containsKey(node.id)) {
            Wearable.getMessageClient(context).sendMessage(
                node.id,
                path,
                data
            ).apply {
                addOnSuccessListener {
                    Log.i(
                        LOG_ID,
                        dataSource.toString() + " data send to node " + node.toString()
                    )
                }
                addOnFailureListener {
                    Log.w(
                        LOG_ID,
                        "Failed " + (retryCount+1).toString() + ". time to send " + dataSource.toString() + " data to node " + node.toString()
                    )
                    if (retryCount < 2)
                        sendMessage(node, path, data, dataSource, retryCount+1)
                }
            }
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

                if (p0.path == Constants.SETTINGS_INTENT_MESSAGE_PATH || extras.containsKey(Constants.SETTINGS_BUNDLE)) {
                    Log.d(LOG_ID, "Settings receceived from " + p0.sourceNodeId + ": " + extras.toString())
                    val bundle = if (extras.containsKey(Constants.SETTINGS_BUNDLE)) extras.getBundle(Constants.SETTINGS_BUNDLE) else extras
                    ReceiveData.setSettings(context, bundle!!)
                    ReceiveData.notify(context, ReceiveDataSource.SETTINGS, bundle)
                }

                if(p0.path == Constants.REQUEST_DATA_MESSAGE_PATH) {
                    Log.d(LOG_ID, "Data request received from " + p0.sourceNodeId)
                    var bundle = ReceiveData.createExtras()
                    var source = ReceiveDataSource.BROADCAST
                    if( bundle == null && BatteryReceiver.batteryPercentage > 0) {
                        bundle = BatteryReceiver.batteryBundle
                        source = ReceiveDataSource.BATTERY_LEVEL
                    }
                    if (bundle != null) {
                        if (GlucoDataService.appSource == AppSource.PHONE_APP) {
                            Log.d(LOG_ID, "Adding settings for sending")
                            bundle.putBundle(Constants.SETTINGS_BUNDLE, ReceiveData.getSettingsBundle())
                        }
                        sendMessage(source, bundle)
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