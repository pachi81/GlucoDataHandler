package de.michelinside.glucodatahandler.common.tasks

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import de.michelinside.glucodatahandler.common.utils.Log
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifySource

class ElapsedTimeTask : BackgroundTask() {
    companion object {
        private val LOG_ID = "GDH.Task.Time.ElapsedTask"
        private var relativeTimeValue = true
        private var interval = 0L
        val relativeTime: Boolean get() {return relativeTimeValue}
        fun setInterval(new_interval: Long) {
            Log.d(LOG_ID, "setInterval called for new interval: " + new_interval + " - current: " + interval)
            interval = new_interval
            TimeTaskService.checkTimer()
        }

        val isActive: Boolean get() {
            Log.v(LOG_ID, "Check active: - has notifier: ${InternalNotifier.hasTimeNotifier} - relativeTime: $relativeTime - interval: $interval")
            return (InternalNotifier.hasTimeNotifier && (relativeTime || interval > 0))
        }
    }

    override fun getIntervalMinute(): Long {
        return if (relativeTimeValue) 1L
        else if(interval > 0) interval
        else if(InternalNotifier.hasTimeNotifier) 1L
        else 0
    }

    override fun execute(context: Context) {
        Handler(context.mainLooper).post {
            Log.d(LOG_ID, "send time notifier")
            InternalNotifier.notify(context, NotifySource.TIME_VALUE, null)
        }
    }

    override fun active(elapsetTimeMinute: Long): Boolean {
        Log.v(LOG_ID, "Check active for elapsed time $elapsetTimeMinute min")
        return isActive && (InternalNotifier.hasTimeNotifier || elapsetTimeMinute <= 60)
    }

    override fun checkPreferenceChanged(sharedPreferences: SharedPreferences, key: String?, context: Context): Boolean {
        if ((key == null || key == Constants.SHARED_PREF_RELATIVE_TIME)) {
            if( relativeTimeValue != sharedPreferences.getBoolean(Constants.SHARED_PREF_RELATIVE_TIME, true) ) {
                relativeTimeValue = sharedPreferences.getBoolean(Constants.SHARED_PREF_RELATIVE_TIME, true)
                Log.d(LOG_ID, "relative time setting changed to " + relativeTime)
                InternalNotifier.notify(context, NotifySource.TIME_VALUE, null)
                return true
            }
        }
        return false
    }
}