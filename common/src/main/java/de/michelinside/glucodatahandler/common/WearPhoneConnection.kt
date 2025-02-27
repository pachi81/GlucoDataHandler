package de.michelinside.glucodatahandler.common

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.*
import de.michelinside.glucodatahandler.common.database.dbSync
import de.michelinside.glucodatahandler.common.notification.AlarmHandler
import de.michelinside.glucodatahandler.common.notification.AlarmNotificationBase
import de.michelinside.glucodatahandler.common.notification.AlarmType
import de.michelinside.glucodatahandler.common.notifier.*
import de.michelinside.glucodatahandler.common.receiver.BatteryReceiver
import de.michelinside.glucodatahandler.common.notifier.DataSource
import de.michelinside.glucodatahandler.common.tasks.DataSourceTask
import de.michelinside.glucodatahandler.common.utils.Utils
import kotlinx.coroutines.*
import java.math.RoundingMode
import java.text.DateFormat
import java.util.*
import kotlin.coroutines.cancellation.CancellationException


enum class Command {
    STOP_ALARM,
    SNOOZE_ALARM,
    TEST_ALARM,
    AA_CONNECTION_STATE,
    DISABLE_INACTIVE_TIME,
    PAUSE_NODE,
    RESUME_NODE,
    FORCE_UPDATE,
    DB_SYNC
}

class WearPhoneConnection : MessageClient.OnMessageReceivedListener, CapabilityClient.OnCapabilityChangedListener, NotifierInterface {
    private val LOG_ID = "GDH.WearPhoneConnection"
    private lateinit var context: Context
    private var lastSendValuesTime = 0L

    private val capabilityName: String get() {
        if(GlucoDataService.appSource == AppSource.PHONE_APP)  // phone sends to wear
        {
            return Constants.CAPABILITY_WEAR
        }
        return Constants.CAPABILITY_PHONE
    }

    private val dataSource: DataSource
        get() {
        if(GlucoDataService.appSource == AppSource.PHONE_APP)  // received from wear
        {
            return DataSource.WEAR
        }
        return DataSource.PHONE
    }

    companion object {
        private var connectedNodes = Collections.synchronizedMap(mutableMapOf<String,Node>())
        private val nodeBatteryLevel = Collections.synchronizedMap(mutableMapOf<String,Int>())
        private val noDataReceived = Collections.synchronizedSet(mutableSetOf<String>())
        private val noDataSend = Collections.synchronizedSet(mutableSetOf<String>())
        private val nodesPaused = Collections.synchronizedSet(mutableSetOf<String>())
        val nodesConnected: Boolean get() = connectedNodes.size>0
        private var connectRetries = 0
        private val filter = mutableSetOf(
            NotifySource.BROADCAST,
            NotifySource.IOB_COB_CHANGE,
            NotifySource.IOB_COB_TIME,
            NotifySource.BATTERY_LEVEL) // to trigger re-start for the case of stopped by the system

        private val notConnectedNodes: Set<String> get() = noDataReceived + noDataSend
        val connectionError: Boolean get() = notConnectedNodes.isNotEmpty() && connectRetries > 3

        fun getBatterLevels(addMissing: Boolean = true): List<Int> {
            val batterLevels = mutableListOf<Int>()
            connectedNodes.forEach { node ->
                (if (nodeBatteryLevel.containsKey(node.key)) {
                    batterLevels.add(nodeBatteryLevel.getValue(node.key))
                }
                else if (addMissing) {
                    batterLevels.add(-1)
                } else {})
            }
            return batterLevels
        }

        private fun getDisplayName(node: Node): String {
            val result = node.displayName.replace("_", " ")
            return if(result.indexOf("(") > 0) {
                result.take(result.indexOf("(")).trim()
            } else {
                result.trim()
            }
        }

        fun getNodeConnectionStates(context: Context, addMissing: Boolean = true): Map<String, String> {
            val connectionStates = mutableMapOf<String, String>()
            connectedNodes.forEach { node ->
                (if (nodeBatteryLevel.containsKey(node.key)) {
                    val level = nodeBatteryLevel.getValue(node.key)
                    connectionStates[getDisplayName(node.value)] = if (level > 0) "${level}%" else context.getString(R.string.state_connected)
                }
                else if (addMissing) {
                    connectionStates[getDisplayName(node.value)] = context.getString(R.string.state_await_data)
                } else {})
            }
            return connectionStates

        }

        fun getNodeBatteryLevel(nodeId: String, addMissing: Boolean = true): Map<String, Int> {
            val nodeBatterLevels = mutableMapOf<String, Int>()
            val node = connectedNodes[nodeId]
            if (node != null) {
                if (nodeBatteryLevel.containsKey(nodeId)) {
                    nodeBatterLevels[getDisplayName(node)] = nodeBatteryLevel.getValue(nodeId)
                }
                else if (addMissing) {
                    nodeBatterLevels[getDisplayName(node)] = -1
                }
            }
            return nodeBatterLevels
        }

    }

