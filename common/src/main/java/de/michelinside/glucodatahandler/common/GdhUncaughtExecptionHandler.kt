package de.michelinside.glucodatahandler.common

import android.util.Log
import java.lang.Exception
import kotlin.system.exitProcess

object GdhUncaughtExecptionHandler : Thread.UncaughtExceptionHandler {
    private val LOG_ID = "GDH.UncaughtExceptionHandler"
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private var exceptionCaught = false

    fun init() {
        Log.d(LOG_ID, "init called")
        if(defaultHandler == null) {
            defaultHandler = Thread.getDefaultUncaughtExceptionHandler()!!
            Log.d(LOG_ID, "Replace default handler $defaultHandler")
            Thread.setDefaultUncaughtExceptionHandler(this)
        }
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        try {
            if(!exceptionCaught) {
                exceptionCaught = true
                val message = "${e.message}\n${e.stackTraceToString()}"
                Log.e(LOG_ID, "Uncaught exception detected in thread ${t.name}: $message")
                if(GlucoDataService.sharedPref != null) {
                    val sharedPref = GlucoDataService.sharedPref!!
                    val customLayoutEnabled = sharedPref.getBoolean(
                        Constants.SHARED_PREF_PERMANENT_NOTIFICATION_CUSTOM_LAYOUT,
                        true
                    )
                    with(sharedPref.edit()) {
                        putBoolean(Constants.SHARED_PREF_UNCAUGHT_EXCEPTION_DETECT, true)
                        putString(Constants.SHARED_PREF_UNCAUGHT_EXCEPTION_MESSAGE, message)
                        if (message.contains("BadForegroundServiceNotificationException") || message.contains(
                                "RemoteServiceException"
                            )
                        ) {
                            Log.e(
                                LOG_ID,
                                "BadForegroundServiceNotificationException detected! customLayoutEnabled=$customLayoutEnabled"
                            )
                            if (customLayoutEnabled)
                                putBoolean(
                                    Constants.SHARED_PREF_PERMANENT_NOTIFICATION_CUSTOM_LAYOUT,
                                    false
                                )
                            else
                                putBoolean(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_EMPTY, true)
                        }
                        apply()
                        Thread.sleep(100) // wait for saving
                        Log.v(LOG_ID, "Exception saved!")
                    }
                }
            } else {
                Log.d(LOG_ID, "Exception already handled!")
            }
        } catch (e: Exception) {
            Log.e(LOG_ID, "Exception: ${e.message}")
        }
        if(defaultHandler!=null)
            defaultHandler!!.uncaughtException(t, e)
        else
            exitProcess(10)
    }

}