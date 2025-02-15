package de.michelinside.glucodatahandler.common.database

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import de.michelinside.glucodatahandler.common.AppSource
import de.michelinside.glucodatahandler.common.Command
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringWriter

object dbSync : ChannelClient.ChannelCallback() {
    private val LOG_ID = "GDH.dbSync"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var finished = true
    private var channel: ChannelClient.Channel? = null
    private var retryCount = 0

    private fun registerChannel(context: Context) {
        Log.d(LOG_ID, "registerChannel called")
        Wearable.getChannelClient(context).registerChannelCallback(this)
    }

    private fun getStringFromInputStream(stream: InputStream?): String {
        var n: Int
        val buffer = CharArray(1024 * 4)
        val reader = InputStreamReader(stream, "UTF8")
        val writer = StringWriter()
        while (-1 != (reader.read(buffer).also { n = it })) writer.write(buffer, 0, n)
        return writer.toString()
    }

    override fun onChannelOpened(p0: ChannelClient.Channel) {
        try {
            super.onChannelOpened(p0)
            Log.d(LOG_ID, "onChannelOpened")
            channel = p0
            scope.launch {
                try {
                    Log.d(LOG_ID, "receiving...")
                    val inputStream = Tasks.await(Wearable.getChannelClient(GlucoDataService.context!!).getInputStream(p0))
                    Log.d(LOG_ID, "received - read")
                    val received = getStringFromInputStream(inputStream)
                    Log.v(LOG_ID, "received data: $received")
                    val gson = Gson()
                    val data = gson.fromJson(received, Array<GlucoseValue>::class.java).toList()
                    Log.i(LOG_ID, "${data.size} values received")
                    dbAccess.addGlucoseValues(data)
                    Log.d(LOG_ID, "db data saved")
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "reading input exception: " + exc.message.toString() )
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onChannelOpened exception: " + exc.message.toString() )
        }
    }

    override fun onInputClosed(p0: ChannelClient.Channel, i: Int, i1: Int) {
        try {
            super.onInputClosed(p0, i, i1)
            Log.d(LOG_ID, "onInputClosed")
            Wearable.getChannelClient(GlucoDataService.context!!).close(p0)
            channel = null
            finished = true
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onInputClosed exception: " + exc.message.toString() )
        }
    }

    override fun onOutputClosed(p0: ChannelClient.Channel, p1: Int, p2: Int) {
        try {
            super.onOutputClosed(p0, p1, p2)
            Log.d(LOG_ID, "onOutputClosed")
            Wearable.getChannelClient(GlucoDataService.context!!).close(p0)
            channel = null
            finished = true
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onInputClosed exception: " + exc.message.toString() )
        }
    }

    fun sendData(context: Context, nodeId: String) {
        try {
            Log.i(LOG_ID, "send db data to $nodeId")
            finished = false
            val channelClient = Wearable.getChannelClient(context)
            val channelTask =
                channelClient.openChannel(nodeId, Constants.DB_SYNC_CHANNEL_PATH)
            channelTask.addOnSuccessListener { channel ->
                Thread {
                    try {
                        val outputStream = Tasks.await(channelClient.getOutputStream(channel))
                        val minTime = System.currentTimeMillis() - (if(GlucoDataService.appSource == AppSource.WEAR_APP) Constants.DB_MAX_DATA_TIME_MS else Constants.DB_MAX_DATA_WEAR_TIME_MS)  // from phone to wear, only send the last 24h
                        val data = dbAccess.getGlucoseValues(minTime)
                        Log.i(LOG_ID, "sending ${data.size} values")
                        val gson = Gson()
                        val string = gson.toJson(data)
                        Log.v(LOG_ID, string)
                        outputStream.write(string.toByteArray())
                        outputStream.flush()
                        outputStream.close()
                        channelClient.close(channel)
                        Log.d(LOG_ID, "db data sent")
                        if(GlucoDataService.appSource == AppSource.WEAR_APP) {
                            Log.i(LOG_ID, "Clear old data after sync")
                            dbAccess.deleteOldValues(System.currentTimeMillis()-Constants.DB_MAX_DATA_WEAR_TIME_MS)
                        }
                    } catch (exc: Exception) {
                        Log.e(LOG_ID, "sendData exception: " + exc.toString())
                    }
                }.start()
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "sendData exception: " + exc.toString())
        }
    }

    private fun close(context: Context) {
        try {
            if(channel != null) {
                Log.d(LOG_ID, "close channel")
                val channelClient = Wearable.getChannelClient(context)
                channelClient.close(channel!!)
                channelClient.unregisterChannelCallback(this)
                finished = true
                channel = null
                Log.d(LOG_ID, "channel closed")
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "close exception: " + exc.toString())
        }
    }

    private fun waitFor(context: Context) {
        Thread {
            try {
                Log.v(LOG_ID, "Waiting for receiving db data")
                var count = 0
                while (!finished && count < 10) {
                    Thread.sleep(1000)
                    count++
                }
                val success: Boolean
                if (!finished) {
                    Log.w(LOG_ID, "Receiving still not finished!")
                    success = false
                } else {
                    Log.d(LOG_ID, "Receiving finished!")
                    success = true
                }
                close(context)
                if (success) {
                    Log.i(LOG_ID, "db sync succeeded")
                } else {
                    Log.w(LOG_ID, "db sync failed")
                    if(retryCount < 3) {
                        retryCount++
                        requestDbSync(context, retryCount)
                    }
                }
            } catch (exc: Exception) {
                Log.e(LOG_ID, "waitFor exception: " + exc.message.toString() )
            }
        }.start()
    }

    fun requestDbSync(context: Context, curRetry: Int = 0) {
        Log.d(LOG_ID, "request db sync - finished: $finished - retry: $curRetry")
        if (finished) {
            finished = false
            retryCount = curRetry
            registerChannel(context)
            GlucoDataService.sendCommand(Command.DB_SYNC)
            waitFor(context)
        }
    }
}