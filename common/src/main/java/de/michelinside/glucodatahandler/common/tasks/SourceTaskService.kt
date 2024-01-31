package de.michelinside.glucodatahandler.common.tasks

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkerParameters

@SuppressLint("StaticFieldLeak")
object SourceTaskService: BackgroundTaskService(43, "GDH.Task.Source.TaskService", true) {
    override fun getAlarmReceiver() : Class<*> = SourceAlarmReceiver::class.java

    override fun getBackgroundTasks(): MutableList<BackgroundTask> =
        mutableListOf(LibreViewSourceTask(), NightscoutSourceTask())  // Obsolete should always the last one!
    override fun hasIobCobSupport() = true
}

class SourceTaskWorker(context: Context, workerParams: WorkerParameters): BackgroundWorker(context, workerParams) {
    override fun execute(context: Context) {
        SourceTaskService.alarmTrigger()
    }
}

class SourceAlarmReceiver(): BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if(SourceTaskService.useWorker)
            BackgroundWorker.triggerWork(context!!, SourceTaskWorker::class.java)
        else
            SourceTaskService.alarmTrigger()
    }
}
