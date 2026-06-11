package de.michelinside.glucodatahandler.common.service

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import de.michelinside.glucodatahandler.common.Command
import de.michelinside.glucodatahandler.common.WearPhoneConnection
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.Log

object WearPhoneManager {
    private const val LOG_ID = "GDH.srv.WearPhoneManager"

    @JvmStatic
    @SuppressLint("StaticFieldLeak")
    private var connection: WearPhoneConnection? = null
    
    fun init(context: Context, sendSettings: Boolean) {
        if (connection == null) {
            connection = WearPhoneConnection()
            connection!!.open(context, sendSettings)
        }
    }

    fun close() {
        if (connection != null) {
            connection!!.close()
            connection = null
        }
    }


    fun getWearPhoneConnection(): WearPhoneConnection? {
        return connection
    }

    fun checkForConnectedNodes(refreshDataOnly: Boolean = false) {
        try {
            Log.d(LOG_ID, "checkForConnectedNodes called for dataOnly=$refreshDataOnly - connection: ${connection!=null}")
            if (connection!=null) {
                if(!refreshDataOnly)
                    connection!!.checkForConnectedNodes(true)
                else
                    connection!!.checkForNodesWithoutData()
            }
        } catch (exc: Exception) {
            Log.e(
                LOG_ID,
                "checkForConnectedNodes exception: " + exc.message.toString()
            )
        }
    }

    fun resetWearPhoneConnection() {
        try {
            if (connection != null) {
                connection!!.resetConnection()
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID,"resetWearPhoneConnection exception: " + exc.message.toString())
        }
    }

    fun sendCommand(command: Command, extras: Bundle? = null) {
        try {
            if (connection!=null)
                connection!!.sendCommand(command, extras)
        } catch (exc: Exception) {
            Log.e(
                LOG_ID,
                "sendCommand exception: " + exc.message.toString()
            )
        }
    }

    fun sendToConnectedDevices(dataSource: NotifySource, extras: Bundle) {
        Thread {
            try {
                if(connection != null)
                    connection!!.sendMessage(dataSource, extras, null)
            } catch (exc: Exception) {
                Log.e(LOG_ID, "SendMessage exception: " + exc.toString())
            }
        }.start()
    }

    fun sendLogcatRequest() {
        if(connection != null) {
            Log.d(LOG_ID, "sendLogcatRequest called")
            connection!!.sendMessage(NotifySource.LOGCAT_REQUEST, null, filterReceiverId = connection!!.pickBestNodeId())
        }
    }
}