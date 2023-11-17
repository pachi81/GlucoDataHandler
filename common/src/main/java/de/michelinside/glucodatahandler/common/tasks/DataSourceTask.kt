package de.michelinside.glucodatahandler.common.tasks

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

abstract class DataSourceTask(private val enabledKey: String) : BackgroundTask() {
    private var enabled = false
    private var interval = 1L

    companion object {
        private val LOG_ID = "GlucoDataHandler.Task.DataSourceTask"
        private var httpClient: OkHttpClient? = null

        val preferencesToSend = mutableSetOf(
            Constants.SHARED_PREF_SOURCE_DELAY,
            Constants.SHARED_PREF_SOURCE_INTERVAL,
            Constants.SHARED_PREF_LIBRE_USER,
            Constants.SHARED_PREF_LIBRE_PASSWORD,
            Constants.SHARED_PREF_LIBRE_RECONNECT,
        )
        fun updateSettings(context: Context, bundle: Bundle) {
            val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            with(sharedPref.edit()) {
                putInt(Constants.SHARED_PREF_SOURCE_DELAY, bundle.getInt(Constants.SHARED_PREF_SOURCE_DELAY, -1))
                putString(Constants.SHARED_PREF_SOURCE_INTERVAL, bundle.getString(Constants.SHARED_PREF_SOURCE_INTERVAL, "1"))
                putString(Constants.SHARED_PREF_LIBRE_USER, bundle.getString(Constants.SHARED_PREF_LIBRE_USER, ""))
                putString(Constants.SHARED_PREF_LIBRE_PASSWORD, bundle.getString(Constants.SHARED_PREF_LIBRE_PASSWORD, ""))
                putBoolean(Constants.SHARED_PREF_LIBRE_RECONNECT, bundle.getBoolean(Constants.SHARED_PREF_LIBRE_RECONNECT, false))
                apply()
            }
            InternalNotifier.notify(context, NotifySource.SOURCE_SETTINGS, null)
        }
        fun getSettingsBundle(sharedPref: SharedPreferences): Bundle {
            val bundle = Bundle()
            bundle.putInt(Constants.SHARED_PREF_SOURCE_DELAY, sharedPref.getInt(Constants.SHARED_PREF_SOURCE_DELAY, -1))
            bundle.putString(Constants.SHARED_PREF_SOURCE_INTERVAL, sharedPref.getString(Constants.SHARED_PREF_SOURCE_INTERVAL, "1"))
            bundle.putString(Constants.SHARED_PREF_LIBRE_USER, sharedPref.getString(Constants.SHARED_PREF_LIBRE_USER, ""))
            bundle.putString(Constants.SHARED_PREF_LIBRE_PASSWORD, sharedPref.getString(Constants.SHARED_PREF_LIBRE_PASSWORD, ""))
            bundle.putBoolean(Constants.SHARED_PREF_LIBRE_RECONNECT, sharedPref.getBoolean(Constants.SHARED_PREF_LIBRE_RECONNECT, false))
            return bundle
        }

        fun isConnected(context: Context): Boolean {
            try {
                val connectivityManager = context.getSystemService(
                    Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
                    ?: return false

                val activeNetwork = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

                // If we check only for "NET_CAPABILITY_INTERNET", we get "true" if we are connected to a wifi
                // which has no access to the internet. "NET_CAPABILITY_VALIDATED" also verifies that we
                // are online
                return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

            } catch (exc: Exception) {
                Log.e(LOG_ID, "isConnected exception: " + exc.message.toString() )
            }
            return false
        }
    }

    abstract fun executeRequest(context: Context)

    override fun execute(context: Context) {
        if (enabled) {
            Log.d(LOG_ID, "Execute request")
            executeRequest(context)
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
    abstract fun isConnectionError(): Boolean

    override fun getIntervalMinute(): Long {
        if (interval > 1 && isConnectionError()) {
            Log.d(LOG_ID, "Use interval of 1 minute as there is a connection issue")
            return 1   // retry after a minute
        }
        return interval
    }

    override fun active(elapsetTimeMinute: Long): Boolean {
        return enabled
    }

    override fun checkPreferenceChanged(sharedPreferences: SharedPreferences, key: String?, context: Context): Boolean {
        if(key == null) {
            enabled = sharedPreferences.getBoolean(enabledKey, false)
            interval = sharedPreferences.getString(Constants.SHARED_PREF_SOURCE_INTERVAL, "1")?.toLong() ?: 1L
            return true
        } else {
            var result = false
            when(key) {
                enabledKey -> {
                    if (enabled != sharedPreferences.getBoolean(enabledKey, false)) {
                        enabled = sharedPreferences.getBoolean(enabledKey, false)
                        result = true
                        InternalNotifier.notify(GlucoDataService.context!!, NotifySource.SOURCE_STATE_CHANGE, null)
                    }
                }
                Constants.SHARED_PREF_SOURCE_INTERVAL -> {
                    if (interval != (sharedPreferences.getString(Constants.SHARED_PREF_SOURCE_INTERVAL, "1")?.toLong() ?: 1L)) {
                        interval = sharedPreferences.getString(Constants.SHARED_PREF_SOURCE_INTERVAL, "1")?.toLong() ?: 1L
                        result = true
                    }
                }
                Constants.SHARED_PREF_SOURCE_DELAY -> {
                    result = true  // retrigger alarm after delay has changed
                }
            }
            return result
        }
    }
}