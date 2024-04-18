package de.michelinside.glucodatahandler.common

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.*
import de.michelinside.glucodatahandler.common.notification.AlarmHandler
import de.michelinside.glucodatahandler.common.notification.AlarmNotificationBase
import de.michelinside.glucodatahandler.common.notification.AlarmType
import de.michelinside.glucodatahandler.common.notifier.*
import de.michelinside.glucodatahandler.common.receiver.BatteryReceiver
import de.michelinside.glucodatahandler.common.notifier.DataSource
import de.michelinside.glucodatahandler.common.tasks.DataSourceTask
import de.michelinside.glucodatahandler.common.utils.Utils
import kotlinx.coroutines.*
import java.text.DateFormat
import java.util.*
import kotlin.coroutines.cancellation.CancellationException


enum class Command {
    STOP_ALARM,
    SNOOZE_ALARM,
    TEST_ALARM
}

class WearPhoneConnection : MessageClient.OnMessageReceivedListener, CapabilityClient.OnCapabilityChangedListener, NotifierInterface {
    private val LOG_ID = "GDH.WearPhoneConnection"
    private lateinit var context: Context

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
        val nodesConnected: Boolean get() = connectedNodes.size>0
        fun getBatterLevels(addMissing: Boolean = true): List<Int> {
            val batterLevels = mutableListOf<Int>()
            connectedNodes.forEach { node ->
                (if (nodeBatteryLevel.containsKey(node.key)) {
                    batterLevels.add(nodeBatteryLevel.getValue(node.key))
                }
                else if (addMissing) {
                    batterLevels.add(-1)
                })
            }
            return batterLevels
        }

