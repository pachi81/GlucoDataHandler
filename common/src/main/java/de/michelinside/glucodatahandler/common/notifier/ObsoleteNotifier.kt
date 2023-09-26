package de.michelinside.glucodatahandler.common.notifier

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.util.Log
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import java.math.RoundingMode
import java.text.DateFormat
import java.util.*


class ObsoleteNotifier: BroadcastReceiver(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val LOG_ID = "GlucoDataHandler.ObsoleteNotifier"
    private val timeformat = DateFormat.getTimeInstance(DateFormat.DEFAULT)

    companion object {
        private var pendingIntent: PendingIntent? = null
        private var alarmManager: AlarmManager? = null
        private var nextObsoleteNotifySec = -1
        private var elapsedMinute = -1L
        private var triggerTimeValues = false
        private var init: Boolean = false
    }

    private val active: Boolean get() = (ReceiveData.getElapsedTimeMinute(RoundingMode.DOWN) <= 60)

    private fun init(context: Context) {
        if (!init) {
            val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            sharedPref.registerOnSharedPreferenceChangeListener(this)
            onSharedPreferenceChanged(sharedPref,Constants.SHARED_PREF_RELATIVE_TIME)
        }
        if (active) {
            if (pendingIntent == null) {
                Log.d(LOG_ID, "init pendingIntent")
                val i = Intent(context, ObsoleteNotifier::class.java)
                pendingIntent = PendingIntent.getBroadcast(
                    context,
                    42,
                    i,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
                )
            }
            if (alarmManager == null) {
                Log.d(LOG_ID, "init alarmManager")
                alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                context.registerReceiver(this, IntentFilter(Intent.ACTION_TIME_TICK))
            }
            elapsedMinute = ReceiveData.getElapsedTimeMinute(RoundingMode.DOWN)
            nextObsoleteNotifySec = Constants.VALUE_OBSOLETE_SHORT_SEC
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        try {
            Log.d(LOG_ID, "onReceive: " + intent.toString())
            notify(context)
            if (intent?.action != Intent.ACTION_TIME_TICK) {
                // re-schedule, if needed
                startTimer(context)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onReceive exception: " + exc.toString() )
        }
    }

    private fun obsoleteNotify() : Boolean {
        if (nextObsoleteNotifySec > 0 && ReceiveData.isObsolete(nextObsoleteNotifySec)) {
            if (nextObsoleteNotifySec != Constants.VALUE_OBSOLETE_LONG_SEC) {
                nextObsoleteNotifySec = Constants.VALUE_OBSOLETE_LONG_SEC
            } else {
                nextObsoleteNotifySec = -1
            }
            return true
        }
        return false
    }

    private fun notify(context: Context) {
        if (elapsedMinute != ReceiveData.getElapsedTimeMinute(RoundingMode.DOWN)) {
            Log.d(LOG_ID, "notify after " + ReceiveData.getElapsedTimeMinute(RoundingMode.DOWN) + " minute")
            if (obsoleteNotify()) {
                Log.d(LOG_ID, "send obsolete notifier")
                InternalNotifier.notify(context, NotifyDataSource.OBSOLETE_VALUE, null)
            }
            if (triggerTimeValues)
                InternalNotifier.notify(context, NotifyDataSource.TIME_VALUE, null)
            elapsedMinute = ReceiveData.getElapsedTimeMinute(RoundingMode.DOWN)
        }
    }

    private fun getTimeDelay(): Long {
        if (triggerTimeValues) {
            return 60000L - (System.currentTimeMillis() - ReceiveData.time).mod(60000L) + 100
        } else if (!ReceiveData.isObsolete()) {
            val delayTimeSec = if (ReceiveData.isObsolete(Constants.VALUE_OBSOLETE_SHORT_SEC)) Constants.VALUE_OBSOLETE_LONG_SEC else Constants.VALUE_OBSOLETE_SHORT_SEC
            return (delayTimeSec * 1000) - (System.currentTimeMillis()- ReceiveData.time) + 100
        }
        return 0L
    }

    private fun startTimer(context: Context) {
        val delayMs = getTimeDelay()
        if (delayMs > 0 && alarmManager != null && active) {
            Log.d(LOG_ID, "startTimer called")
            val timeMs = System.currentTimeMillis()+delayMs
            Log.d(LOG_ID, "schedule obsolete notification in " + delayMs.toString() + "ms at " + timeformat.format(
                Date(timeMs)
            ))
            alarmManager!!.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                timeMs,
                pendingIntent!!
            )
        } else if (alarmManager != null) {
            stopTimer(context)
        }
    }

    private fun stopTimer(context: Context) {
        if (alarmManager != null && pendingIntent != null) {
            Log.d(LOG_ID, "stopTimer called")
            alarmManager!!.cancel(pendingIntent!!)
            alarmManager = null
            context.unregisterReceiver(this)
        }
    }

    fun schedule(context: Context) {
        try {
            init(context)
            startTimer(context)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "schedule exception: " + exc.message.toString() )
        }
    }

    fun cancel(context: Context) {
        try {
            stopTimer(context)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "cancel exception: " + exc.message.toString() )
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        try {
            Log.d(LOG_ID, "onSharedPreferenceChanged called for " + key)
            if (sharedPreferences != null && key == Constants.SHARED_PREF_RELATIVE_TIME) {
                triggerTimeValues = sharedPreferences.getBoolean(Constants.SHARED_PREF_RELATIVE_TIME, false)
                InternalNotifier.notify(GlucoDataService.context!!, NotifyDataSource.TIME_VALUE, null)
                if (alarmManager!=null)
                    startTimer(GlucoDataService.context!!)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "cancel exception: " + exc.message.toString() )
        }
    }
}