package de.michelinside.glucodatahandler.common

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.*
import kotlin.coroutines.cancellation.CancellationException


class ConnectionHandler : MessageClient.OnMessageReceivedListener {
    private val LOG_ID = "GlucoDataHandler.ConnectionHandler"
    val WEAR_CAPABILITY = "glucodata_intent"
    val GLUCODATA_INTENT_MESSAGE_PATH = "/glucodata_intent"

    fun SendMessage(context: Context, glucodataIntent: ByteArray)
    {
        Log.d(LOG_ID, "SendMessage called")
        try {
            /*val capabilityInfo: CapabilityInfo = Tasks.await(
                   Wearable.getCapabilityClient(context).getCapability(WEAR_CAPABILITY, CapabilityClient.FILTER_REACHABLE))
            Log.d(LOG_ID, "nodes received")
*/
            val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes) //capabilityInfo.nodes
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

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == WEAR_CAPABILITY) {
            Log.d(LOG_ID, "Message received: " + messageEvent.toString())
        }

    }

}