package de.michelinside.glucodatahandler.common.tasks

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.util.Log
import de.michelinside.glucodatahandler.common.BuildConfig
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.R
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.notifier.DataSource
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.net.UnknownHostException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager


enum class SourceState(val resId: Int) {
    INACTIVE(R.string.source_state_not_active),
    IN_PROGRESS(R.string.source_state_in_progress),
    OK(R.string.source_state_ok),
    NO_NEW_VALUE(R.string.source_state_no_new_value),
    NO_CONNECTION(R.string.source_state_no_connection),
    ERROR(R.string.source_state_error);
}

abstract class DataSourceTask(private val enabledKey: String, protected val source: DataSource) : BackgroundTask() {
    private var enabled = false
    private var interval = 1L
    private var httpClient: OkHttpClient? = null

    companion object {
        private val LOG_ID = "GlucoDataHandler.Task.DataSourceTask"

        val preferencesToSend = mutableSetOf(
            Constants.SHARED_PREF_SOURCE_DELAY,
            Constants.SHARED_PREF_SOURCE_INTERVAL,
            Constants.SHARED_PREF_LIBRE_USER,
            Constants.SHARED_PREF_LIBRE_PASSWORD,
            Constants.SHARED_PREF_LIBRE_RECONNECT,
            Constants.SHARED_PREF_NIGHTSCOUT_URL,
            Constants.SHARED_PREF_NIGHTSCOUT_SECRET,
            Constants.SHARED_PREF_NIGHTSCOUT_TOKEN,
        )
        fun updateSettings(context: Context, bundle: Bundle) {
            val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            with(sharedPref.edit()) {
                putInt(Constants.SHARED_PREF_SOURCE_DELAY, bundle.getInt(Constants.SHARED_PREF_SOURCE_DELAY, -1))
                putString(Constants.SHARED_PREF_SOURCE_INTERVAL, bundle.getString(Constants.SHARED_PREF_SOURCE_INTERVAL, "1"))
                putString(Constants.SHARED_PREF_LIBRE_USER, bundle.getString(Constants.SHARED_PREF_LIBRE_USER, ""))
                putString(Constants.SHARED_PREF_LIBRE_PASSWORD, bundle.getString(Constants.SHARED_PREF_LIBRE_PASSWORD, ""))
                putBoolean(Constants.SHARED_PREF_LIBRE_RECONNECT, bundle.getBoolean(Constants.SHARED_PREF_LIBRE_RECONNECT, false))

                putString(Constants.SHARED_PREF_NIGHTSCOUT_URL, bundle.getString(Constants.SHARED_PREF_NIGHTSCOUT_URL, ""))
                putString(Constants.SHARED_PREF_NIGHTSCOUT_SECRET, bundle.getString(Constants.SHARED_PREF_NIGHTSCOUT_SECRET, ""))
                putString(Constants.SHARED_PREF_NIGHTSCOUT_TOKEN, bundle.getString(Constants.SHARED_PREF_NIGHTSCOUT_TOKEN, ""))
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

            bundle.putString(Constants.SHARED_PREF_NIGHTSCOUT_URL, sharedPref.getString(Constants.SHARED_PREF_NIGHTSCOUT_URL, ""))
            bundle.putString(Constants.SHARED_PREF_NIGHTSCOUT_SECRET, sharedPref.getString(Constants.SHARED_PREF_NIGHTSCOUT_SECRET, ""))
            bundle.putString(Constants.SHARED_PREF_NIGHTSCOUT_TOKEN, sharedPref.getString(Constants.SHARED_PREF_NIGHTSCOUT_TOKEN, ""))
            return bundle
        }

        private fun getNetwork(context: Context): Network? {
            val connectivityManager = context.getSystemService(
                Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
                ?: return null
            return connectivityManager.activeNetwork
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

        var lastSource: DataSource = DataSource.JUGGLUCO
        var lastState: SourceState = SourceState.INACTIVE
        var lastError: String = ""
        var lastErrorCode: Int = -1

        fun setLastError(source: DataSource, error: String, code: Int = -1) {
            setState( source, SourceState.ERROR, error, code)
        }

        fun setState(source: DataSource, state: SourceState, error: String = "", code: Int = -1) {
            lastError = error
            lastErrorCode = code
            lastSource = source
            lastState = state

            Handler(GlucoDataService.context!!.mainLooper).post {
                InternalNotifier.notify(GlucoDataService.context!!, NotifySource.SOURCE_STATE_CHANGE, null)
            }
        }

        private fun getStateMessage(context: Context): String {
            if (lastState == SourceState.ERROR && lastError.isNotEmpty()) {
                var result = ""
                if (lastErrorCode > 0) {
                    result = lastErrorCode.toString() + ": "
                }
                result += lastError
                return result
            }
            return context.getString(lastState.resId)
        }

        fun getState(context: Context): String {
            return "%s: %s".format(context.getString(lastSource.resId), getStateMessage(context))
        }

        private fun isShortInterval(): Boolean {
            return when(lastState) {
                SourceState.NO_CONNECTION,
                SourceState.NO_NEW_VALUE,
                SourceState.INACTIVE -> true
                SourceState.ERROR -> lastErrorCode >= 500
                else -> false
            }
        }
    }

    abstract fun executeRequest(context: Context)

    override fun execute(context: Context) {
        if (enabled) {
            if (!isConnected(context)) {
                setState(source, SourceState.NO_CONNECTION)
                return
            }
            Log.d(LOG_ID, "Execute request")
            setState(source, SourceState.IN_PROGRESS)
            try {
                executeRequest(context)
            } catch (ex: UnknownHostException) {
                Log.w(LOG_ID, "Internet connection issue: " + ex)
                setState(source, SourceState.NO_CONNECTION)
            } catch (ex: Exception) {
                Log.e(LOG_ID, "Exception during login: " + ex)
                setLastError(source, ex.message.toString())
            }
        }
    }

    protected fun handleResult(extras: Bundle) {
        val done = AtomicBoolean(false)
        val task = Runnable {
            val lastTime = ReceiveData.time
            ReceiveData.handleIntent(GlucoDataService.context!!, source, extras)
            if (ReceiveData.time == lastTime)
                setState(source, SourceState.NO_NEW_VALUE)
            else
                setState(source, SourceState.OK)
            done.set(true)
        }
        Handler(GlucoDataService.context!!.mainLooper).post(task)
        synchronized(task) {
            while (!done.get()) {
                Thread.sleep(5)
            }
            Log.w(LOG_ID, "handleResult done!")
        }
    }

    protected fun getHttpClient(trustAll: Boolean = false): OkHttpClient {
        if (httpClient != null) {
            return httpClient!!
        }

        val builder = OkHttpClient().newBuilder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
        builder.socketFactory(getNetwork(GlucoDataService.context!!)!!.socketFactory)
        if (trustAll) {
            // trust all certificates (see https://www.baeldung.com/okhttp-client-trust-all-certificates)
            val trustAllCerts = arrayOf<TrustManager>(
                @SuppressLint("CustomX509TrustManager")
                object : X509TrustManager {
                    @SuppressLint("TrustAllX509TrustManager")
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                    @SuppressLint("TrustAllX509TrustManager")
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> {
                        return arrayOf()
                    }
                }
            )
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())
            builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            builder.hostnameVerifier { _: String?, _: SSLSession? -> true }
        }

        httpClient = builder.build()
        return httpClient!!
    }


    private fun checkResponse(response: Response): String? {
        if (!response.isSuccessful) {
            setLastError(source, response.message, response.code)
            return null
        }
        return response.body?.string()
    }
    
    private fun createRequest(url: String, header: MutableMap<String, String>, postJSON: String? = null): Request {
        Log.d(LOG_ID, "Create request for url " + url)
        val builder = Request.Builder()
            .url(url)
        header.forEach {
            builder.addHeader(it.key, it.value)
        }
        if (postJSON != null) {
            val body: RequestBody = RequestBody.create(
                "application/json".toMediaTypeOrNull(), postJSON
            )
            builder.post(body)
        }
        return builder.build()
    }

    protected fun httpGet(url: String, header: MutableMap<String, String>): String? {
        return httpCall(createRequest(url, header))
    }

    protected fun httpPost(url: String, header: MutableMap<String, String>, postJSON: String): String? {
        return httpCall(createRequest(url, header, postJSON))
    }

    protected fun httpCall(request: Request): String? {
        if (BuildConfig.DEBUG) {  // do not log personal data
            Log.d(LOG_ID, request.toString())
        }
        return checkResponse(getHttpClient().newCall(request).execute())
    }

    override fun getIntervalMinute(): Long {
        if (interval > 1 && isShortInterval()) {
            Log.d(LOG_ID, "Use short interval of 1 minute.")
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
            if (enabled)
                setState(source, SourceState.INACTIVE)
            return true
        } else {
            var result = false
            when(key) {
                enabledKey -> {
                    if (enabled != sharedPreferences.getBoolean(enabledKey, false)) {
                        enabled = sharedPreferences.getBoolean(enabledKey, false)
                        result = true
                        if (source != lastSource)
                            setState(source, SourceState.INACTIVE)
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
