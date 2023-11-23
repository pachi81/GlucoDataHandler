package de.michelinside.glucodatahandler.common.tasks

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

@SuppressLint("StaticFieldLeak")
object SourceTaskService: BackgroundTaskService(43, "GDH.Task.SourceTaskService", true) {
    override fun getAlarmReceiver() : Class<*> = SourceAlarmReceiver::class.java

    override fun getBackgroundTasks(): MutableList<BackgroundTask> =
        mutableListOf(LibreViewSourceTask(), NightscoutSourceTask(), ObsoleteTask())  // Obsolete should always the last one!
}

class SourceAlarmReceiver(): BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        SourceTaskService.alarmTrigger(intent)
    }
}
