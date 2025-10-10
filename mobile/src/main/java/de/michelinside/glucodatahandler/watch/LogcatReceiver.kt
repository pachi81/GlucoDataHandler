package de.michelinside.glucodatahandler.watch

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import de.michelinside.glucodatahandler.common.utils.Log
import android.widget.Toast
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import de.michelinside.glucodatahandler.GlucoDataServiceMobile
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.FileOutputStream

object LogcatReceiver : ChannelClient.ChannelCallback() {
    private val LOG_ID = "GDH.wear.LogcatReceiver"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var finished = true
    private var fileUri: Uri? = null
    private var channel: ChannelClient.Channel? = null
    private var receivedBytes = 0

    val isActive: Boolean get() = !finished
    @SuppressLint("StaticFieldLeak")


    fun registerChannel(context: Context, uri: Uri) {
        Log.d(LOG_ID, "registerChannel called")
        fileUri = uri
        Wearable.getChannelClient(context).registerChannelCallback(this)
    }

    override fun onChannelOpened(p0: ChannelClient.Channel) {
        try {
            Log.d(LOG_ID, "onChannelOpened for path ${p0.path}")
            if(p0.path != Constants.LOGCAT_CHANNEL_PATH)
                return
            super.onChannelOpened(p0)
            channel = p0
            scope.launch {
                try {
                    Log.d(LOG_ID, "receiving...")
                    receivedBytes = 0
                    val inputStream = Tasks.await(Wearable.getChannelClient(GlucoDataService.context!!).getInputStream(p0))
                    Log.d(LOG_ID, "received, save to file " + fileUri)
                    GlucoDataService.context!!.contentResolver.openFileDescriptor(fileUri!!, "w")?.use {
                        FileOutputStream(it.fileDescriptor).use { os ->
                            Log.v(LOG_ID, "read")
                            val buffer = ByteArray(4 * 1024) // or other buffer size
                            var read: Int
                            while (inputStream.read(buffer).also { rb -> read = rb } != -1) {
                                Log.v(LOG_ID, "write $read bytes")
                                os.write(buffer, 0, read)
                                receivedBytes += read
                            }
                            Log.v(LOG_ID, "flush")
                            os.flush()
                        }
                    }
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
            Log.d(LOG_ID, "onInputClosed for path ${p0.path}")
            if(p0.path != Constants.LOGCAT_CHANNEL_PATH)
                return
            super.onInputClosed(p0, i, i1)
            Wearable.getChannelClient(GlucoDataService.context!!).close(p0)
            channel = null
            finished = true
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onInputClosed exception: " + exc.message.toString() )
        }
    }

    fun waitFor(context: Context) {
        Thread {
            try {
                Log.v(LOG_ID, "Waiting for receiving logs")
                var count = 0
                while (!finished && count < 10) {
                    Thread.sleep(1000)
                    count++
                }
                var success: Boolean
                if (!finished) {
                    Log.w(LOG_ID, "Receiving still not finished after receiving $receivedBytes bytes!")
                    if(channel != null)
                        Wearable.getChannelClient(context).close(channel!!)
                    success = false
                } else {
                    Log.d(LOG_ID, "Receiving finished for $receivedBytes bytes!")
                    success = receivedBytes > 0
                }
                Wearable.getChannelClient(context).unregisterChannelCallback(this)
                Log.d(LOG_ID, "unregisterChannel called")
                finished = true
                val text = if (success) {
                    GlucoDataService.context!!.resources.getText(R.string.logcat_wear_save_succeeded)
                } else {
                    GlucoDataService.context!!.resources.getText(R.string.logcat_wear_save_failed)
                }
                Handler(GlucoDataService.context!!.mainLooper).post {
                    Toast.makeText(GlucoDataService.context!!, text, Toast.LENGTH_SHORT).show()
                }
            } catch (exc: Exception) {
                Log.e(LOG_ID, "waitFor exception: " + exc.message.toString() )
            }
        }.start()
    }

    fun requestLogs(context: Context, uri: Uri) {
        if (finished) {
            Log.v(LOG_ID, "request logs to " + uri)
            finished = false
            channel = null
            registerChannel(context, uri)
            GlucoDataServiceMobile.sendLogcatRequest()
            waitFor(context)
        }
    }
}