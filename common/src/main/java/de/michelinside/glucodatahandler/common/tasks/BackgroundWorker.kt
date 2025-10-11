package de.michelinside.glucodatahandler.common.tasks

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import de.michelinside.glucodatahandler.common.utils.Log
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
import de.michelinside.glucodatahandler.common.utils.Utils
import java.time.Duration

abstract class BackgroundWorker(val context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    companion object {
        private val LOG_ID = "GDH.Task.BackgroundWorker"
        fun triggerWork(context: Context, taskServiceClass: Class<out BackgroundWorker>) {
            Log.v(LOG_ID, "triggerWork called for class " + taskServiceClass.simpleName)
            val builder = OneTimeWorkRequest.Builder(taskServiceClass)
                .setConstraints(
                    Constraints.Builder()
                        .setTriggerContentMaxDelay(Duration.ofMillis(100))
                        .build()
                ).setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)

            val workManager = WorkManager.getInstance(context)
            workManager.enqueue(builder.build())
        }

        fun triggerDelay(context: Context, taskServiceClass: Class<out BackgroundWorker>, delaySec: Long) {
            stopWork(context, taskServiceClass)
            Log.d(LOG_ID, "triggerDelay called for class ${taskServiceClass.simpleName} with delay ${delaySec}s at ${Utils.getUiTimeStamp(System.currentTimeMillis() + delaySec*1000)}")
            val builder = OneTimeWorkRequest.Builder(taskServiceClass)
                .setConstraints(
                    Constraints.Builder()
                        .setTriggerContentMaxDelay(Duration.ofMillis(100))
                        .build()
                )
                .setInitialDelay(delaySec, java.util.concurrent.TimeUnit.SECONDS)
                .addTag(taskServiceClass.simpleName)

            val workManager = WorkManager.getInstance(context)
            workManager.enqueue(builder.build())
        }

        fun stopWork(context: Context, taskServiceClass: Class<out BackgroundWorker>) {
            Log.d(LOG_ID, "stopWork called for class " + taskServiceClass.simpleName)
            val workManager = WorkManager.getInstance(context)
            workManager.cancelAllWorkByTag(taskServiceClass.simpleName)
        }

        fun stopAllWork(context: Context) {
            val workManager = WorkManager.getInstance(context)
            workManager.cancelAllWork()
            TimeTaskService.stop()
            SourceTaskService.stop()
        }

        fun checkServiceRunning(context: Context) {
            // watchdog to check, if all services are running
            if(!TimeTaskService.checkRunning()) {
                Log.e(LOG_ID, "TimeTaskService not running! Restart it")
                TimeTaskService.stop()
                TimeTaskService.run(context)
            }
            if(!SourceTaskService.checkRunning()) {
                Log.e(LOG_ID, "SourceTaskService not running! Restart it")
                SourceTaskService.stop()
                SourceTaskService.run(context)
            }
        }
    }

    abstract fun execute(context: Context)

    override fun doWork(): Result {
        Log.i(LOG_ID, "doWork called for ${javaClass.simpleName}")
        try {
            execute(context)
        } catch (ex: Exception) {
            Log.e(LOG_ID, "doWork: " + ex)
        }
        Log.d(LOG_ID, "doWork finished for ${javaClass.simpleName}")
        return Result.success()
    }

    override fun onStopped() {
        Log.i(LOG_ID, "onStopped called for ${javaClass.simpleName}")
    }

    override fun getForegroundInfo(): ForegroundInfo {
        Log.d(LOG_ID, "getForegroundInfo called for ${javaClass.simpleName}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return ForegroundInfo(GlucoDataService.NOTIFICATION_ID, getNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
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