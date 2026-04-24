package de.michelinside.glucodatahandler.common.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Context.ALARM_SERVICE
import android.content.Intent
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService.Companion.foreground
import de.michelinside.glucodatahandler.common.utils.Log
import de.michelinside.glucodatahandler.common.utils.Utils

object StartupTrigger {
    private const val LOG_ID = "GDH.srv.StartupTrigger"

    private var alarmManager: AlarmManager? = null
    private var alarmPendingIntent: PendingIntent? = null

    fun stopTrigger() {
        try {
            if(alarmManager != null && alarmPendingIntent != null) {
                Log.i(LOG_ID, "Stop trigger")
                alarmManager!!.cancel(alarmPendingIntent!!)
                alarmManager = null
                alarmPendingIntent = null
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "stopTrigger exception: " + exc.message.toString())
        }
    }

    fun triggerStartService(context: Context, receiver: Class<*>) {
        try {
            Log.i(LOG_ID, "Trigger start service - foreground: $foreground - alarm active: ${alarmManager != null && alarmPendingIntent != null}")
            if(foreground || (alarmManager != null && alarmPendingIntent != null))
                return
            alarmManager = context.getSystemService(ALARM_SERVICE) as AlarmManager
            var hasExactAlarmPermission = true
            if (!Utils.canScheduleExactAlarms(context)) {
                Log.d(LOG_ID, "Need permission to set exact alarm!")
                hasExactAlarmPermission = false
            }
            val intent = Intent(context, receiver)
            intent.action = Constants.ACTION_START_FOREGROUND
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            alarmPendingIntent = PendingIntent.getBroadcast(
                context,
                911,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
            )
            val alarmTime = System.currentTimeMillis() + 1000
            Log.i(LOG_ID, "Trigger alarm at ${Utils.getUiTimeStamp(alarmTime)} - exactAlarm: $hasExactAlarmPermission")
            if (hasExactAlarmPermission) {
                alarmManager!!.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    alarmTime,
                    alarmPendingIntent!!
                )
            } else {
                alarmManager!!.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    alarmTime,
                    alarmPendingIntent!!
                )
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "triggerStartService exception: " + exc.message.toString())
            stopTrigger()
        }
    }
}