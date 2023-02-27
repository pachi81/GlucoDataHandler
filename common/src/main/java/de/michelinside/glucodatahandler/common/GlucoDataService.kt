package de.michelinside.glucodatahandler.common

import android.content.Context
import android.content.IntentFilter
import android.net.Uri
import android.os.*
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.*
import kotlin.coroutines.cancellation.CancellationException


open class GlucoDataService : WearableListenerService(), MessageClient.OnMessageReceivedListener, CapabilityClient.OnCapabilityChangedListener, ReceiveDataInterface {
    private val LOG_ID = "GlucoDataHandler.GlucoDataService"
    private lateinit var receiver: GlucoseDataReceiver
    private var lastAlarmTime = 0L
    private var lastAlarmType = ReceiveData.AlarmType.OK

    companion object GlucoDataService {
        private var isRunning = false
        val running get() = isRunning
    }

    override fun onCreate() {
        try {
            super.onCreate()
            Log.i(LOG_ID, "onCreate called")
            isRunning = true

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
            receiver = GlucoseDataReceiver()
            registerReceiver(receiver, intentFilter)
            if (BuildConfig.DEBUG) {
                Thread {
                    while (true) {
                        // create Thread which send dummy intents
                        this.sendBroadcast(Utils.getDummyGlucodataIntent(true))
                        Thread.sleep(10000)
                    }
                }.start()
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreate exception: " + exc.toString())
        }
    }

    override fun onDestroy() {
        try {
            Log.w(LOG_ID, "onDestroy called")
            unregisterReceiver(receiver)
            ReceiveData.remNotifier(this)
            Wearable.getMessageClient(this).removeListener(this)
            Wearable.getCapabilityClient(this).removeListener(this)
            super.onDestroy()
            isRunning = false
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

    fun getVibrationPattern(alarmType: ReceiveData.AlarmType): LongArray? {
        return when(alarmType) {
            ReceiveData.AlarmType.LOW_ALARM -> longArrayOf(0, 1000, 500, 1000, 500, 1000, 500, 1000, 500, 1000, 500, 1000)
            ReceiveData.AlarmType.LOW -> longArrayOf(0, 700, 500, 700, 5000, 700, 500, 700)
            ReceiveData.AlarmType.HIGH -> longArrayOf(0, 500, 500, 500, 500, 500, 500, 500)
            ReceiveData.AlarmType.HIGH_ALARM -> longArrayOf(0, 800, 500, 800, 800, 600, 800, 800, 500, 800, 800, 600, 800)
            else -> null
        }
    }

    fun vibrate(alarmType: ReceiveData.AlarmType): Boolean {
        val vibratePattern = getVibrationPattern(alarmType) ?: return false
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        Log.i(LOG_ID, "vibration for " + alarmType.toString())
        vibrator.vibrate(VibrationEffect.createWaveform(vibratePattern, -1))
        return true
    }

    override fun OnReceiveData(context: Context, dataSource: ReceiveDataSource, extras: Bundle?) {
        try {
            Log.d(LOG_ID, "OnReceiveData for source " + dataSource.toString() + " and extras " + extras.toString())
            if (dataSource != ReceiveDataSource.MESSAGECLIENT && extras != null) {
                Thread {
                    SendMessage(context, Utils.bundleToBytes(extras))
                }.start()
            }
            val sharedPref = this.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            if (sharedPref.getBoolean(Constants.SHARED_PREF_NOTIFICATION, false)) {
                val curAlarmType = ReceiveData.getAlarmType()
                val forceAlarm = (ReceiveData.alarm and 8) != 0 // alarm triggered by Juggluco
                Log.d(LOG_ID, "Check vibration: force=" + forceAlarm.toString() +
                        " - curAlarmType=" + curAlarmType.toString() +
                        " - lastAlarmType=" + lastAlarmType.toString() +
                        " - lastAlarmTime=" + lastAlarmTime.toString() +
                        " - time=" + ReceiveData.time.toString() +
                        " - delta=" + ReceiveData.delta.toString() +
                        " - rate=" + ReceiveData.rate.toString() +
                        " - diff=" + (ReceiveData.time - lastAlarmTime).toString()
                )
                if (curAlarmType == ReceiveData.AlarmType.LOW_ALARM || curAlarmType == ReceiveData.AlarmType.LOW)
                {
                    // Low alarm only, if the values are still falling!
                    val durLow = sharedPref.getLong(Constants.SHARED_PREF_NOTIFY_DURATION_LOW, 15) * 60 * 1000
                    if( forceAlarm || curAlarmType < lastAlarmType || ((ReceiveData.delta < 0F || ReceiveData.rate < 0F) && (ReceiveData.time - lastAlarmTime >= durLow)) )
                    {
                        if( vibrate(curAlarmType) ) {
                            lastAlarmTime = ReceiveData.time
                            lastAlarmType = curAlarmType
                        }
                    }
                }
                else if (curAlarmType == ReceiveData.AlarmType.HIGH_ALARM || curAlarmType == ReceiveData.AlarmType.HIGH)
                {
                    // High alarm only, if the values are still rising!
                    val durHigh = sharedPref.getLong(Constants.SHARED_PREF_NOTIFY_DURATION_HIGH, 20) * 60 * 1000
                    if( forceAlarm || curAlarmType > lastAlarmType || ((ReceiveData.delta > 0F || ReceiveData.rate > 0F) && (ReceiveData.time - lastAlarmTime >= durHigh)) )
                    {
                        if( vibrate(curAlarmType) ) {
                            lastAlarmTime = ReceiveData.time
                            lastAlarmType = curAlarmType
                        }
                    }
                }
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