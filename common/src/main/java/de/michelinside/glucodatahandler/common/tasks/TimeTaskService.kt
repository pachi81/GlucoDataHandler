package de.michelinside.glucodatahandler.common.tasks

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkerParameters
import de.michelinside.glucodatahandler.common.notifier.NotifySource

@SuppressLint("StaticFieldLeak")
object TimeTaskService: BackgroundTaskService(42, "GDH.Task.Time.TaskService", TimeTaskWorker::class.java) {
    override fun getAlarmReceiver() : Class<*> = TimeAlarmReceiver::class.java

    // also notify for TIME_NOTIFIER_CHANGE as if there is no receiver, the alarm manager is not needed
    override fun getNotifySourceFilter() : MutableSet<NotifySource> = mutableSetOf(NotifySource.TIME_NOTIFIER_CHANGE)

    override fun getBackgroundTasks(): MutableList<BackgroundTask> =
        mutableListOf(ElapsedTimeTask(), ObsoleteTask())
}


class TimeTaskWorker(context: Context, workerParams: WorkerParameters): BackgroundWorker(context, workerParams) {
    override fun execute(context: Context) {
        TimeTaskService.alarmTrigger()
    }
}

class TimeAlarmReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if(TimeTaskService.useWorker)
            BackgroundWorker.triggerWork(context!!, TimeTaskWorker::class.java)
        else
            TimeTaskService.alarmTrigger()
    }
}
