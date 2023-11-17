package de.michelinside.glucodatahandler.common.tasks

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.Utils
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import java.math.RoundingMode
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

abstract class BackgroundTaskService(val alarmReqId: Int, protected val LOG_ID: String, protected val initialExecution: Boolean = false): SharedPreferences.OnSharedPreferenceChangeListener,
    NotifierInterface {
    private val DEFAULT_DELAY_MS = 3000L
    private val WAKE_LOCK_TIMEOUT = 10000L // 10 seconds

    private var backgroundTaskList = mutableListOf<BackgroundTask>()
    private var curInterval = -1L
    private var curDelay = -1L
    private lateinit var context: Context
    private lateinit var sharedPref: SharedPreferences
    private var pendingIntent: PendingIntent? = null
    private var alarmManager: AlarmManager? = null
    private var lastElapsedMinute = 0L
    private val elapsedTimeMinute: Long
        get() {
            return ReceiveData.getElapsedTimeMinute()
        }

    abstract fun getBackgroundTasks(): MutableList<BackgroundTask>

    private fun initBackgroundTasks() {
        backgroundTaskList = getBackgroundTasks()

        backgroundTaskList.forEach {
            it.checkPreferenceChanged(sharedPref, null, context)
        }
    }

    private fun executeTasks() {
        try {
            if (lastElapsedMinute != elapsedTimeMinute && elapsedTimeMinute != 0L) {
                Thread {
                    val wakeLock: PowerManager.WakeLock =
                        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GlucoDataHandler::BackgroundTaskTag").apply {
                                acquire(WAKE_LOCK_TIMEOUT)
                            }
                        }
                    try {
                        backgroundTaskList.forEach {
                            if (elapsedTimeMinute != 0L && ((lastElapsedMinute < 0 && initialExecution) || (elapsedTimeMinute.mod(it.getIntervalMinute()) == 0L && it.active(elapsedTimeMinute)))) {
                                try {
                                    Log.i(LOG_ID, "execute after " + elapsedTimeMinute + " min: " + it)
                                    it.execute(context)
                                } catch (ex: Exception) {
                                    Log.e(LOG_ID, "exception while execute task " + it + ": " + ex)
                                }
                            }
                        }
                    } catch (ex: Exception) {
                        Log.e(LOG_ID, "exception while executing tasks: " + ex)
                    } finally {
                        wakeLock.release()
                    }
                    // restart timer at the end, for the case a new value has been received
                    startTimer()
                }.start()
            } else {
                // restart timer
                startTimer()
            }
        } catch (ex: Exception) {
            Log.e(LOG_ID, "executeTasks: " + ex)
        }
    }

    private fun getInterval(): Long {
        var newInterval = -1L
        backgroundTaskList.forEach {
            if (it.active(elapsedTimeMinute) && newInterval <= 0L || it.getIntervalMinute() < newInterval)
                newInterval = it.getIntervalMinute()
        }
        return newInterval
    }

    private fun checkTimer() {
        try {
            val newInterval = getInterval()
            val newDelay = getDelay()
            if (curInterval != newInterval || curDelay != newDelay) {
                Log.i(LOG_ID, "Interval has changed from " + curInterval + "m+" + curDelay + "s to " + newInterval + "m+" + newDelay + "s")
                val triggerExecute = curInterval <= 0 && newInterval > 0  // changed from inactive to active so trigger an initial execution
                curInterval = newInterval
                curDelay = newDelay
                if(triggerExecute && initialExecution) {
                    Log.i(LOG_ID, "Trigger initial execution")
                    lastElapsedMinute = -1L
                    executeTasks()
                } else if (curInterval > 0) {
                    lastElapsedMinute = 0L
                    startTimer()
                }
            }
        } catch (ex: Exception) {
            Log.e(LOG_ID, "calculateInterval: " + ex)
        }
    }

    private fun active(elapsedTime: Long) : Boolean {
        backgroundTaskList.forEach {
            if (it.active(elapsedTime))
                return true
        }
        Log.i(LOG_ID, "Not active for elapsed time " + elapsedTime)
        return false
    }

    open fun getDelay(): Long = 0L

    private val useDefaultDelay: Boolean get() = (getDelay()*1000 <= DEFAULT_DELAY_MS)

    private fun getDelayResult(): Long {
        if(useDefaultDelay)
            return DEFAULT_DELAY_MS
        return getDelay()*1000
    }

    private fun getNextAlarm(): Calendar? {
        curInterval = getInterval() // always update the interval while executing
        if (curInterval > 0L) {
            val elapsedTimeMin = Utils.round((System.currentTimeMillis() - ReceiveData.time).toFloat()/60000, 0, RoundingMode.DOWN).toLong()
            val nextTriggerMin = (elapsedTimeMin/curInterval)*curInterval + curInterval
            Log.d(LOG_ID, "elapsed: " + elapsedTimeMin + " nextTrigger: " + nextTriggerMin + " - interval: "+ curInterval)
            if (active(nextTriggerMin)) {
                val nextAlarmCal = Calendar.getInstance()
                nextAlarmCal.timeInMillis = ReceiveData.time + TimeUnit.MINUTES.toMillis(nextTriggerMin) + getDelayResult()
                if (nextAlarmCal.get(Calendar.SECOND) < TimeUnit.MILLISECONDS.toSeconds(DEFAULT_DELAY_MS)) {
                    nextAlarmCal.add(Calendar.SECOND, -1*(nextAlarmCal.get(Calendar.SECOND)+1))
                }
                Log.i(LOG_ID, "Set next alarm after " + nextTriggerMin + " minute(s) at " + DateFormat.getTimeInstance(DateFormat.DEFAULT).format(nextAlarmCal.time)
                      + " (received at " + DateFormat.getTimeInstance(DateFormat.DEFAULT).format(Date(ReceiveData.time)) + ") with a delay of " + getDelayResult()/1000 + "s")
                return nextAlarmCal
            }
        }
        Log.d(LOG_ID, "No next alarm set for current interval " + curInterval)
        return null
    }

    abstract fun getAlarmReceiver() : Class<*>
    private fun init() {
        if (pendingIntent == null) {
            Log.d(LOG_ID, "init pendingIntent")
            val i = Intent(context, getAlarmReceiver())
            pendingIntent = PendingIntent.getBroadcast(
                context,
                alarmReqId,
                i,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
            )
        }
        if (alarmManager == null) {
            Log.d(LOG_ID, "init alarmManager")
            alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        }
    }

    private fun startTimer() {
        Log.d(LOG_ID, "startTimer called")
        val nextAlarm = getNextAlarm()
        if (nextAlarm != null) {
            init()
            if (alarmManager != null) {
                lastElapsedMinute = elapsedTimeMinute
                alarmManager!!.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextAlarm.timeInMillis,
                    pendingIntent!!
                )
                return
            }
        }
        // else
        if (alarmManager != null) {
            stopTimer()
        }
    }

    private fun stopTimer() {
        if (alarmManager != null && pendingIntent != null) {
            Log.d(LOG_ID, "stopTimer called")
            alarmManager!!.cancel(pendingIntent!!)
            alarmManager = null
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        try {
            if (sharedPreferences != null) {
                    Log.d(LOG_ID, "onSharedPreferenceChanged called for " + key)
                    var changed = false
                    backgroundTaskList.forEach {
                        if (it.checkPreferenceChanged(sharedPreferences, key, context))
                            changed = true
                    }
                    if (changed) {
                        checkTimer()
                    }
                }
        } catch (ex: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged: " + ex)
        }
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            Log.d(LOG_ID, "OnNotifyData for source " + dataSource.toString())
            // restart time
            startTimer()
        } catch (ex: Exception) {
            Log.e(LOG_ID, "OnNotifyData: " + ex)
        }
    }

    fun run(set_context: Context) {
        try {
            context = set_context
            sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            sharedPref.registerOnSharedPreferenceChangeListener(this)
            InternalNotifier.addNotifier(
                this,
                mutableSetOf(NotifySource.BROADCAST, NotifySource.MESSAGECLIENT)
            )
            initBackgroundTasks()
            checkTimer()  // this will start the time
        } catch (ex: Exception) {
            Log.e(LOG_ID, "run: " + ex)
        }
    }

    fun stop() {
        try {
            InternalNotifier.remNotifier(this)
            sharedPref.unregisterOnSharedPreferenceChangeListener(this)
            stopTimer()
        } catch (ex: Exception) {
            Log.e(LOG_ID, "run: " + ex)
        }
    }

    fun alarmTrigger(intent: Intent?) {
        try {
            Log.d(LOG_ID, "onReceive: " + intent.toString())
            if (active(elapsedTimeMinute)) {
                executeTasks()
            }
        } catch (ex: Exception) {
            Log.e(LOG_ID, "onReceive: " + ex)
        }
    }
}

