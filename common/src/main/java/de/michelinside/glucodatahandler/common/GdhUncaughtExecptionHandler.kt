package de.michelinside.glucodatahandler.common

import android.content.Context
import android.util.Log
import java.lang.Exception
import kotlin.system.exitProcess

class GdhUncaughtExecptionHandler(val context: Context) : Thread.UncaughtExceptionHandler {
    private val LOG_ID = "GDH.UncaughtExceptionHandler"
    private var defaultHandler: Thread.UncaughtExceptionHandler

    init {
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()!!
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        try {
            val message = "${e.message}\n${e.stackTraceToString()}"
            Log.e(LOG_ID, "Uncaught exception detected in thread ${t.name}: $message")
            val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            val customLayoutEnabled = sharedPref.getBoolean(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_CUSTOM_LAYOUT, true)
            with(sharedPref.edit()) {
                putBoolean(Constants.SHARED_PREF_UNCAUGHT_EXCEPTION_DETECT, true)
                putString(Constants.SHARED_PREF_UNCAUGHT_EXCEPTION_MESSAGE, message)
                if (message.contains("BadForegroundServiceNotificationException") || message.contains("RemoteServiceException")) {
                    Log.e(LOG_ID, "BadForegroundServiceNotificationException detected! customLayoutEnabled=$customLayoutEnabled")
                    if (customLayoutEnabled)
                        putBoolean(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_CUSTOM_LAYOUT, false)
                    else
                        putBoolean(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_EMPTY, true)
                }
                apply()
                Thread.sleep(100) // wait for saving
                Log.v(LOG_ID, "Exception saved!")
            }
        } catch (e: Exception) {
            Log.e(LOG_ID, "Exception: ${e.message}")
        }
        exitProcess(10)
    }

}