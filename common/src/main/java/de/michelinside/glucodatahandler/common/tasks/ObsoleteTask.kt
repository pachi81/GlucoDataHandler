package de.michelinside.glucodatahandler.common.tasks

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import de.michelinside.glucodatahandler.common.utils.Log
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifySource

class ObsoleteTask : BackgroundTask() {
    private val LOG_ID = "GDH.Task.Time.ObsoleteTask"
    private var obsoleteTime = ReceiveData.obsoleteTimeInMinute
    override fun getIntervalMinute(): Long {
        // obsolete will occur after 5 and 10 minutes
        return obsoleteTime.toLong()
    }

    override fun execute(context: Context) {
        Handler(context.mainLooper).post {
            Log.d(LOG_ID, "send obsolete notifier")
            InternalNotifier.notify(context, NotifySource.OBSOLETE_VALUE, null)
            if (!ElapsedTimeTask.isActive) {// also send time_value, if the ElapsedTimeTask not running
                Log.d(LOG_ID, "send time notifier from obsolete")
                InternalNotifier.notify(context, NotifySource.TIME_VALUE, null)
            }
        }
    }

    override fun active(elapsetTimeMinute: Long): Boolean {
        Log.v(LOG_ID, "Check active for elapsed time $elapsetTimeMinute min - has notifier: ${InternalNotifier.hasObsoleteNotifier}")
        return elapsetTimeMinute <= (2*getIntervalMinute()) && InternalNotifier.hasObsoleteNotifier
    }
    override fun checkPreferenceChanged(sharedPreferences: SharedPreferences, key: String?, context: Context): Boolean {
        if ((key == null || key == Constants.SHARED_PREF_OBSOLETE_TIME)) {
            if( obsoleteTime != sharedPreferences.getInt(Constants.SHARED_PREF_OBSOLETE_TIME, obsoleteTime) ) {
                obsoleteTime = sharedPreferences.getInt(Constants.SHARED_PREF_OBSOLETE_TIME, obsoleteTime)
                Log.i(LOG_ID, "obsolete time setting changed to $obsoleteTime")
                InternalNotifier.notify(context, NotifySource.OBSOLETE_VALUE, null)
                if (!ElapsedTimeTask.isActive) {// also send time_value, if the ElapsedTimeTask not running
                    Log.d(LOG_ID, "send time notifier")
                    InternalNotifier.notify(context, NotifySource.TIME_VALUE, null)
                }
                return true
            }
        }
        return false
    }
}