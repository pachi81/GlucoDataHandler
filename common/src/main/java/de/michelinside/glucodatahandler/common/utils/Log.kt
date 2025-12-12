package de.michelinside.glucodatahandler.common.utils

import android.content.Context
import android.content.SharedPreferences
import de.michelinside.glucodatahandler.common.BuildConfig
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.database.LogEntry
import de.michelinside.glucodatahandler.common.database.dbAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.Collections
import java.util.Date
import java.util.Locale

val format = SimpleDateFormat("dd.MM HH:mm:ss.SSS", Locale.GERMAN)

object Log: SharedPreferences.OnSharedPreferenceChangeListener {
    private const val LOG_ID = "GDH.Log"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var minLevel = android.util.Log.DEBUG  // use initial DEBUG to get all DEBUG messages during startup
    private var logDuration = 60*60*1000 // 1h
    private var lastClearTime = 0L
    private var lastSaveTime = 0L
    private val logBuffer = Collections.synchronizedList(mutableListOf<LogEntry>())
    private val maxLogBufferSize: Int get() {
        if(minLevel <= android.util.Log.DEBUG)
            return 50
        if(dbLoggingEnabled)
            return 30
        return 5 // user logs only
    }

    private val clearLogTimeMinute: Int get() {
        if(minLevel <= android.util.Log.DEBUG)
            return 10
        return 15
    }

    val dbLoggingEnabled: Boolean get() {
        return logDuration > 0
    }

    fun getPriorityString(priority: Int): String {
        return when (priority) {
            android.util.Log.VERBOSE -> "V"
            android.util.Log.DEBUG -> "D"
            android.util.Log.INFO -> "I"
            android.util.Log.WARN -> "W"
            android.util.Log.ERROR -> "E"
            else -> priority.toString()
        }
    }

    fun init(context: Context) {
        val sharedPreferences = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        onSharedPreferenceChanged(sharedPreferences, null)
        i(LOG_ID, "Logs init: logDuration=${Duration.ofMillis(logDuration.toLong()).toHours()}h - minLevel=${getPriorityString(minLevel)}")
    }

    fun close(context: Context) {
        i(LOG_ID, "close called")
        val sharedPreferences = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        flushLogBuffer(wait = true)
    }

    fun v(tag: String, msg: String): Int {
        if (BuildConfig.DEBUG) {
            return android.util.Log.v(tag, msg)
        }
        return 0
    }

    fun d(tag: String, msg: String): Int {
        logToDB(android.util.Log.DEBUG, tag, msg)
        if(!Constants.RELEASE)
            return android.util.Log.d(tag, msg)
        return 0
    }

    fun i(tag: String, msg: String): Int {
        logToDB(android.util.Log.INFO, tag, msg)
        return android.util.Log.i(tag, msg)
    }

    fun w(tag: String, msg: String): Int {
        logToDB(android.util.Log.WARN, tag, msg)
        return android.util.Log.w(tag, msg)
    }

    fun e(tag: String, msg: String): Int {
        logToDB(android.util.Log.ERROR, tag, msg)
        return android.util.Log.e(tag, msg)
    }

    fun e(tag: String, msg: String, throwable: Throwable?): Int {
        logToDB(android.util.Log.ERROR, tag, msg)
        return android.util.Log.e(tag, msg, throwable)
    }

    fun isLoggable(tag: String, priority: Int): Boolean {
        if(dbLoggingEnabled && priority >= minLevel)
            return true
        if(Constants.RELEASE && priority <= android.util.Log.DEBUG)
            return false
        if(priority < android.util.Log.DEBUG)
            return BuildConfig.DEBUG
        return android.util.Log.isLoggable(tag, priority)
    }

    private fun logToDB(priority: Int, tag: String, msg: String) {
        try {
            if(dbLoggingEnabled && priority >= minLevel) {
                saveLog(LogEntry(priority, tag, msg))
            }
        } catch (exc: Exception) {
            android.util.Log.e(LOG_ID, "Error while logging", exc)
        }
    }

    private fun saveLog(logEntry: LogEntry) {
        scope.launch {
            try {
                logBuffer.add(logEntry)
                if (logBuffer.size >= maxLogBufferSize || (System.currentTimeMillis() - lastSaveTime) >= 60000) {
                    flushLogBuffer()
                }
            } catch (exc: Exception) {
                android.util.Log.e(LOG_ID, "Error while saving log entry", exc)
            }
        }
    }

