package de.michelinside.glucodatahandler.common.tasks

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.utils.Utils
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.WakeLockHelper
import java.math.RoundingMode
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

abstract class BackgroundTaskService(val alarmReqId: Int, protected val LOG_ID: String, protected val initialExecution: Boolean = false): SharedPreferences.OnSharedPreferenceChangeListener,
    NotifierInterface {
    private val DEFAULT_DELAY_MS = 3000L

    private var backgroundTaskList = mutableListOf<BackgroundTask>()
    private var curInterval = -1L
    private var curDelay = -1L
    private var context: Context? = null
    private lateinit var sharedPref: SharedPreferences
    private var pendingIntent: PendingIntent? = null
    private var alarmManager: AlarmManager? = null
    private var lastElapsedMinute = 0L
    private var isRunning: Boolean = false
    private var currentAlarmTime = 0L
    private val elapsedTimeMinute: Long
        get() {
            return ReceiveData.getElapsedTimeMinute()
        }

    private val elapsedIobCobTimeMinute: Long
        get() {
            return ReceiveData.getElapsedIobCobTimeMinute()
        }

    private var useWorkerVar = false
    var useWorker: Boolean
        get() = useWorkerVar
        set(value) {useWorkerVar = value}

    open fun hasIobCobSupport() = false
    abstract fun getBackgroundTasks(): MutableList<BackgroundTask>

    private fun initBackgroundTasks() {
        backgroundTaskList = getBackgroundTasks()

        backgroundTaskList.forEach {
            it.checkPreferenceChanged(sharedPref, null, context!!)
        }
    }

    fun checkExecution(task: BackgroundTask? = null): Boolean {
        if(task != null) {
            Log.v(LOG_ID, "checkExecution for " + task.javaClass.simpleName + ": elapsedTimeMinute=" + elapsedTimeMinute
                    + " - lastElapsedMinute=" + lastElapsedMinute
                    + " - elapsedIobCobTimeMinute=" + elapsedIobCobTimeMinute
                    + " - interval=" + task.getIntervalMinute()
                    + " - active=" + task.active(elapsedTimeMinute))
            if(task.active(elapsedTimeMinute)) {
                if (elapsedTimeMinute != 0L) {
                    if (lastElapsedMinute < 0 && initialExecution) {
                        Log.v(LOG_ID, "Trigger initial task execution")
                        return true   // trigger initial execution
                    }
                    if (elapsedTimeMinute.mod(task.getIntervalMinute()) == 0L) {
                        Log.v(LOG_ID, "Trigger "+ task.javaClass.simpleName + " execution after " + elapsedTimeMinute + " min")
                        return true   // interval expired for active task
                    }
                }
                if (task.hasIobCobSupport()) {
                    if (elapsedIobCobTimeMinute >= task.getIntervalMinute()) {
                        Log.v(LOG_ID, "Trigger " + task.javaClass.simpleName + " IOB/COB execution after " + elapsedIobCobTimeMinute + " min")
                        return true   // IOB/COB interval expired for active task
                    }
                }
            }
        } else {
            Log.v(LOG_ID,"checkExecution: " + "elapsedTimeMinute=" + elapsedTimeMinute
                    + " - lastElapsedMinute=" + lastElapsedMinute
                    + " - elapsedIobCobTimeMinute=" + elapsedIobCobTimeMinute)
            if ((lastElapsedMinute != elapsedTimeMinute && elapsedTimeMinute != 0L)) {
                Log.v(LOG_ID, "Check task execution after " + elapsedTimeMinute + " min")
                return true   // time expired and no new value
            }
            if (hasIobCobSupport() && elapsedIobCobTimeMinute > 0) {
                Log.v(LOG_ID, "Check IOB/COB task execution after " + elapsedIobCobTimeMinute + " min")
                return true // check each task for additional IOB COB data
            }
        }
        // nothing to execute
        if (task != null) {
            Log.v(LOG_ID, "nothing to execute for " + task.javaClass.simpleName)
        } else {
            Log.v(LOG_ID, "nothing to execute")
        }
        return false
    }

    private fun executeTasks(force: Boolean = false) {
        Log.v(LOG_ID, "executeTasks called with force: " + force)
        try {
            if (force || checkExecution()) {
                Thread {
                    WakeLockHelper(context!!).use {
                        try {
                            isRunning = true
                            backgroundTaskList.forEach {
                                if ((force && it.active(elapsedTimeMinute)) || checkExecution(it)) {
                                    try {
                                        Log.i(LOG_ID,"execute after " + elapsedTimeMinute + " min: " + it.javaClass.simpleName)
                                        it.execute(context!!)
                                    } catch (ex: Exception) {
                                        Log.e(LOG_ID,"exception while execute task " + it.javaClass.simpleName + ": " + ex)
                                    }
                                }
                            }
                        } catch (ex: Exception) {
                            Log.e(LOG_ID, "exception while executing tasks: " + ex)
                        }
                        // restart timer at the end, for the case a new value has been received
                        startTimer()
                        isRunning = false
                    }
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

    private fun active(elapsedTime: Long) : Boolean {
        Log.v(LOG_ID, "check active after elapsed time " + elapsedTime)
        backgroundTaskList.forEach {
            if (it.active(elapsedTime))
                return true
        }
        Log.i(LOG_ID, "Not active for elapsed time " + elapsedTime)
        return false
    }

    private fun getDelay(): Long {
        var delayResult = DEFAULT_DELAY_MS
        backgroundTaskList.forEach {
            if (it.active(elapsedTimeMinute) && it.getDelayMs() > delayResult)
                delayResult = it.getDelayMs()
        }
        return delayResult
    }

    private fun checkTimer() {
        try {
            val newInterval = getInterval()
            val newDelay = getDelay()
            if (initialExecution || curInterval != newInterval || curDelay != newDelay) {
                Log.i(LOG_ID, "Interval has changed from " + curInterval + "m+" + curDelay + "ms to " + newInterval + "m+" + newDelay + "ms")
                var triggerExecute = initialExecution || (curInterval <= 0 && newInterval > 0)  // changed from inactive to active so trigger an initial execution
                if (!triggerExecute && curInterval > newInterval && elapsedTimeMinute >= newInterval) {
                    // interval get decreased, so check for execution is needed
                    triggerExecute = true
                }
                curInterval = newInterval
                curDelay = newDelay
                if(triggerExecute && initialExecution && elapsedTimeMinute >= newInterval) {
                    Log.i(LOG_ID, "Trigger initial execution")
                    executeTasks(true)
                } else if (curInterval > 0) {
                    lastElapsedMinute = 0L
                    startTimer()
                }
            }
        } catch (ex: Exception) {
            Log.e(LOG_ID, "calculateInterval: " + ex)
        }
    }

    private fun getNextAlarm(): Calendar? {
        curInterval = getInterval() // always update the interval while executing
        if (curInterval > 0L) {
            val elapsedTimeMin = Utils.round((System.currentTimeMillis() - ReceiveData.time).toFloat()/60000, 0, RoundingMode.DOWN).toLong()
            val nextTriggerMin = (elapsedTimeMin/curInterval)*curInterval + curInterval
            Log.d(LOG_ID, "elapsed: " + elapsedTimeMin + " nextTrigger: " + nextTriggerMin + " - interval: "+ curInterval)
            if (active(nextTriggerMin)) {
                val nextAlarmCal = Calendar.getInstance()
                nextAlarmCal.timeInMillis = ReceiveData.time + TimeUnit.MINUTES.toMillis(nextTriggerMin) + getDelay()
                if (nextAlarmCal.get(Calendar.SECOND) < TimeUnit.MILLISECONDS.toSeconds(DEFAULT_DELAY_MS)) {
                    nextAlarmCal.add(Calendar.SECOND, -1*(nextAlarmCal.get(Calendar.SECOND)+1))
                }
                if (currentAlarmTime != nextAlarmCal.timeInMillis) {
                    Log.i(LOG_ID, "Set next alarm after " + nextTriggerMin + " minute(s) at " + DateFormat.getTimeInstance(DateFormat.DEFAULT).format(nextAlarmCal.time)
                          + " (received at " + DateFormat.getTimeInstance(DateFormat.DEFAULT).format(Date(ReceiveData.time)) + ") with a delay of " + getDelay()/1000 + "s")
                }
                return nextAlarmCal
            }
        }
        Log.d(LOG_ID, "No next alarm set for current interval " + curInterval)
        return null
    }

    abstract fun getAlarmReceiver() : Class<*>
    private fun init() {
        if (pendingIntent == null) {
            Log.v(LOG_ID, "init pendingIntent")
            val i = Intent(context, getAlarmReceiver())
            pendingIntent = PendingIntent.getBroadcast(
                context,
                alarmReqId,
                i,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
            )
        }
        if (alarmManager == null) {
            Log.v(LOG_ID, "init alarmManager")
            alarmManager = context!!.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            currentAlarmTime = 0L
        }
    }

    private fun startTimer() {
        Log.v(LOG_ID, "startTimer called")
        val nextAlarm = getNextAlarm()
        if (nextAlarm != null) {
            init()
            if (alarmManager != null) {
                if (currentAlarmTime != nextAlarm.timeInMillis) {
                    currentAlarmTime = nextAlarm.timeInMillis
                    lastElapsedMinute = elapsedTimeMinute
                    alarmManager!!.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextAlarm.timeInMillis,
                        pendingIntent!!
                    )
                } else {
                    Log.d(LOG_ID, "Ignore next alarm as it is already active")
                }
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
            Log.v(LOG_ID, "stopTimer called")
            alarmManager!!.cancel(pendingIntent!!)
            alarmManager = null
            currentAlarmTime = 0L
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        try {
            if (sharedPreferences != null) {
                    Log.v(LOG_ID, "onSharedPreferenceChanged called for " + key)
                    var changed = false
                    backgroundTaskList.forEach {
                        if (it.checkPreferenceChanged(sharedPreferences, key, context!!))
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
            Log.v(LOG_ID, "OnNotifyData for source " + dataSource.toString())
            if (!isRunning) {  // check only if for not already in execution
                if(mutableSetOf(NotifySource.BROADCAST, NotifySource.MESSAGECLIENT).contains(dataSource))
                    executeTasks()    // check for additional IOB - COB content or restart time
                else
                    startTimer()  // check for restart
            }
        } catch (ex: Exception) {
            Log.e(LOG_ID, "OnNotifyData: " + ex)
        }
    }

    open fun getNotifySourceFilter() : MutableSet<NotifySource> = mutableSetOf()

    fun run(set_context: Context) {
        try {
            context = set_context
            sharedPref = context!!.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            sharedPref.registerOnSharedPreferenceChangeListener(this)
            val filter = mutableSetOf(NotifySource.BROADCAST, NotifySource.MESSAGECLIENT)
            filter.addAll(getNotifySourceFilter())
            InternalNotifier.addNotifier(
                context!!, this,
                filter
            )
            initBackgroundTasks()
            checkTimer()  // this will start the time
        } catch (ex: Exception) {
            Log.e(LOG_ID, "run: " + ex)
        }
    }

    fun stop() {
        try {
            if (context != null) {
                InternalNotifier.remNotifier(context!!, this)
                sharedPref.unregisterOnSharedPreferenceChangeListener(this)
                stopTimer()
            }
        } catch (ex: Exception) {
            Log.e(LOG_ID, "run: " + ex)
        }
    }

    fun alarmTrigger() {
        try {
            Log.v(LOG_ID, "alarmTrigger called")
            if (active(elapsedTimeMinute)) {
                executeTasks()
            }
        } catch (ex: Exception) {
            Log.e(LOG_ID, "alarmTrigger: " + ex)
        }
    }
}

