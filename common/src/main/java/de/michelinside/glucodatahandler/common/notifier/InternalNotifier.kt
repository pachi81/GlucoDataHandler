package de.michelinside.glucodatahandler.common.notifier

import android.content.Context
import android.os.Bundle
import android.util.Log
import java.util.Collections

object InternalNotifier {
    private const val LOG_ID = "GDH.InternalNotifier"
    private var notifiers = Collections.synchronizedMap(mutableMapOf<NotifierInterface, MutableSet<NotifySource>?>())
    private var timeNotifierCount = 0
    private var obsoleteNotifierCount = 0
    val hasTimeNotifier: Boolean get() = timeNotifierCount>0
    val hasObsoleteNotifier: Boolean get() = obsoleteNotifierCount>0

    fun addNotifier(context: Context, notifier: NotifierInterface, sourceFilter: MutableSet<NotifySource>)
    {
        val timeChanged = hasSource(notifier, NotifySource.TIME_VALUE) != sourceFilter.contains(NotifySource.TIME_VALUE) ||
                hasSource(notifier, NotifySource.OBSOLETE_VALUE) != sourceFilter.contains(NotifySource.OBSOLETE_VALUE)
        Log.i(LOG_ID, "add notifier $notifier - filter: $sourceFilter - timechanged: $timeChanged")
        notifiers[notifier] = sourceFilter.toMutableSet()
        Log.d(LOG_ID, "notifier size: " + notifiers.size.toString() )
        if(timeChanged)
            checkTimeNotifierChanged(context)

    }
    fun remNotifier(context: Context, notifier: NotifierInterface)
    {
        Log.i(LOG_ID, "rem notifier " + notifier.toString() )
        val timeChanged = hasSource(notifier, NotifySource.TIME_VALUE) ||
                hasSource(notifier, NotifySource.OBSOLETE_VALUE)
        notifiers.remove(notifier)
        Log.d(LOG_ID, "notifier size: " + notifiers.size.toString() )
        if(timeChanged)
            checkTimeNotifierChanged(context)
    }

    fun hasNotifier(notifier: NotifierInterface): Boolean {
        return notifiers.contains(notifier)
    }

    private fun getNotifiers(): MutableMap<NotifierInterface, MutableSet<NotifySource>?> {
        Log.d(LOG_ID, "getNotifiers")
        synchronized(notifiers) {
            Log.d(LOG_ID, "getNotifiers synchronized")
            return notifiers.toMutableMap()
        }
    }

    fun notify(context: Context, notifySource: NotifySource, extras: Bundle?)
    {
        Log.d(LOG_ID, "Sending new data from " + notifySource.toString() + " to " + getNotifierCount(notifySource) + " notifier(s).")
        val curNotifiers = getNotifiers()
        curNotifiers.forEach{
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
        val curNotifiers = getNotifiers()
        curNotifiers.forEach{
            if (it.value == null || it.value!!.contains(notifySource)) {
                Log.v(LOG_ID, "Notifier " + it.toString() + " has source " + notifySource.toString())
                count++
            }
        }
        return count
    }

    private fun checkTimeNotifierChanged(context: Context) {
        Log.v(LOG_ID, "checkTimeNotifierChanged called cur-time-count=$timeNotifierCount - cur-obsolete-count=$obsoleteNotifierCount")
        var trigger = false
        val newTimeCount = getNotifierCount(NotifySource.TIME_VALUE)
        if(timeNotifierCount != newTimeCount) {
            Log.d(LOG_ID, "Time notifier have changed from $timeNotifierCount to $newTimeCount")
            val curCount = timeNotifierCount
            timeNotifierCount = newTimeCount
            if(curCount == 0 || newTimeCount == 0)
                trigger = true
        }
        val newObsoleteCount = getNotifierCount(NotifySource.OBSOLETE_VALUE)
        if(obsoleteNotifierCount != newObsoleteCount) {
            Log.d(LOG_ID, "Obsolete notifier have changed from $obsoleteNotifierCount to $newObsoleteCount")
            val curCount = obsoleteNotifierCount
            obsoleteNotifierCount = newObsoleteCount
            if(curCount == 0 || newObsoleteCount == 0)
                trigger = true
        }
        if(trigger)
            notify(context, NotifySource.TIME_NOTIFIER_CHANGE, null)
    }

    private fun hasSource(notifier: NotifierInterface, notifySource: NotifySource): Boolean {
        if(hasNotifier(notifier)) {
            synchronized(notifiers) {
                if(notifiers.contains(notifier) && notifiers[notifier] != null) {
                    Log.v(LOG_ID, "Check notifier ${notifier} has source $notifySource: ${notifiers[notifier]}")
                    return notifiers[notifier]!!.contains(notifySource)
                }
            }
        }
        return false
    }
}