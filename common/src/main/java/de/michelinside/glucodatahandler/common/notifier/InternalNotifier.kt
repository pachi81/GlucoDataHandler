package de.michelinside.glucodatahandler.common.notifier

import android.content.Context
import android.os.Bundle
import android.util.Log

object InternalNotifier {
    private const val LOG_ID = "GlucoDataHandler.InternalNotifier"
    private var notifiers = mutableMapOf<NotifierInterface, MutableSet<NotifyDataSource>?>()
    fun addNotifier(notifier: NotifierInterface, sourceFilter: MutableSet<NotifyDataSource>)
    {
        Log.d(LOG_ID, "add notifier " + notifier.toString() )
        notifiers[notifier] = sourceFilter
        Log.d(LOG_ID, "notifier size: " + notifiers.size.toString() )
    }
    fun remNotifier(notifier: NotifierInterface)
    {
        Log.d(LOG_ID, "rem notifier " + notifier.toString() )
        notifiers.remove(notifier)
        Log.d(LOG_ID, "notifier size: " + notifiers.size.toString() )
    }

    fun notify(context: Context, dataSource: NotifyDataSource, extras: Bundle?)
    {
        Log.d(LOG_ID, "Sending new data to " + notifiers.size.toString() + " notifier(s).")
        notifiers.forEach{
            try {
                if (it.value == null || it.value!!.contains(dataSource)) {
                    Log.d(LOG_ID, "Sending new data from " + dataSource.toString() + " to " + it.toString())
                    it.key.OnNotifyData(context, dataSource, extras)
                }
            } catch (exc: Exception) {
                Log.e(LOG_ID, "OnReceiveData exception: " + exc.message.toString() )
            }
        }
    }
}