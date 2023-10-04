package de.michelinside.glucodatahandler.common.notifier

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import java.math.RoundingMode
import java.text.DateFormat
import java.util.*


class ElapsedTimeNotifier: BroadcastReceiver(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val LOG_ID = "GlucoDataHandler.ElapsedTimeNotifier"
    private val timeformat = DateFormat.getTimeInstance(DateFormat.DEFAULT)

    companion object {
        private var pendingIntent: PendingIntent? = null
        private var alarmManager: AlarmManager? = null
        private var nextObsoleteNotifySec = -1
        private var elapsedMinute = -1L
        private var init: Boolean = false
        var relativeTime = false
    }

    private val active: Boolean get() {
        if(relativeTime)
            return (ReceiveData.getElapsedTimeMinute(RoundingMode.DOWN) <= 60)
        else
            return nextObsoleteNotifySec > 0
    }

    private fun init(context: Context) {
        if (!init) {
            val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            sharedPref.registerOnSharedPreferenceChangeListener(this)
            onSharedPreferenceChanged(sharedPref,Constants.SHARED_PREF_RELATIVE_TIME)
            init = true
        }
        if (active) {
            if (pendingIntent == null) {
                Log.d(LOG_ID, "init pendingIntent")
                val i = Intent(context, ElapsedTimeNotifier::class.java)
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
            }
            elapsedMinute = ReceiveData.getElapsedTimeMinute(RoundingMode.DOWN)
            if (elapsedMinute < Constants.VALUE_OBSOLETE_SHORT_SEC/60)
                nextObsoleteNotifySec = Constants.VALUE_OBSOLETE_SHORT_SEC
            else if (elapsedMinute < Constants.VALUE_OBSOLETE_LONG_SEC/60)
                nextObsoleteNotifySec = Constants.VALUE_OBSOLETE_LONG_SEC
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        try {
            Log.d(LOG_ID, "onReceive: " + intent.toString())
            notify(context)
            // re-schedule, if needed
            startTimer(context)
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
            Log.i(LOG_ID, "notify after " + ReceiveData.getElapsedTimeMinute(RoundingMode.DOWN) + " minute")
            if (obsoleteNotify()) {
                Log.d(LOG_ID, "send obsolete notifier")
                InternalNotifier.notify(context, NotifyDataSource.OBSOLETE_VALUE, null)
            }
            if (relativeTime)
                InternalNotifier.notify(context, NotifyDataSource.TIME_VALUE, null)
            elapsedMinute = ReceiveData.getElapsedTimeMinute(RoundingMode.DOWN)
        }
    }

    private fun getTimeDelay(): Long {
        if (relativeTime) {
            return 60000L - (System.currentTimeMillis() - ReceiveData.time).mod(60000L) + 3000
        } else if (!ReceiveData.isObsolete()) {
            val delayTimeSec = if (ReceiveData.isObsolete(Constants.VALUE_OBSOLETE_SHORT_SEC)) Constants.VALUE_OBSOLETE_LONG_SEC else Constants.VALUE_OBSOLETE_SHORT_SEC
            return (delayTimeSec * 1000) - (System.currentTimeMillis()- ReceiveData.time) + 100
        }
        return 0L
    }

    private fun startTimer(context: Context) {
        Log.d(LOG_ID, "startTimer called")
        val delayMs = getTimeDelay()
        if (delayMs > 0 && alarmManager != null && active) {
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
                relativeTime = sharedPreferences.getBoolean(Constants.SHARED_PREF_RELATIVE_TIME, false)
                Log.d(LOG_ID, "relativeTime changed to " + relativeTime)
                if (GlucoDataService.context != null) {
                    InternalNotifier.notify(GlucoDataService.context!!, NotifyDataSource.TIME_VALUE, null)
                    if (alarmManager!=null)
                        startTimer(GlucoDataService.context!!)
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "cancel exception: " + exc.message.toString() )
        }
    }
}