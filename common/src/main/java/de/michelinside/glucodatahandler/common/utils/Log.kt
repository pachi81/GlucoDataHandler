package de.michelinside.glucodatahandler.common.utils

import de.michelinside.glucodatahandler.common.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

val format = SimpleDateFormat("dd.MM HH:mm:ss.SSS", Locale.GERMAN)

class LogObject(val priority: Int, val tag: String?, val msg: String?, val time: Long = System.currentTimeMillis(), val pid: Int = android.os.Process.myPid(), val tid: Int = android.os.Process.myTid()) {
    private fun getPriorityString(): String {
        return when (priority) {
            android.util.Log.VERBOSE -> "V"
            android.util.Log.DEBUG -> "D"
            android.util.Log.INFO -> "I"
            android.util.Log.WARN -> "W"
            android.util.Log.ERROR -> "E"
            else -> priority.toString()
        }
    }
    override fun toString(): String {
        return "${format.format(Date(time))} ${pid.toString().padStart(5)} ${tid.toString().padStart(5)} ${getPriorityString()} $tag: $msg"
    }
}

object Log {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val logList = Collections.synchronizedList(mutableListOf<LogObject>())
    private var minLevel = if(BuildConfig.DEBUG)  android.util.Log.VERBOSE else android.util.Log.INFO
    private val logDuration = 60*60*1000 // 1h
    private var lastClearTime = 0L

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
            if(priority >= minLevel) {
                val result = if(throwable != null) android.util.Log.e(tag, msg, throwable) else android.util.Log.println(priority, tag, msg)
                saveLog(LogObject(priority, tag, msg))
                return result
            }
        } catch (exc: Exception) {
            // ignore as it happens while logging...
        }
        return -1
    }

    private fun saveLog(logObject: LogObject) {
        scope.launch {
            try {
                logList.add(logObject)
                clearLogs()
            } catch (exc: Exception) {
                // ignore as it happens while logging...
            }
        }
    }

    private fun clearLogs() {
        try {
            if(System.currentTimeMillis() - lastClearTime >= 15*60*1000) {  // remove old entries every 15 minutes
                synchronized(logList) {
                    if(System.currentTimeMillis() - lastClearTime > logDuration) {
                        val removeTime = System.currentTimeMillis() - logDuration
                        logList.removeAll { it.time < removeTime }
                        lastClearTime = System.currentTimeMillis()
                    }
                }
            }
        } catch (exc: Exception) {
            // ignore as it happens while logging...
        }
    }

    fun getLogs(): String {
        var list: MutableList<LogObject>
        synchronized(logList) {
            list = logList.toMutableList()
        }
        list.sortBy { it.time }
        val sb = StringBuilder()
        list.forEach {
            sb.append(it.toString() + "\n")
        }
        sb.append("---------------------------------------------------------------\n\n")
        return sb.toString()
    }
}