    fun flushLogBuffer(wait: Boolean = false) {
        if(!dbAccess.active) {
            // clear logs to prevent filling buffer with old entries
            logBuffer.clear()
            return  // wait for database is ready
        }
        val entriesToSave = synchronized(logBuffer) {
            if (logBuffer.isEmpty()) {
                return@synchronized null
            }
            val entries = ArrayList(logBuffer)
            logBuffer.clear()
            entries
        }

        if (entriesToSave != null) {
            v(LOG_ID, "Flushing ${entriesToSave.size} log entries to database... wait=$wait")
            val job = dbAccess.addLogs(entriesToSave)
            if (wait && job != null) {
                runBlocking {
                    d(LOG_ID, "Waiting for ${entriesToSave.size} log entries to be flushed...")
                    job.join()
                    d(LOG_ID, "Flushed ${entriesToSave.size} log entries to database.")
                }
            }
            lastSaveTime = System.currentTimeMillis()
            if(!wait) {
                d(LOG_ID, "Flushed ${entriesToSave.size} log entries to database asynchronously.")
            }
            clearLogs()
        }
    }

    private fun clearLogs(force: Boolean = false) {
        try {
            if(!dbAccess.active)
                return  // wait for database is ready
            v(LOG_ID, "Check clearing logs - force=$force - lastClearTime=${Utils.getUiTimeStamp(lastClearTime)} - logDuration=$logDuration - time elapsed: ${Utils.getElapsedTimeMinute(lastClearTime)}")
            if(logDuration <= 0) {
                if(force) {
                    dbAccess.deleteAllLogs()
                    logBuffer.clear()
                }
                return
            }
            if(force || (Utils.getElapsedTimeMinute(lastClearTime) >= clearLogTimeMinute)) {  // remove old entries every 15 minutes
                d(LOG_ID, "Clearing logs - force=$force - lastClearTime=${Utils.getUiTimeStamp(lastClearTime)} - logDuration=${Duration.ofMillis(logDuration.toLong()).toHours()}h")
                lastClearTime = System.currentTimeMillis()
                val removeTime = System.currentTimeMillis() - logDuration
                dbAccess.deleteOldLogs(removeTime)

                if(minLevel == android.util.Log.DEBUG) {
                    val removeTime = System.currentTimeMillis() - 30*60*1000 // remove old debug entries after 30 minutes
                    dbAccess.deleteOldDebugLogs(removeTime)
                } else if(force) {
                    dbAccess.deleteOldDebugLogs(System.currentTimeMillis())
                }
            }
        } catch (exc: Exception) {
            android.util.Log.e(LOG_ID, "Error while logging", exc)
        }
    }

    private fun toString(log: LogEntry): String {
        return "${format.format(Date(log.timestamp))} ${log.pid.toString().padStart(5)} ${log.tid.toString().padStart(5)} ${getPriorityString(log.priority)} ${log.tag}: ${log.msg}"
    }

    fun getLogs(): String {
        val entries = if(dbAccess.active) {
                flushLogBuffer(true)
                dbAccess.getLogs()
            } else {
                synchronized(logBuffer) {
                    if (logBuffer.isEmpty()) {
                        return@synchronized emptyList()
                    }
                    val entries = ArrayList(logBuffer)
                    logBuffer.clear()
                    entries
                }
            }
        if(entries.isEmpty())
            return "--- No DB Logs found: duration=${Duration.ofMillis(logDuration.toLong()).toHours()}h - level=${getPriorityString(minLevel)} - (DB version: ${dbAccess.version} ${dbAccess.creationError})---"
        val sb = StringBuilder()
        sb.append("------------------ DB Logs: ${Duration.ofMillis(logDuration.toLong()).toHours()}h - level=${getPriorityString(minLevel)} ----------------------\n\n")
        if(!dbAccess.active) {
            sb.append("---------------- DB NOT ACTIVE !!!! ----------------------------\n")
            sb.append(dbAccess.creationError).append("\n\n")
        }
        entries.forEach {
            sb.append(toString(it)).append("\n")
        }
        sb.append("---------------------------------------------------------------\n\n")
        return sb.toString()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if(key.isNullOrEmpty() || key == Constants.SHARED_PREF_LOG_DEBUG) {
            minLevel = if(sharedPreferences.getBoolean(Constants.SHARED_PREF_LOG_DEBUG, false))  android.util.Log.DEBUG else android.util.Log.INFO
            i(LOG_ID, "Log level changed to ${getPriorityString(minLevel)}")
            scope.launch {
                clearLogs(true)
            }
        }
        if(key.isNullOrEmpty() || key == Constants.SHARED_PREF_LOG_DURATION) {
            logDuration = sharedPreferences.getInt(Constants.SHARED_PREF_LOG_DURATION, 0) * 60*60*1000
            i(LOG_ID, "Log duration changed to ${Duration.ofMillis(logDuration.toLong()).toHours()}h")
            scope.launch {
                clearLogs(true)
            }
        }

    }
}
