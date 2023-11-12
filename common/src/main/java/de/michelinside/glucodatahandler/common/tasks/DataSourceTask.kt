package de.michelinside.glucodatahandler.common.tasks

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import de.michelinside.glucodatahandler.common.Constants
import okhttp3.OkHttpClient
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

abstract class DataSourceTask(val enabledKey: String) : BackgroundTask() {
    private val LOG_ID = "GlucoDataHandler.Task.DataSourceTask"
    private var enabled = false
    private var delay = 10L
    private var interval = 1L
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var future: ScheduledFuture<*>? = null

    companion object {
        private var httpClient: OkHttpClient? = null
    }

    abstract fun executeRequest()

    override fun execute(context: Context) {
        if( enabled && (future == null || future!!.isDone) ) {
            Log.i(LOG_ID, "execute in " + delay + " seconds")
            if (delay > 0L) {
                future = executor.schedule({ run() }, delay, TimeUnit.SECONDS)
            } else
                triggerDirectExecution()
        }
    }

    protected fun run() {
        if (enabled) {
            Log.d(LOG_ID, "Execute request")
            executeRequest()
        }
    }

    protected fun triggerDirectExecution() {
        if( enabled && (future == null || future!!.isDone) ) {
            // trigger direct execution!
            executor.execute { run() }
        }
    }

    protected fun getHttpClient(): OkHttpClient {
        if (httpClient != null) {
            return httpClient!!
        }
        val builder = OkHttpClient().newBuilder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
        httpClient = builder.build()
        return httpClient!!
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
            triggerDirectExecution()
            return true
        } else {
            var result = false
            when(key) {
                enabledKey -> {
                    if (enabled != sharedPreferences.getBoolean(enabledKey, false)) {
                        enabled = sharedPreferences.getBoolean(enabledKey, false)
                        if (enabled) {
                            triggerDirectExecution()
                        } else if(future!=null && !future!!.isDone) {
                            future!!.cancel(true)
                        }
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