    private fun openConnection() {
        Log.d(LOG_ID, "open connection")
        connectedNodes.clear()
        nodeBatteryLevel.clear()
        noDataReceived.clear()
        noDataSend.clear()
        Wearable.getMessageClient(context).addListener(this)
        Log.d(LOG_ID, "MessageClient added")
        Wearable.getCapabilityClient(context).addListener(this,
            Uri.parse("wear://"),
            CapabilityClient.FILTER_REACHABLE)
        Log.d(LOG_ID, "CapabilityClient added")
        checkForConnectedNodes()
    }

    private fun closeConnection() {
        Log.d(LOG_ID, "close connection")
        Wearable.getMessageClient(context).removeListener(this)
        Wearable.getCapabilityClient(context).removeListener(this)
        connectedNodes.clear()
        nodeBatteryLevel.clear()
        noDataReceived.clear()
        noDataSend.clear()
        nodesPaused.clear()
        Log.d(LOG_ID, "connection closed")
    }

    fun open(context: Context, sendSettings: Boolean) {
        Log.v(LOG_ID, "open called")
        this.context = context
        openConnection()
        if (sendSettings) {
            filter.add(NotifySource.SETTINGS)   // only send setting changes from phone to wear!
            filter.add(NotifySource.SOURCE_SETTINGS)
            filter.add(NotifySource.ALARM_SETTINGS)
        }
        InternalNotifier.addNotifier(this.context, this, filter)
    }

    fun close() {
        Log.v(LOG_ID, "close called")
        InternalNotifier.remNotifier(context, this)
        closeConnection()
    }

    fun resetConnection() {
        Log.w(LOG_ID, "reset connection called")
        closeConnection()
        openConnection()
    }

    private fun addTimer() {
        if(!filter.contains(NotifySource.TIME_VALUE)) {
            Log.i(LOG_ID, "add timer")
            filter.add(NotifySource.TIME_VALUE)
            InternalNotifier.addNotifier(context, this, filter)
        }
    }

    private fun removeTimer() {
        if(filter.contains(NotifySource.TIME_VALUE)) {
            Log.i(LOG_ID, "remove timer")
            filter.remove(NotifySource.TIME_VALUE)
            InternalNotifier.addNotifier(context, this, filter)
        }
    }

