package de.michelinside.glucodatahandler.common.notifier

import android.content.Context
import android.os.Bundle
import android.util.Log

object InternalNotifier {
    private const val LOG_ID = "GDH.InternalNotifier"
    private var notifiers = mutableMapOf<NotifierInterface, MutableSet<NotifySource>?>()
    fun addNotifier(context: Context, notifier: NotifierInterface, sourceFilter: MutableSet<NotifySource>)
    {
        Log.i(LOG_ID, "add notifier " + notifier.toString() + " - filter: " + sourceFilter.toString() )
        notifiers[notifier] = sourceFilter
        Log.d(LOG_ID, "notifier size: " + notifiers.size.toString() )
        notify(context, NotifySource.NOTIFIER_CHANGE, null)
    }
    fun remNotifier(context: Context, notifier: NotifierInterface)
    {
        Log.i(LOG_ID, "rem notifier " + notifier.toString() )
        notifiers.remove(notifier)
        Log.d(LOG_ID, "notifier size: " + notifiers.size.toString() )
        notify(context, NotifySource.NOTIFIER_CHANGE, null)
    }

    fun hasNotifier(notifier: NotifierInterface): Boolean {
        return notifiers.contains(notifier)
    }

    fun notify(context: Context, notifySource: NotifySource, extras: Bundle?)
    {
        Log.d(LOG_ID, "Sending new data from " + notifySource.toString() + " to " + getNotifierCount(notifySource) + " notifier(s).")
        notifiers.forEach{
            try {
                if (it.value == null || it.value!!.contains(notifySource)) {
                    Log.v(LOG_ID, "Sending new data from " + notifySource.toString() + " to " + it.toString())
                    it.key.OnNotifyData(context, notifySource, extras)
                }
            } catch (exc: Exception) {
                Log.e(LOG_ID, "OnNotifyData exception: " + exc.message.toString() )
            }
        }
    }

    fun getNotifierCount(notifySource: NotifySource): Int {
        var count = 0
        notifiers.forEach {
            if (it.value == null || it.value!!.contains(notifySource)) {
                count++
            }
        }
        return count
    }
}