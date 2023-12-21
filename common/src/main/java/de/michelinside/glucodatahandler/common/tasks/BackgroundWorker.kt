package de.michelinside.glucodatahandler.common.tasks

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.R
import de.michelinside.glucodatahandler.common.notification.ChannelType
import de.michelinside.glucodatahandler.common.notification.Channels
import java.time.Duration

abstract class BackgroundWorker(val context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    private val LOG_ID = "GDH.Task.BackgroundWorker"
    companion object {
        fun triggerWork(context: Context, taskServiceClass: Class<out BackgroundWorker>) {
            Log.v("GDH.Task.BackgroundTaskService", "triggerWork called for class " + taskServiceClass.simpleName)
            val builder = OneTimeWorkRequest.Builder(taskServiceClass)
                .setConstraints(
                    Constraints.Builder()
                        .setTriggerContentMaxDelay(Duration.ofMillis(100))
                        .build()
                ).setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)

            val workManager = WorkManager.getInstance(context)
            workManager.enqueue(builder.build())
        }

        fun stopAllWork(context: Context) {
            val workManager = WorkManager.getInstance(context)
            workManager.cancelAllWork()
            TimeTaskService.stop()
            SourceTaskService.stop()
        }
    }

    abstract fun execute(context: Context)

    override fun doWork(): Result {
        Log.v(LOG_ID, "doWork called")
        try {
            execute(context)
        } catch (ex: Exception) {
            Log.e(LOG_ID, "doWork: " + ex)
        }
        Log.v(LOG_ID, "doWork finished")
        return Result.success()
    }

    override fun onStopped() {
        Log.w(LOG_ID, "onStopped called")
    }

    override fun getForegroundInfo(): ForegroundInfo {
        Log.w(LOG_ID, "getForegroundInfo called")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return ForegroundInfo(GlucoDataService.NOTIFICATION_ID, getNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        }

        return ForegroundInfo(GlucoDataService.NOTIFICATION_ID,getNotification())
    }

    private fun getNotification() : Notification {
        if (GlucoDataService.service != null) {
            return GlucoDataService.service!!.getNotification()
        }

        // create foreground notification runner
        Channels.createNotificationChannel(context, ChannelType.WORKER)

        val notification = NotificationCompat.Builder(context, ChannelType.WORKER.channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentTitle(context.getString(R.string.name))
            .setLocalOnly(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentText("Work in progress...")
            .build()
        return notification
    }

}