    private suspend fun getConnectedNodes(forceSendDataRequest: Boolean = false) = coroutineScope {
        Log.d(LOG_ID, "getting connected nodes")
        supervisorScope {
            val nodes = async(Dispatchers.IO) {
                Tasks.await(
                    Wearable.getCapabilityClient(context)
                        .getCapability(
                            capabilityName,
                            CapabilityClient.FILTER_REACHABLE
                        )
                ).nodes
            }
            withContext(Dispatchers.Main) {
                try {
                    setConnectedNodes(nodes.await(), false, forceSendDataRequest)
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "getConnectedNodes exception: " + exc.toString())
                }
           }
        }
    }

    fun checkForConnectedNodes(forceSendDataRequest: Boolean = false) {
        Log.d(LOG_ID, "check for connected nodes")
        GlobalScope.launch {
            try {
                getConnectedNodes(forceSendDataRequest)
            } catch (exc: Exception) {
                Log.e(LOG_ID, "launch getConnectedNodes exception: " + exc.toString())
            }
        }
    }

    fun checkForNodesWithoutData() {
        try {
            Log.d(LOG_ID, "check for connected nodes without data")
            connectedNodes.forEach { node ->
                if (!nodeBatteryLevel.containsKey(node.key)) {
                    sendDataRequest(node.value.id)
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "checkForNodesWithoutData exception: " + exc.toString())
        }
    }

    private fun setConnectedNodes(nodes: MutableSet<Node>, resetErrors: Boolean = false, forceSendDataRequest: Boolean = false) {
        val curNodes = connectedNodes.keys.toSortedSet()
        val newNodes = nodes.map { it.id }.toSortedSet()
        Log.d(LOG_ID, "Check node change, current: " + curNodes.toString() + " - new: " + newNodes.toString())
        if (curNodes.size != newNodes.size || curNodes != newNodes ) {
            if(newNodes.isEmpty()) {
                curNodes.forEach { id -> remNode(id) }
            } else {
                // add new nodes
                nodes.forEach { node -> if (!connectedNodes.contains(node.id)) addNewNode(node, resetErrors) }
                if (curNodes.isNotEmpty()) {
                    // remove old nodes
                    curNodes.removeAll(newNodes)  // remove not change ids from curNodes
                    curNodes.forEach {
                        remNode(it)
                    }
                }
            }
            InternalNotifier.notify(context, NotifySource.CAPILITY_INFO, null)
        } else if(forceSendDataRequest)
            sendDataRequest()
    }

    private fun addNewNode(node: Node, resetErrors: Boolean = false) {
        Log.i(LOG_ID, "New node connected: " + node.toString() + " - reset errors: " + resetErrors)
        if(resetErrors)
            connectRetries = 0
        connectedNodes[node.id] = node
        noDataReceived.add(node.id)
        noDataSend.add(node.id)
        sendDataRequest(node.id)
        addTimer()
    }

    private fun remNode(nodeId: String) {
        Log.i(LOG_ID, "Node disconnected: $nodeId")
        connectedNodes.remove(nodeId)
        nodeBatteryLevel.remove(nodeId)// remove all battery levels from not connected nodes
        noDataReceived.remove(nodeId)
        noDataSend.remove(nodeId)
        nodesPaused.remove(nodeId)
        if (connectedNodes.isEmpty())
            removeTimer()
    }

    private fun checkNodesConnected() {
        connectRetries++
        Log.d(LOG_ID, "${connectRetries}. check nodes connected: $notConnectedNodes")
        if (notConnectedNodes.isNotEmpty()) {
            if (connectRetries < 3) {
                Log.i(LOG_ID, "re-send data request to: $notConnectedNodes")
                notConnectedNodes.forEach { id -> sendDataRequest(id) }
            } else if (connectRetries == 3) {
                Log.e(LOG_ID, "Reset connection as there are still nodes not connected: $notConnectedNodes")
                resetConnection()
            } else if (connectRetries == 4) {
                // stop timer and display error
                removeTimer()
                InternalNotifier.notify(context, NotifySource.CAPILITY_INFO, null)
            }
        } else {
            connectRetries = 0
            removeTimer()
        }
    }

    private fun sendDataRequest(filterReceiverId: String? = null) {
        val extras = ReceiveData.createExtras()
        extras?.putBundle(Constants.ALARM_EXTRA_BUNDLE, AlarmHandler.getExtras())
        sendMessage(NotifySource.CAPILITY_INFO, extras, filterReceiverId = filterReceiverId)  // send data request for new node
    }

    private fun checkConnectedNode(nodeId: String) {
        if (!connectedNodes.containsKey(nodeId)) {
            Log.i(LOG_ID, "Node with id " + nodeId + " not yet connected, check connection!")
            checkForConnectedNodes()
        }
    }

    fun pickBestNodeId(): String? {
        // Find a nearby node or pick one arbitrarily.
        return connectedNodes.values.firstOrNull { it.isNearby }?.id ?: connectedNodes.values.firstOrNull()?.id
    }


    private fun setNodeBatteryLevel(nodeId: String, level: Int) {
        if (level >= 0 && (!nodeBatteryLevel.containsKey(nodeId) || nodeBatteryLevel.getValue(nodeId) != level )) {
            Log.d(LOG_ID, "Setting new battery level for node " + nodeId + ": " + level + "%")
            nodeBatteryLevel[nodeId] = level
            val extra = Bundle()
            extra.putInt(BatteryReceiver.LEVEL, level)
            InternalNotifier.notify(context, NotifySource.NODE_BATTERY_LEVEL, extra)
        }
    }

    private fun getPath(dataSource: NotifySource) =
        when(dataSource) {
            NotifySource.BATTERY_LEVEL -> Constants.BATTERY_INTENT_MESSAGE_PATH
            NotifySource.CAPILITY_INFO -> Constants.REQUEST_DATA_MESSAGE_PATH
            NotifySource.SETTINGS -> Constants.SETTINGS_INTENT_MESSAGE_PATH
            NotifySource.SOURCE_SETTINGS -> Constants.SOURCE_SETTINGS_INTENT_MESSAGE_PATH
            NotifySource.ALARM_SETTINGS -> Constants.ALARM_SETTINGS_INTENT_MESSAGE_PATH
            NotifySource.LOGCAT_REQUEST -> Constants.REQUEST_LOGCAT_MESSAGE_PATH
            else -> Constants.GLUCODATA_INTENT_MESSAGE_PATH
        }

    fun sendMessage(dataSource: NotifySource, extras: Bundle?, ignoreReceiverId: String? = null, filterReceiverId: String? = null)
    {
        try {
            Log.v(LOG_ID, "sendMessage called for $dataSource filter receiver $filterReceiverId ignoring receiver $ignoreReceiverId with extras $extras")
            if( nodesConnected && dataSource != NotifySource.NODE_BATTERY_LEVEL ) {
                Log.d(LOG_ID, connectedNodes.size.toString() + " nodes found for sending message for " + dataSource.toString())
                if (extras != null && dataSource != NotifySource.BATTERY_LEVEL && BatteryReceiver.batteryPercentage >= 0) {
                    extras.putInt(BatteryReceiver.LEVEL, BatteryReceiver.batteryPercentage)
                }
                if (extras != null && dataSource == NotifySource.CAPILITY_INFO && GlucoDataService.appSource == AppSource.PHONE_APP) {
                    Log.d(LOG_ID, "Adding settings for sending")
                    extras.putBundle(Constants.SETTINGS_BUNDLE, GlucoDataService.getSettings())
                    extras.putBundle(Constants.SOURCE_SETTINGS_BUNDLE, DataSourceTask.getSettingsBundle(context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)))
                    extras.putBundle(Constants.ALARM_SETTINGS_BUNDLE, AlarmHandler.getSettings())
                }
                // Send a message to all nodes in parallel
                val curNodes = connectedNodes.values
                curNodes.forEach { node ->
                    Thread {
                        try {
                            if ((ignoreReceiverId == null && filterReceiverId == null) || ignoreReceiverId != node.id || filterReceiverId == node.id) {
                                if (dataSource == NotifySource.CAPILITY_INFO)
                                    Thread.sleep(1000)  // wait a bit after the connection has changed
                                sendMessage(node, getPath(dataSource), Utils.bundleToBytes(extras), dataSource)
                            }
                        } catch (exc: Exception) {
                            Log.e(LOG_ID, "sendMessage to " + node.toString() + " exception: " + exc.toString())
                        }
                    }.start()
                }
                if(dataSource == NotifySource.BROADCAST)
                    lastSendValuesTime = ReceiveData.time
            }
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (exception: Exception) {
            Log.e(LOG_ID, "Sending message failed: $exception")
        }
    }

    private fun sendMessage(node: Node, path: String, data: ByteArray?, dataSource: NotifySource, retryCount: Long = 0L) {
        if (retryCount > 0) {
            Log.i(LOG_ID, "Sleep " + (retryCount).toString() + " seconds, before retry sending.")
            Thread.sleep(retryCount * 5000)
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
                    if(noDataSend.contains(node.id)) {
                        noDataSend.remove(node.id)
                        checkNodeConnect(node.id)
                    }
                }
                addOnFailureListener { error ->
                    if (retryCount < 2) {
                        Log.w(
                            LOG_ID,
                            "Failed " + (retryCount+1).toString() + ". time to send " + dataSource.toString() + " data to node " + node.toString() + ": $error"
                        )
                        Thread {
                            try {
                                sendMessage(node, path, data, dataSource, retryCount+1)
                            } catch (exc: Exception) {
                                Log.e(LOG_ID, "sendMessage to " + node.toString() + " exception: " + exc.toString())
                            }
                        }.start()
                    } else {
                        Log.e(
                            LOG_ID,
                            "Failed " + (retryCount+1).toString() + ". time to send " + dataSource.toString() + " data to node " + node.toString() + ": $error"
                        )
                    }
                }
            }
        }
    }

    private fun checkNodeConnect(nodeId: String) {
        Log.d(LOG_ID, "check node connect $nodeId")
        if(!noDataReceived.contains(nodeId) && !noDataSend.contains(nodeId)) {
            Log.i(LOG_ID, "Node with id " + nodeId + " connected!")
            if(notConnectedNodes.isEmpty())
                removeTimer()
            dbSync.requestDbSync(context)
        } else if(noDataReceived.contains(nodeId)) {
            Log.i(LOG_ID, "Node with id " + nodeId + " still waiting for receiving data!")
        } else if(noDataSend.contains(nodeId)) {
            Log.i(LOG_ID, "Node with id " + nodeId + " still sending data!")
        }
    }

    fun sendCommand(command: Command, extras: Bundle?) {
        // Send command to all nodes in parallel
        Log.d(LOG_ID, "sendCommand called for $command with extras: ${Utils.dumpBundle(extras)}")
        val commandBundle = Bundle()
        commandBundle.putString(Constants.COMMAND_EXTRA, command.toString())
        if(extras != null) {
            commandBundle.putBundle(Constants.COMMAND_BUNDLE, extras)
            if (GlucoDataService.appSource == AppSource.PHONE_APP) {
                Log.d(LOG_ID, "Adding settings for sending command")
                commandBundle.putBundle(Constants.SETTINGS_BUNDLE, GlucoDataService.getSettings())
                commandBundle.putBundle(Constants.SOURCE_SETTINGS_BUNDLE, DataSourceTask.getSettingsBundle(context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)))
                commandBundle.putBundle(Constants.ALARM_SETTINGS_BUNDLE, AlarmHandler.getSettings())
            }
            if(BatteryReceiver.batteryPercentage >= 0) {
                commandBundle.putInt(BatteryReceiver.LEVEL, BatteryReceiver.batteryPercentage)
            }
        }
        connectedNodes.forEach { node ->
            Thread {
                try {
                    sendMessage(node.value, Constants.COMMAND_PATH, Utils.bundleToBytes(commandBundle), NotifySource.COMMAND)
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "sendCommand to " + node.value.toString() + " exception: " + exc.toString())
                }
            }.start()
        }
    }

    override fun onMessageReceived(p0: MessageEvent) {
        try {
            Log.i(LOG_ID, "onMessageReceived from " + p0.sourceNodeId + " with path " + p0.path)
            checkConnectedNode(p0.sourceNodeId)
            if(noDataReceived.contains(p0.sourceNodeId)) {
                noDataReceived.remove(p0.sourceNodeId)
                checkNodeConnect(p0.sourceNodeId)
            }
            val extras = Utils.bytesToBundle(p0.data)
            //Log.v(LOG_ID, "Received extras for path ${p0.path}: ${Utils.dumpBundle(extras)}")
            if(extras!= null) {
                if (extras.containsKey(Constants.SETTINGS_BUNDLE)) {
                    val bundle = extras.getBundle(Constants.SETTINGS_BUNDLE)
                    Log.d(LOG_ID, "Glucose settings received from " + p0.sourceNodeId + ": " + Utils.dumpBundle(bundle))
                    GlucoDataService.setSettings(context, bundle!!)
                    InternalNotifier.notify(context, NotifySource.SETTINGS, bundle)
                    extras.remove(Constants.SETTINGS_BUNDLE)
                }

                if (extras.containsKey(Constants.SOURCE_SETTINGS_BUNDLE)) {
                    val bundle = extras.getBundle(Constants.SOURCE_SETTINGS_BUNDLE)
                    if (bundle != null) {
                        Log.d(LOG_ID, "Glucose source settings received from " + p0.sourceNodeId + ": " + Utils.dumpBundle(bundle))
                        DataSourceTask.updateSettings(context, bundle)
                    }
                    extras.remove(Constants.SOURCE_SETTINGS_BUNDLE)
                }

                if (extras.containsKey(Constants.ALARM_SETTINGS_BUNDLE)) {
                    val bundle = extras.getBundle(Constants.ALARM_SETTINGS_BUNDLE)
                    if (bundle != null) {
                        Log.d(LOG_ID, "Glucose alarm settings received from " + p0.sourceNodeId + ": " + Utils.dumpBundle(bundle))
                        AlarmHandler.setSettings(context, bundle)
                    }
                    extras.remove(Constants.ALARM_SETTINGS_BUNDLE)
                }

                if (extras.containsKey(Constants.ALARM_EXTRA_BUNDLE)) {
                    val bundle = extras.getBundle(Constants.ALARM_EXTRA_BUNDLE)
                    if (bundle != null) {
                        Log.d(LOG_ID, "Glucose alarm extras received from " + p0.sourceNodeId + ": " + Utils.dumpBundle(bundle))
                        AlarmHandler.setExtras(context, bundle)
                    }
                    extras.remove(Constants.ALARM_EXTRA_BUNDLE)
                }

                if (extras.containsKey(BatteryReceiver.LEVEL)) {
                    val level = extras.getInt(BatteryReceiver.LEVEL, -1)
                    Log.d(LOG_ID, "Battery level received for node " + p0.sourceNodeId + ": " + level + "%")
                    setNodeBatteryLevel(p0.sourceNodeId, level)
                    extras.remove(BatteryReceiver.LEVEL)
                }

                if(p0.path == Constants.COMMAND_PATH) {
                    handleCommand(extras, p0.sourceNodeId)
                    return
                }

                var forceSend = false
                if(p0.path == Constants.GLUCODATA_INTENT_MESSAGE_PATH || extras.containsKey(ReceiveData.SERIAL)) {
                    Log.d(LOG_ID, "Glucodata values received from " + p0.sourceNodeId + ": " + extras.toString())
                    if (extras.containsKey(ReceiveData.TIME)) {
                        Log.d(LOG_ID, "Received data from: " +
                                DateFormat.getTimeInstance(DateFormat.DEFAULT).format((extras.getLong(ReceiveData.TIME))) +
                                " - current value time: " +
                                DateFormat.getTimeInstance(DateFormat.DEFAULT).format((ReceiveData.time)))
                        if (extras.getLong(ReceiveData.TIME) >= ReceiveData.time)
                            ReceiveData.handleIntent(context, dataSource, extras, true)
                        else
                            forceSend = true  //  received data is older than current one, send current one

                        if (GlucoDataService.appSource == AppSource.PHONE_APP && connectedNodes.size > 1) {
                            Log.d(LOG_ID, "Forward glucodata values to other connected nodes")
                            val newExtras = extras
                            if(newExtras.containsKey(BatteryReceiver.LEVEL))  // remove it, because the one from the phone must be used!
                                newExtras.remove(BatteryReceiver.LEVEL)
                            sendMessage(NotifySource.BROADCAST, extras, p0.sourceNodeId)
                        }
                    }
                }

                if (p0.path == Constants.SOURCE_SETTINGS_INTENT_MESSAGE_PATH) {
                    if (!extras.isEmpty) {
                        Log.d(LOG_ID, "Glucose source settings received from " + p0.sourceNodeId + ": " + extras.toString())
                        DataSourceTask.updateSettings(context, extras)
                    }
                }

                if (p0.path == Constants.ALARM_SETTINGS_INTENT_MESSAGE_PATH) {
                    if (!extras.isEmpty) {
                        Log.d(LOG_ID, "Glucose alarm settings received from " + p0.sourceNodeId + ": " + extras.toString())
                        AlarmHandler.setSettings(context, extras)
                    }
                }

                if (p0.path == Constants.SETTINGS_INTENT_MESSAGE_PATH) {
                    // check for other settings send...
                    extras.remove(Constants.SETTINGS_BUNDLE)
                    extras.remove(Constants.SOURCE_SETTINGS_BUNDLE)
                    extras.remove(Constants.ALARM_SETTINGS_BUNDLE)
                    extras.remove(BatteryReceiver.LEVEL)
                    if (!extras.isEmpty) {
                        val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
                        val keys = extras.keySet()
                        Log.d(LOG_ID, keys.size.toString() + " settings received")
                        with(sharedPref.edit()) {
                            keys.forEach {
                                try {
                                    val value = extras.getBoolean(it)
                                    Log.d(LOG_ID, "Setting value " + value + " for " + it)
                                        putBoolean(it, value)
                                } catch (exc: ClassCastException) {
                                    Log.w(LOG_ID,"Getting value for key " + it + " caused exception: " + exc.message)
                                }
                            }
                            apply()
                        }
                        InternalNotifier.notify(context, NotifySource.SETTINGS, extras)
                    }
                }

                if(p0.path == Constants.REQUEST_DATA_MESSAGE_PATH || forceSend) {
                    Log.d(LOG_ID, "Data request received from " + p0.sourceNodeId)
                    if (p0.path == Constants.REQUEST_DATA_MESSAGE_PATH) {
                        // new data request -> new connection on other side -> reset connection
                        noDataSend.add(p0.sourceNodeId)  // add to trigger db sync after connection established
                    }
                    var bundle = ReceiveData.createExtras()
                    var source = NotifySource.BROADCAST
                    if( bundle == null && BatteryReceiver.batteryPercentage >= 0) {
                        bundle = BatteryReceiver.batteryBundle
                        source = NotifySource.BATTERY_LEVEL
                    }
                    if (bundle != null) {
                        if (GlucoDataService.appSource == AppSource.PHONE_APP) {
                            Log.d(LOG_ID, "Adding settings for sending")
                            bundle.putBundle(Constants.SETTINGS_BUNDLE, GlucoDataService.getSettings())
                            bundle.putBundle(Constants.SOURCE_SETTINGS_BUNDLE, DataSourceTask.getSettingsBundle(context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)))
                            bundle.putBundle(Constants.ALARM_SETTINGS_BUNDLE, AlarmHandler.getSettings())
                        }
                        bundle.putBundle(Constants.ALARM_EXTRA_BUNDLE, AlarmHandler.getExtras())
                        sendMessage(source, bundle)
                    }
                }
            }
            if(p0.path == Constants.REQUEST_LOGCAT_MESSAGE_PATH) {
                sendLogcat(p0.sourceNodeId)
            }
            if (p0.path != Constants.REQUEST_DATA_MESSAGE_PATH && noDataSend.contains(p0.sourceNodeId)) {
                sendDataRequest()
            }
            GlucoDataService.checkServices(context)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onMessageReceived exception: " + exc.message.toString() )
        }
    }

    private fun handleCommand(extras: Bundle, nodeId: String) {
        try {
            Log.d(LOG_ID, "Command received from node $nodeId: ${Utils.dumpBundle(extras)}")
            val command = Command.valueOf(extras.getString(Constants.COMMAND_EXTRA, ""))
            val bundle = extras.getBundle(Constants.COMMAND_BUNDLE)
            when(command) {
                Command.STOP_ALARM -> AlarmNotificationBase.instance!!.stopCurrentNotification(context, fromClient = true)
                Command.SNOOZE_ALARM -> AlarmHandler.setSnoozeTime(bundle!!.getLong(AlarmHandler.SNOOZE_TIME, 0L), fromClient = true)
                Command.TEST_ALARM -> AlarmNotificationBase.instance!!.executeTest(AlarmType.fromIndex(bundle!!.getInt(Constants.ALARM_TYPE_EXTRA, ReceiveData.getAlarmType().ordinal)), context, false)
                Command.AA_CONNECTION_STATE -> InternalNotifier.notify(context, NotifySource.CAR_CONNECTION, bundle)
                Command.DISABLE_INACTIVE_TIME -> AlarmHandler.disableInactiveTime(fromClient = true)
                Command.PAUSE_NODE -> nodesPaused.add(nodeId)
                Command.RESUME_NODE -> {
                    if(nodesPaused.contains(nodeId)) {
                        Log.d(LOG_ID, "Resume node $nodeId")
                        nodesPaused.remove(nodeId)
                        if (canSendMessage(context, NotifySource.BROADCAST))
                            sendMessage(NotifySource.BROADCAST, ReceiveData.createExtras())
                        else
                            sendCommand(Command.FORCE_UPDATE, ReceiveData.createExtras())
                    }
                }
                Command.FORCE_UPDATE -> {
                    if(ReceiveData.hasNewValue(bundle)) {
                        ReceiveData.handleIntent(context, dataSource, bundle, true)
                    } else {
                        Log.d(LOG_ID, "Force update received from node $nodeId")
                        InternalNotifier.notify(context, NotifySource.MESSAGECLIENT, bundle)
                    }
                }
                Command.DB_SYNC -> dbSync.sendData(context, nodeId)
            }

        } catch (exc: Exception) {
            Log.e(LOG_ID, "handleCommand exception: " + exc.toString())
        }
    }

    private fun sendLogcat(phoneNodeId: String) {
        try {
                val channelClient = Wearable.getChannelClient(context)
                val channelTask =
                    channelClient.openChannel(phoneNodeId, Constants.LOGCAT_CHANNEL_PATH)
                channelTask.addOnSuccessListener { channel ->
                    Thread {
                        try {
                            val outputStream = Tasks.await(channelClient.getOutputStream(channel))
                            Log.d(LOG_ID, "sending Logcat")
                            Utils.saveLogs(outputStream)
                            channelClient.close(channel)
                            Log.d(LOG_ID, "Logcat sent")
                        } catch (exc: Exception) {
                            Log.e(LOG_ID, "sendLogcat exception: " + exc.toString())
                        }
                    }.start()
                }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "sendLogcat exception: " + exc.toString())
        }
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        try {
            Log.i(LOG_ID, "onCapabilityChanged called: " + capabilityInfo.toString())
            setConnectedNodes(capabilityInfo.nodes, true)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCapabilityChanged exception: " + exc.toString())
        }
    }

    private fun ignoreSourceWithoutExtras(dataSource: NotifySource): Boolean {
        return when(dataSource) {
            NotifySource.IOB_COB_CHANGE,
            NotifySource.IOB_COB_TIME,
            NotifySource.ALARM_SETTINGS -> true // do not send these sources without extras
            else -> false
        }
    }

    private fun canSendMessage(context: Context, dataSource: NotifySource): Boolean {
        if(!nodesConnected)
            return false
        if(dataSource == NotifySource.BROADCAST && !ReceiveData.forceAlarm && ReceiveData.getAlarmType() != AlarmType.VERY_LOW) {
            val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            if(!sharedPref.getBoolean(Constants.SHARED_PREF_PHONE_WEAR_SCREEN_OFF_UPDATE, true) && nodesPaused.size == connectedNodes.size) {
                Log.d(LOG_ID, "Ignore data because all nodes paused")
                return false
            }
            if(lastSendValuesTime == ReceiveData.time) {
                Log.d(LOG_ID, "Ignore data because of same time")
                return false
            }
            val interval = sharedPref.getInt(Constants.SHARED_PREF_SEND_TO_WATCH_INTERVAL, 1)
            val elapsedTime = Utils.getElapsedTimeMinute(lastSendValuesTime, RoundingMode.HALF_UP)
            Log.v(LOG_ID, "Check sending for interval $interval - elapsed: ${elapsedTime}")
            if (interval > 1 && elapsedTime < interval) {
                Log.d(LOG_ID, "Ignore data because of interval $interval - elapsed: ${elapsedTime} - last: ${Utils.getUiTimeStamp(lastSendValuesTime)}")
                return false
            }
        } else if (dataSource == NotifySource.BATTERY_LEVEL) {
            val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            if(!sharedPref.getBoolean(Constants.SHARED_PREF_PHONE_WEAR_SCREEN_OFF_UPDATE, true) && nodesPaused.size == connectedNodes.size) {
                Log.d(LOG_ID, "Ignore data because all nodes paused")
                return false
            }
        }
        return true
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            Log.d(LOG_ID, "OnNotifyData called for " + dataSource.toString())
            if (dataSource == NotifySource.TIME_VALUE) {
                checkNodesConnected()
            } else if ((extras != null || !ignoreSourceWithoutExtras(dataSource)) && canSendMessage(context, dataSource)) {
                Log.d(LOG_ID, "OnNotifyData for source " + dataSource.toString() + " and extras " + extras.toString())
                sendMessage(dataSource, extras)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: " + exc.toString())
        }
    }
}
