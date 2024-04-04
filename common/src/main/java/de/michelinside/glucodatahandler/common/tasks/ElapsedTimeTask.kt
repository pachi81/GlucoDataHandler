package de.michelinside.glucodatahandler.common.tasks

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.util.Log
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifySource

class ElapsedTimeTask : BackgroundTask() {
    companion object {
        private val LOG_ID = "GDH.Task.Time.ElapsedTask"
        private var relativeTimeValue = false
        private var interval = 0L
        val relativeTime: Boolean get() {return relativeTimeValue}
        fun setInterval(new_interval: Long) {
            Log.d(LOG_ID, "setInterval called for new interval: " + new_interval + " - current: " + interval)
            interval = new_interval
            TimeTaskService.checkTimer()
        }
    }

    override fun getIntervalMinute(): Long {
        return if (relativeTimeValue) 1L
        else if(interval > 0) interval
        else if(InternalNotifier.getNotifierCount(NotifySource.TIME_VALUE) > 0) 1L
        else 0
    }

    override fun execute(context: Context) {
        Handler(context.mainLooper).post {
            Log.d(LOG_ID, "send time notifier")
            InternalNotifier.notify(context, NotifySource.TIME_VALUE, null)
        }
    }

    override fun active(elapsetTimeMinute: Long): Boolean {
        return (relativeTimeValue || interval > 0 || InternalNotifier.getNotifierCount(NotifySource.TIME_VALUE) > 0) && elapsetTimeMinute <= 60
    }

    override fun checkPreferenceChanged(sharedPreferences: SharedPreferences, key: String?, context: Context): Boolean {
        if ((key == null || key == Constants.SHARED_PREF_RELATIVE_TIME)) {
            if( relativeTimeValue != sharedPreferences.getBoolean(Constants.SHARED_PREF_RELATIVE_TIME, false) ) {
                relativeTimeValue = sharedPreferences.getBoolean(Constants.SHARED_PREF_RELATIVE_TIME, false)
                Log.d(LOG_ID, "relative time setting changed to " + relativeTimeValue)
                InternalNotifier.notify(context, NotifySource.TIME_VALUE, null)
                return true
            }
        }
        return false
    }
}