package de.michelinside.glucodatahandler.common.utils

import android.content.Context
import android.content.SharedPreferences
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
    private val LOG_ID = "GDH.Log"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var minLevel = android.util.Log.DEBUG  // use initial DEBUG to get all DEBUG messages during startup
    private var logDuration = 60*60*1000 // 1h
    private var lastClearTime = 0L
    private var lastSaveTime = 0L
    private val logBuffer = Collections.synchronizedList(mutableListOf<LogEntry>())
    private val maxLogBufferSize: Int get() {
        if(minLevel <= android.util.Log.DEBUG)
            return 50
        return 10
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
        return println(android.util.Log.VERBOSE, tag, msg)
    }

    fun d(tag: String, msg: String): Int {
        return println(android.util.Log.DEBUG, tag, msg)
    }

    fun i(tag: String, msg: String): Int {
        return println(android.util.Log.INFO, tag, msg)
    }

    fun w(tag: String, msg: String): Int {
        return println(android.util.Log.WARN, tag, msg)
    }

    fun e(tag: String, msg: String): Int {
        return println(android.util.Log.ERROR, tag, msg)
    }

    fun e(tag: String, msg: String, throwable: Throwable?): Int {
        return println(android.util.Log.ERROR, tag, msg, throwable = throwable)
    }

    fun isLoggable(tag: String, priority: Int): Boolean {
        return android.util.Log.isLoggable(tag, priority)
    }

    private fun println(priority: Int, tag: String, msg: String, forUser: Boolean = false, throwable: Throwable? = null): Int {
        try {
            val result = if(throwable != null) android.util.Log.e(tag, msg, throwable) else android.util.Log.println(priority, tag, msg)
            if(forUser || (dbLoggingEnabled && priority >= minLevel)) {
                saveLog(LogEntry(priority, tag, msg))
            }
            return result
        } catch (exc: Exception) {
            android.util.Log.e(LOG_ID, "Error while logging", exc)
        }
        return -1
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
        if(!dbAccess.active)
            return  // wait for database is ready
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
                    i(LOG_ID, "Flushed ${entriesToSave.size} log entries to database.")
                }
            }
            lastSaveTime = System.currentTimeMillis()
            if(!wait) {
                i(LOG_ID, "Flushed ${entriesToSave.size} log entries to database asynchronously.")
            }
            clearLogs()
        }
    }

    private fun clearLogs(force: Boolean = false) {
        try {
            if(!dbAccess.active)
                return  // wait for database is ready
            v(LOG_ID, "Clearing logs - force=$force - logDuration=$logDuration")
            if(logDuration <= 0) {
                if(force) {
                    dbAccess.deleteAllLogs()
                    logBuffer.clear()
                }
                return
            }
            if(force || (System.currentTimeMillis() - lastClearTime >= 15*60*1000)) {  // remove old entries every 15 minutes
                d(LOG_ID, "Clearing logs - force=$force - lastClearTime=${Utils.getUiTimeStamp(lastClearTime)} - logDuration=${Duration.ofMillis(logDuration.toLong()).toHours()}h")

                if(force || System.currentTimeMillis() - lastClearTime > logDuration) {
                    val removeTime = System.currentTimeMillis() - logDuration
                    dbAccess.deleteOldLogs(removeTime)
                }
                if(minLevel == android.util.Log.DEBUG) {
                    val removeTime = System.currentTimeMillis() - 30*60*1000 // remove old debug entries after 30 minutes
                    dbAccess.deleteOldDebugLogs(removeTime)
                } else if(force) {
                    dbAccess.deleteOldDebugLogs(System.currentTimeMillis())
                }
                lastClearTime = System.currentTimeMillis()
            }
        } catch (exc: Exception) {
            android.util.Log.e(LOG_ID, "Error while logging", exc)
        }
    }

    private fun toString(log: LogEntry): String {
        return "${format.format(Date(log.timestamp))} ${log.pid.toString().padStart(5)} ${log.tid.toString().padStart(5)} ${getPriorityString(log.priority)} ${log.tag}: ${log.msg}"
    }

    fun getLogs(): String {
        flushLogBuffer(true)
        val entries = dbAccess.getLogs()
        if(entries.isEmpty())
            return ""
        val sb = StringBuilder()
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
