package de.michelinside.glucodatahandler.common.utils

import android.content.Context
import android.content.SharedPreferences
import de.michelinside.glucodatahandler.common.BuildConfig
import de.michelinside.glucodatahandler.common.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.Collections
import java.util.Date
import java.util.Locale

val format = SimpleDateFormat("dd.MM HH:mm:ss.SSS", Locale.GERMAN)

class LogObject(
    val priority: Int,
    val tag: String?,
    val msg: String?,
    val time: Long = System.currentTimeMillis(),
    val pid: Int = android.os.Process.myPid(),
    val tid: Int = android.os.Process.myTid()) {

    override fun toString(): String {
        return "${format.format(Date(time))} ${pid.toString().padStart(5)} ${tid.toString().padStart(5)} ${Log.getPriorityString(priority)} $tag: $msg"
    }
}

object Log: SharedPreferences.OnSharedPreferenceChangeListener {
    private val LOG_ID = "GDH.Log"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var minLevel = if(BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.INFO
    private var logDuration = 0 // 1h
    private var lastClearTime = 0L

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
    }

    fun close(context: Context) {
        val sharedPreferences = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
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
        return println(android.util.Log.ERROR, tag, msg, throwable)
    }

    private fun println(priority: Int, tag: String, msg: String, throwable: Throwable? = null): Int {
        try {
            val result = if(throwable != null) android.util.Log.e(tag, msg, throwable) else android.util.Log.println(priority, tag, msg)
            if(priority >= minLevel && dbLoggingEnabled) {
                saveLog(LogObject(priority, tag, msg))
            }
            return result
        } catch (exc: Exception) {
            android.util.Log.e(LOG_ID, "Error while logging", exc)
        }
        return -1
    }

    private fun saveLog(logObject: LogObject) {
        scope.launch {
            try {
                // TODO save to db!
                clearLogs()
            } catch (exc: Exception) {
                android.util.Log.e(LOG_ID, "Error while logging", exc)
            }
        }
    }

    private fun clearLogs(force: Boolean = false) {
        try {
            if(force || System.currentTimeMillis() - lastClearTime >= 15*60*1000) {  // remove old entries every 15 minutes
                d(LOG_ID, "Clearing logs - force=$force - lastClearTime=${Utils.getUiTimeStamp(lastClearTime)} - logDuration=${Duration.ofMillis(logDuration.toLong()).toHours()}h")

                if(force || System.currentTimeMillis() - lastClearTime > logDuration) {
                    val removeTime = System.currentTimeMillis() - logDuration
                    // TODO: clear db logs
                    lastClearTime = System.currentTimeMillis()
                }
            }
        } catch (exc: Exception) {
            android.util.Log.e(LOG_ID, "Error while logging", exc)
        }
    }

    fun getLogs(): String {
        // TODO get from db
        val sb = StringBuilder()
        sb.append("---------------------------------------------------------------\n\n")
        return sb.toString()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if(key.isNullOrEmpty() || key == Constants.SHARED_PREF_LOG_DEBUG) {
            minLevel = if(sharedPreferences.getBoolean(Constants.SHARED_PREF_LOG_DEBUG, false))  android.util.Log.DEBUG else android.util.Log.INFO
            i(LOG_ID, "Log level changed to ${getPriorityString(minLevel)}")
            clearLogs(true)
        }
        if(key.isNullOrEmpty() || key == Constants.SHARED_PREF_LOG_DURATION) {
            logDuration = sharedPreferences.getInt(Constants.SHARED_PREF_LOG_DURATION, 0) * 60*60*1000
            i(LOG_ID, "Log duration changed to ${Duration.ofMillis(logDuration.toLong()).toHours()}h")
        }

    }
}