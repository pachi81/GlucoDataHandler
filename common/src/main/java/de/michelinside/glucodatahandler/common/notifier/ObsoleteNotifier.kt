package de.michelinside.glucodatahandler.common.notifier

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.ReceiveData
import java.text.DateFormat
import java.util.*


class ObsoleteNotifier: BroadcastReceiver() {
    private val LOG_ID = "GlucoDataHandler.ObsoleteNotifier"
    private val timeformat = DateFormat.getTimeInstance(DateFormat.DEFAULT)
    @SuppressLint("InvalidWakeLockTag")
    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(LOG_ID, "onReceive: " + intent.toString())
        notify(context)
        // re-schedule, if needed
        schedule(context)
    }

    private fun notify(context: Context) {
        InternalNotifier.notify(context, NotifyDataSource.OBSOLETE_VALUE, null)
    }

    private fun getNotifyDelay(): Long {
        if (!ReceiveData.isObsolete()) {
            val delayTimeSec = if (ReceiveData.isObsolete(Constants.VALUE_OBSOLETE_SHORT_SEC)) Constants.VALUE_OBSOLETE_LONG_SEC else Constants.VALUE_OBSOLETE_SHORT_SEC
            return (delayTimeSec * 1000) - (System.currentTimeMillis()- ReceiveData.time) + 100
        }
        return 0L
    }

    fun schedule(context: Context) {
        val delayMs = getNotifyDelay()
        if (delayMs > 0) {
            val timeMs = System.currentTimeMillis()+delayMs
            Log.d(LOG_ID, "schedule obsolete notification in " + delayMs.toString() + "ms at " + timeformat.format(
                Date(timeMs)
            ))
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val i = Intent(context, ObsoleteNotifier::class.java)
            val pi = PendingIntent.getBroadcast(context, 42, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT)
            am.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                timeMs,
                pi
            )
        }
    }

    fun cancel(context: Context) {
        Log.d(LOG_ID, "cancelAlarm")
        val intent = Intent(context, ObsoleteNotifier::class.java)
        val sender = PendingIntent.getBroadcast(context, 42, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(sender)
    }
}