        fun getBatterLevelsAsString(): String {
            if (nodesConnected)
                return getBatterLevels().joinToString { if (it > 0) it.toString() + "%" else "?%"}
            return "-"
        }
    }

    fun open(context: Context, sendSettings: Boolean) {
        Log.d(LOG_ID, "open connection")
        this.context = context
        Wearable.getMessageClient(context).addListener(this)
        Log.d(LOG_ID, "MessageClient added")
        Wearable.getCapabilityClient(context).addListener(this,
            Uri.parse("wear://"),
            CapabilityClient.FILTER_REACHABLE)
        Log.d(LOG_ID, "CapabilityClient added")
        val filter = mutableSetOf(
            NotifySource.BROADCAST,
            NotifySource.IOB_COB_CHANGE,
            NotifySource.IOB_COB_TIME,
            NotifySource.BATTERY_LEVEL)   // to trigger re-start for the case of stopped by the system
        if (sendSettings) {
            filter.add(NotifySource.SETTINGS)   // only send setting changes from phone to wear!
            filter.add(NotifySource.SOURCE_SETTINGS)
            filter.add(NotifySource.ALARM_SETTINGS)
        }
        InternalNotifier.addNotifier(this.context, this, filter)
        checkForConnectedNodes()
    }

    fun close() {
        InternalNotifier.remNotifier(context, this)
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
                            capabilityName,
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
            sendDataRequest()
        }
    }

    private fun sendDataRequest(filterReceiverId: String? = null) {
        val extras = ReceiveData.createExtras()
        InternalNotifier.notify(context, NotifySource.CAPILITY_INFO, ReceiveData.createExtras())
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
                if (extras != null && dataSource != NotifySource.BATTERY_LEVEL && BatteryReceiver.batteryPercentage > 0) {
                    extras.putInt(BatteryReceiver.LEVEL, BatteryReceiver.batteryPercentage)
                }
                if (extras != null && dataSource == NotifySource.CAPILITY_INFO && GlucoDataService.appSource == AppSource.PHONE_APP) {
                    Log.d(LOG_ID, "Adding settings for sending")
                    extras.putBundle(Constants.SETTINGS_BUNDLE, ReceiveData.getSettingsBundle())
                    extras.putBundle(Constants.SOURCE_SETTINGS_BUNDLE, DataSourceTask.getSettingsBundle(context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)))
                    extras.putBundle(Constants.ALARM_SETTINGS_BUNDLE, AlarmHandler.getSettings())
                }
                // Send a message to all nodes in parallel
                connectedNodes.forEach { node ->
                    Thread {
                        try {
                            if ((ignoreReceiverId == null && filterReceiverId == null) || ignoreReceiverId != node.value.id || filterReceiverId == node.value.id) {
                                if (dataSource == NotifySource.CAPILITY_INFO)
                                    Thread.sleep(1000)  // wait a bit after the connection has changed
                                sendMessage(node.value, getPath(dataSource), Utils.bundleToBytes(extras), dataSource)
                            }
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

    private fun sendMessage(node: Node, path: String, data: ByteArray?, dataSource: NotifySource, retryCount: Long = 0L) {
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

    fun sendCommand(command: Command, extras: Bundle?) {
        // Send command to all nodes in parallel
        Log.d(LOG_ID, "sendCommand called for $command with extras: ${Utils.dumpBundle(extras)}")
        val commandBundle = Bundle()
        commandBundle.putString(Constants.COMMAND_EXTRA, command.toString())
        if(extras != null) {
            commandBundle.putBundle(Constants.COMMAND_BUNDLE, extras)
            if (GlucoDataService.appSource == AppSource.PHONE_APP) {
                Log.d(LOG_ID, "Adding settings for sending command")
                commandBundle.putBundle(Constants.SETTINGS_BUNDLE, ReceiveData.getSettingsBundle())
                commandBundle.putBundle(Constants.SOURCE_SETTINGS_BUNDLE, DataSourceTask.getSettingsBundle(context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)))
                commandBundle.putBundle(Constants.ALARM_SETTINGS_BUNDLE, AlarmHandler.getSettings())
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
            val extras = Utils.bytesToBundle(p0.data)
            //Log.v(LOG_ID, "Received extras for path ${p0.path}: ${Utils.dumpBundle(extras)}")
            if(extras!= null) {
                if (extras.containsKey(Constants.SETTINGS_BUNDLE)) {
                    val bundle = extras.getBundle(Constants.SETTINGS_BUNDLE)
                    Log.d(LOG_ID, "Glucose settings receceived from " + p0.sourceNodeId + ": " + bundle.toString())
                    ReceiveData.setSettings(context, bundle!!)
                    InternalNotifier.notify(context, NotifySource.SETTINGS, bundle)
                    extras.remove(Constants.SETTINGS_BUNDLE)
                }

                if (extras.containsKey(Constants.SOURCE_SETTINGS_BUNDLE)) {
                    val bundle = extras.getBundle(Constants.SOURCE_SETTINGS_BUNDLE)
                    if (bundle != null) {
                        Log.d(LOG_ID, "Glucose source settings receceived from " + p0.sourceNodeId + ": " + bundle.toString())
                        DataSourceTask.updateSettings(context, bundle)
                    }
                    extras.remove(Constants.SOURCE_SETTINGS_BUNDLE)
                }

                if (extras.containsKey(Constants.ALARM_SETTINGS_BUNDLE)) {
                    val bundle = extras.getBundle(Constants.ALARM_SETTINGS_BUNDLE)
                    if (bundle != null) {
                        Log.d(LOG_ID, "Glucose alarm settings receceived from " + p0.sourceNodeId + ": " + bundle.toString())
                        AlarmHandler.setSettings(context, bundle)
                    }
                    extras.remove(Constants.ALARM_SETTINGS_BUNDLE)
                }
                if (extras.containsKey(BatteryReceiver.LEVEL)) {
                    val level = extras.getInt(BatteryReceiver.LEVEL, -1)
                    Log.d(LOG_ID, "Battery level received for node " + p0.sourceNodeId + ": " + level + "%")
                    setNodeBatteryLevel(p0.sourceNodeId, level)
                    extras.remove(BatteryReceiver.LEVEL)
                }

                if(p0.path == Constants.COMMAND_PATH) {
                    handleCommand(extras)
                    return
                }

                var forceSend = false
                if(p0.path == Constants.GLUCODATA_INTENT_MESSAGE_PATH || extras.containsKey(ReceiveData.SERIAL)) {
                    Log.d(LOG_ID, "Glucodata values receceived from " + p0.sourceNodeId + ": " + extras.toString())
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
                        Log.d(LOG_ID, "Glucose source settings receceived from " + p0.sourceNodeId + ": " + extras.toString())
                        DataSourceTask.updateSettings(context, extras)
                    }
                }

                if (p0.path == Constants.ALARM_SETTINGS_INTENT_MESSAGE_PATH) {
                    if (!extras.isEmpty) {
                        Log.d(LOG_ID, "Glucose alarm settings receceived from " + p0.sourceNodeId + ": " + extras.toString())
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
                    var bundle = ReceiveData.createExtras()
                    var source = NotifySource.BROADCAST
                    if( bundle == null && BatteryReceiver.batteryPercentage > 0) {
                        bundle = BatteryReceiver.batteryBundle
                        source = NotifySource.BATTERY_LEVEL
                    }
                    if (bundle != null) {
                        if (GlucoDataService.appSource == AppSource.PHONE_APP) {
                            Log.d(LOG_ID, "Adding settings for sending")
                            bundle.putBundle(Constants.SETTINGS_BUNDLE, ReceiveData.getSettingsBundle())
                            bundle.putBundle(Constants.SOURCE_SETTINGS_BUNDLE, DataSourceTask.getSettingsBundle(context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)))
                            bundle.putBundle(Constants.ALARM_SETTINGS_BUNDLE, AlarmHandler.getSettings())
                        }
                        sendMessage(source, bundle)
                    }
                }
            }
            if(p0.path == Constants.REQUEST_LOGCAT_MESSAGE_PATH) {
                sendLogcat(p0.sourceNodeId)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onMessageReceived exception: " + exc.message.toString() )
        }
    }

    private fun handleCommand(extras: Bundle) {
        try {
            Log.d(LOG_ID, "Command received: ${Utils.dumpBundle(extras)}")
            val command = Command.valueOf(extras.getString(Constants.COMMAND_EXTRA, ""))
            val bundle = extras.getBundle(Constants.COMMAND_BUNDLE)
            when(command) {
                Command.STOP_ALARM -> AlarmNotificationBase.instance!!.stopCurrentNotification(context, fromClient = true)
                Command.SNOOZE_ALARM -> AlarmHandler.setSnoozeTime(bundle!!.getLong(AlarmHandler.SNOOZE_TIME, 0L), fromClient = true)
                Command.TEST_ALARM -> AlarmNotificationBase.instance!!.executeTest(AlarmType.fromIndex(bundle!!.getInt(Constants.ALARM_NOTIFICATION_EXTRA_ALARM_TYPE, ReceiveData.getAlarmType().ordinal)), context)
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
            setConnectedNodes(capabilityInfo.nodes)
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

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            if (extras != null || !ignoreSourceWithoutExtras(dataSource)) {
                Log.d(LOG_ID, "OnNotifyData for source " + dataSource.toString() + " and extras " + extras.toString())
                sendMessage(dataSource, extras)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: " + exc.toString())
        }
    }
}
