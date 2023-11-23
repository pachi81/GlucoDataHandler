package de.michelinside.glucodatahandler.common.tasks

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.util.Log
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifySource

class ObsoleteTask : BackgroundTask() {
    private val LOG_ID = "GDH.Task.ObsoleteTask"
    private var delaySec = 0L
    override fun getIntervalMinute(): Long {
        // obsolete will occur after 5 and 10 minutes
        return 5
    }

    override fun execute(context: Context) {
        Handler(context.mainLooper).post {
            Log.d(LOG_ID, "send obsolete notifier")
            InternalNotifier.notify(context, NotifySource.OBSOLETE_VALUE, null)
            if (!ElapsedTimeTask.relativeTime) {// also send time_value, if the ElapsedTimeTask not running
                Log.d(LOG_ID, "send time notifier")
                InternalNotifier.notify(context, NotifySource.TIME_VALUE, null)
            }
        }
    }

    override fun active(elapsetTimeMinute: Long): Boolean {
        return  elapsetTimeMinute <= 10
    }

    // use same as source delay, for example to have the delay also on the device, wear the sources are not activated...
    override fun getDelayMs(): Long = delaySec * 1000L

    override fun checkPreferenceChanged(sharedPreferences: SharedPreferences, key: String?, context: Context): Boolean {
        if(key == null) {
            delaySec = sharedPreferences.getInt(Constants.SHARED_PREF_SOURCE_DELAY, 0).toLong()
            return true
        } else {
            var result = false
            when(key) {
                Constants.SHARED_PREF_SOURCE_DELAY -> {
                    if (delaySec != sharedPreferences.getInt(Constants.SHARED_PREF_SOURCE_DELAY, 0).toLong()) {
                        delaySec = sharedPreferences.getInt(Constants.SHARED_PREF_SOURCE_DELAY, 0).toLong()
                        result = true  // retrigger alarm after delay has changed
                    }
                }
            }
            return result
        }
    }
}