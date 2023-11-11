package de.michelinside.glucodatahandler.common.tasks

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.util.Log
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifyDataSource

class ElapsedTimeTask : BackgroundTask() {
    private val LOG_ID = "GlucoDataHandler.Task.ElapsedTimeTask"

    companion object {
        private var relativeTimeValue = false
        val relativeTime: Boolean get() {return relativeTimeValue}
    }

    override fun getIntervalMinute(): Long {
        return 1
    }

    override fun execute(context: Context) {
        Handler(context.mainLooper).post {
            Log.d(LOG_ID, "send time notifier")
            InternalNotifier.notify(context, NotifyDataSource.TIME_VALUE, null)
        }
    }

    override fun active(elapsetTimeMinute: Long): Boolean {
        return relativeTimeValue && elapsetTimeMinute > 0 && elapsetTimeMinute <= 60
    }

    override fun checkPreferenceChanged(sharedPreferences: SharedPreferences, key: String?, context: Context): Boolean {
        if ((key == null || key == Constants.SHARED_PREF_RELATIVE_TIME)) {
            if( relativeTimeValue != sharedPreferences.getBoolean(Constants.SHARED_PREF_RELATIVE_TIME, false) ) {
                relativeTimeValue = sharedPreferences.getBoolean(Constants.SHARED_PREF_RELATIVE_TIME, false)
                Log.d(LOG_ID, "relative time setting changed to " + relativeTimeValue)
                InternalNotifier.notify(context, NotifyDataSource.TIME_VALUE, null)
                return true
            }
        }
        return false
    }
}