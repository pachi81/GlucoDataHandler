package de.michelinside.glucodatahandler.common.tasks

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import de.michelinside.glucodatahandler.common.Constants

@SuppressLint("StaticFieldLeak")
object SourceTaskService: BackgroundTaskService(43, "GDH.Task.SourceTaskService", true) {
    private var delay = 10L
    override fun getAlarmReceiver() : Class<*> = SourceAlarmReceiver::class.java

    override fun getBackgroundTasks(): MutableList<BackgroundTask> =
        mutableListOf(LibreViewSourceTask(),NightscoutSourceTask())

    override fun getDelay(): Long = delay

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        try {
            if (sharedPreferences != null) {
                Log.d(LOG_ID, "onSharedPreferenceChanged called for " + key)
                if(key == null) {
                    delay = sharedPreferences.getInt(Constants.SHARED_PREF_SOURCE_DELAY, -1).toLong()
                } else {
                    when(key) {
                        Constants.SHARED_PREF_SOURCE_DELAY -> {
                            delay = sharedPreferences.getInt(Constants.SHARED_PREF_SOURCE_DELAY, -1).toLong()
                        }
                    }
                }
            }
            super.onSharedPreferenceChanged(sharedPreferences, key)
        } catch (ex: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged: " + ex)
        }
    }
}

class SourceAlarmReceiver(): BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        SourceTaskService.alarmTrigger(intent)
    }
}
