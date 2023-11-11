package de.michelinside.glucodatahandler.common.tasks

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import de.michelinside.glucodatahandler.common.Constants
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

abstract class DataSourceTask(val enabledKey: String) : BackgroundTask() {
    private val LOG_ID = "GlucoDataHandler.Task.DataSourceTask"
    private var enabled = false
    private var delay = 10L
    private var interval = 1L

    companion object {
        var editSettings = false
    }

    abstract fun executeRequest()

    override fun execute(context: Context) {
        Log.i(LOG_ID, "execute in " + delay + " seconds")
        if (delay > 0L)
            Executors.newSingleThreadScheduledExecutor().schedule({run()}, delay, TimeUnit.SECONDS)
        else
            run()
    }

    protected fun run() {
        if (enabled) {
            if (editSettings) {
                Log.w(LOG_ID, "Not execute data source during changing settings")
            } else {
                executeRequest()
            }
        }
    }

    protected fun triggerDirectExecution() {
        if(enabled) {
            if(editSettings) {
                Log.i(LOG_ID, "Trigger execution in 10 seconds")
                Executors.newSingleThreadScheduledExecutor().schedule({run()}, 10, TimeUnit.SECONDS)
            } else {
                // trigger direct execution!
                Log.i(LOG_ID, "Trigger execution")
                Executors.newSingleThreadScheduledExecutor().execute { run() }
            }
        }
    }

    override fun getIntervalMinute(): Long {
        return interval
    }

    override fun active(elapsetTimeMinute: Long): Boolean {
        return enabled
    }

    override fun checkPreferenceChanged(sharedPreferences: SharedPreferences, key: String?, context: Context): Boolean {
        if(key == null) {
            enabled = sharedPreferences.getBoolean(enabledKey, false)
            delay = sharedPreferences.getInt(Constants.SHARED_PREF_SOURCE_DELAY, -1).toLong()
            interval = sharedPreferences.getString(Constants.SHARED_PREF_SOURCE_INTERVAL, "1")?.toLong() ?: 1L
            return true
        } else {
            var result = false
            when(key) {
                enabledKey -> {
                    if (enabled != sharedPreferences.getBoolean(enabledKey, false)) {
                        enabled = sharedPreferences.getBoolean(enabledKey, false)
                        triggerDirectExecution()
                        result = true
                    }
                }
                Constants.SHARED_PREF_SOURCE_DELAY -> {
                    if (delay != sharedPreferences.getInt(Constants.SHARED_PREF_SOURCE_DELAY, -1).toLong()) {
                        delay = sharedPreferences.getInt(Constants.SHARED_PREF_SOURCE_DELAY, -1).toLong()
                        // does not change the result as it has no influence on interval handling
                    }
                }
                Constants.SHARED_PREF_SOURCE_INTERVAL -> {
                    if (interval != (sharedPreferences.getString(Constants.SHARED_PREF_SOURCE_INTERVAL, "1")?.toLong() ?: 1L)) {
                        interval = sharedPreferences.getString(Constants.SHARED_PREF_SOURCE_INTERVAL, "1")?.toLong() ?: 1L
                        result = true
                    }
                }
            }
            return result
        }
    }
}