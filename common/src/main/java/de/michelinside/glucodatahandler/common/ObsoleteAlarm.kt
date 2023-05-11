package de.michelinside.glucodatahandler.common

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifyDataSource
import java.util.*


class ObsoleteAlarm: BroadcastReceiver() {
    private val LOG_ID = "GlucoDataHandler.ObsoleteAlarm"
    @SuppressLint("InvalidWakeLockTag")
    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(LOG_ID, "onReceive: " + intent.toString())
        InternalNotifier.notify(context, NotifyDataSource.OBSOLETE_VALUE, null)
        if (!ReceiveData.isObsolete()) {
            val delayTime = (Constants.VALUE_OBSOLETE_LONG_SEC * 1000) - (System.currentTimeMillis()- ReceiveData.time) + 100
            if (delayTime > 0) {
                setAlarm(context, delayTime)
            }
        }
    }

    fun setAlarm(context: Context, delayMs: Long) {
        if (delayMs > 0) {
            val timeMs = System.currentTimeMillis()+delayMs
            Log.d(LOG_ID, "setAlarm in " + delayMs.toString() + "ms at " + ReceiveData.timeformat.format(
                Date(timeMs)
            ))
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val i = Intent(context, ObsoleteAlarm::class.java)
            val pi = PendingIntent.getBroadcast(context, 42, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT)
            am.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                timeMs,
                pi
            )
        }
    }

    fun cancelAlarm(context: Context) {
        Log.d(LOG_ID, "cancelAlarm")
        val intent = Intent(context, ObsoleteAlarm::class.java)
        val sender = PendingIntent.getBroadcast(context, 42, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(sender)
    }
}