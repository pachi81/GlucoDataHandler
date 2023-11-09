package de.michelinside.glucodatahandler.common.tasks

import android.content.Context
import android.os.Handler
import android.util.Log
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifyDataSource

class ObsoleteTask : BackgroundTask() {
    private val LOG_ID = "GlucoDataHandler.Task.ObsoleteTask"
    override fun getIntervalMinute(): Long {
        // obsolete will occur after 5 and 10 minutes
        return 5
    }

    override fun execute(context: Context) {
        Handler(context.mainLooper).post {
            Log.d(LOG_ID, "send obsolete notifier")
            InternalNotifier.notify(context, NotifyDataSource.OBSOLETE_VALUE, null)
            if (!ElapsedTimeTask.relativeTime) {// also send time_value, if the ElapsedTimeTask not running
                Log.d(LOG_ID, "send time notifier")
                InternalNotifier.notify(context, NotifyDataSource.TIME_VALUE, null)
            }
        }
    }

    override fun active(elapsetTimeMinute: Long): Boolean {
        return elapsetTimeMinute <= 10
    }
}