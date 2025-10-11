package de.michelinside.glucodatahandler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.michelinside.glucodatahandler.common.utils.Log
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.notification.AlarmHandler
import de.michelinside.glucodatahandler.common.utils.Utils

class SnoozeAlarmReceiverWear: BroadcastReceiver() {
    private val LOG_ID = "GDH.AlarmSnoozeReceiver"
    override fun onReceive(context: Context, intent: Intent) {
        try {
            Log.i(LOG_ID, "Intent ${intent.action} received: ${Utils.dumpBundle(intent.extras)}" )
            if (intent.hasExtra(Constants.ALARM_SNOOZE_EXTRA_NOTIFY_ID))
                AlarmNotificationWear.stopNotification(intent.getIntExtra(Constants.ALARM_SNOOZE_EXTRA_NOTIFY_ID, 0), context)
            if (intent.hasExtra(Constants.ALARM_SNOOZE_EXTRA_TIME))
                AlarmHandler.setSnooze(intent.getLongExtra(Constants.ALARM_SNOOZE_EXTRA_TIME, 0L))
            if(intent.hasExtra(Constants.ALARM_SNOOZE_EXTRA_START_APP) && intent.getBooleanExtra(Constants.ALARM_SNOOZE_EXTRA_START_APP, true)) {
                val startIntent = Intent(context, WearActivity::class.java)
                startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(startIntent)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onReceive exception: " + exc.toString() )
        }
    }

}