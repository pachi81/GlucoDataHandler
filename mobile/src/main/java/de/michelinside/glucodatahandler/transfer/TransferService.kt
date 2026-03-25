package de.michelinside.glucodatahandler.transfer

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.Log

object TransferService: NotifierInterface, SharedPreferences.OnSharedPreferenceChangeListener {
    val LOG_ID = "GDH.transfer.service"
    private var taskList = mutableListOf<TransferTask>()

    fun start(context: Context) {
        try {
            Log.i(LOG_ID, "start called")
            taskList.clear()
            taskList.add(GlucoDataAuto())
            taskList.add(HealthConnectTask())
            taskList.add(ToXDripBroadcast())
            taskList.add(XDripBroadcast())
            taskList.add(GlucoDataBroadcast())
            taskList.add(NightscoutUploadTask())

            InternalNotifier.addNotifier(
                context,
                this,
                mutableSetOf(
                    NotifySource.BROADCAST,
                    NotifySource.MESSAGECLIENT
                )
            )
            val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            sharedPref.registerOnSharedPreferenceChangeListener(this)
            onSharedPreferenceChanged(sharedPref, null)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "start exception: " + exc.message.toString() )
        }
    }

    fun stop(context: Context) {
        try {
            Log.i(LOG_ID, "stop called")
            taskList.clear()
            InternalNotifier.remNotifier(context, this)
            val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            sharedPref.unregisterOnSharedPreferenceChangeListener(this)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "stop exception: " + exc.message.toString() )
        }
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            Log.d(LOG_ID, "OnNotifyData called for source " + dataSource.toString())
            if(extras != null) {
                executeTasks(context)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: " + exc.message.toString() )
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        try {
            Log.d(LOG_ID, "onSharedPreferenceChanged called for key $key")
            taskList.forEach { task ->
                if(task.checkPreferenceChanged(sharedPreferences, key) && ReceiveData.time > 0) {
                    task.checkExecution(GlucoDataService.context!!)
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.message.toString() )
        }
    }

    private fun executeTasks(context: Context,) {
        try {
            taskList.forEach { task ->
                task.checkExecution(context)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "executeTasks exception: " + exc.message.toString() )
        }
    }
}