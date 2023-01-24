package de.michelinside.glucodatahandler.common

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.*
import kotlin.coroutines.cancellation.CancellationException


open class GlucoDataService : WearableListenerService(), MessageClient.OnMessageReceivedListener, CapabilityClient.OnCapabilityChangedListener, ReceiveDataInterface {
    private val LOG_ID = "GlucoDataHandler.GlucoDataService"
    private var isForegroundService = false

    override fun onCreate() {
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
    }

    override fun onDestroy() {
        ReceiveData.remNotifier(this)
        Wearable.getMessageClient(this).removeListener(this)
        Wearable.getCapabilityClient(this).removeListener(this)
        super.onDestroy()
        Log.d(LOG_ID, "onDestroy called")
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
        Log.i(LOG_ID, "onCapabilityChanged called: " + capabilityInfo.toString())
        ReceiveData.capabilityInfo = capabilityInfo
        ReceiveData.notify(this, ReceiveDataSource.CAPILITY_INFO, ReceiveData.curExtraBundle)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(LOG_ID, "onStartCommand called")
        super.onStartCommand(intent, flags, startId)
        val isForeground = intent?.getBooleanExtra(Constants.SHARED_PREF_FOREGROUND_SERVICE, false)
        if (isForeground == true && !isForegroundService) {
            isForegroundService = true
            Log.i(LOG_ID, "Starting service in foreground!")
            val CHANNEL_ID = "glucodatahandler_service_01"
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Foregorund GlucoDataService",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
                channel
            )
            val notification: Notification = Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("")
                .setContentText("").build()
            startForeground(1, notification)
        } else if ( isForegroundService && intent?.getBooleanExtra(Constants.ACTION_STOP_FOREGROUND, false) == true ) {
            isForegroundService = false
            Log.i(LOG_ID, "Stopping service in foreground!")
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        return START_STICKY  // keep alive
    }

    override fun OnReceiveData(context: Context, dataSource: ReceiveDataSource, extras: Bundle?) {
        if (dataSource != ReceiveDataSource.MESSAGECLIENT && extras != null) {
            Log.d(LOG_ID, "Forward received intent.extras")
            Thread {
                SendMessage(context, Utils.bundleToBytes(extras))
            }.start()
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