package de.michelinside.glucodatahandler.common.tasks

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import de.michelinside.glucodatahandler.common.AppSource
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.Intents
import de.michelinside.glucodatahandler.common.R
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.SourceState
import de.michelinside.glucodatahandler.common.SourceStateData
import de.michelinside.glucodatahandler.common.database.dbAccess
import de.michelinside.glucodatahandler.common.notifier.DataSource
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.receiver.InternalActionReceiver
import de.michelinside.glucodatahandler.common.utils.HttpRequest
import de.michelinside.glucodatahandler.common.utils.Utils
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException

abstract class DataSourceTask(private val enabledKey: String, protected val source: DataSource) : BackgroundTask() {
    private var enabled = false
    private var interval = 1L
    private var delaySec = 10L
    private var httpRequest = HttpRequest()
    protected var retry = false
    protected var firstGetValue = false
    private var isFirstRequest = true  // first request after startup


    companion object {
        private val LOG_ID = "GDH.Task.Source.DataSourceTask"

        val preferencesToSend = mutableSetOf(
            Constants.SHARED_PREF_SOURCE_DELAY,
            Constants.SHARED_PREF_SOURCE_INTERVAL,
            Constants.SHARED_PREF_LIBRE_USER,
            Constants.SHARED_PREF_LIBRE_PASSWORD,
            Constants.SHARED_PREF_LIBRE_RECONNECT,
            Constants.SHARED_PREF_LIBRE_AUTO_ACCEPT_TOU,
            Constants.SHARED_PREF_LIBRE_PATIENT_ID,
            Constants.SHARED_PREF_LIBRE_SERVER,
            Constants.SHARED_PREF_NIGHTSCOUT_URL,
            Constants.SHARED_PREF_NIGHTSCOUT_SECRET,
            Constants.SHARED_PREF_NIGHTSCOUT_TOKEN,
            Constants.SHARED_PREF_NIGHTSCOUT_IOB_COB,
            Constants.SHARED_PREF_DEXCOM_SHARE_USER,
            Constants.SHARED_PREF_DEXCOM_SHARE_PASSWORD,
            Constants.SHARED_PREF_DEXCOM_SHARE_USE_US_URL,
            Constants.SHARED_PREF_DEXCOM_SHARE_RECONNECT
        )
        fun updateSettings(context: Context, bundle: Bundle) {
            val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            with(sharedPref.edit()) {
                putInt(Constants.SHARED_PREF_SOURCE_DELAY, bundle.getInt(Constants.SHARED_PREF_SOURCE_DELAY, -1))
                putString(Constants.SHARED_PREF_SOURCE_INTERVAL, bundle.getString(Constants.SHARED_PREF_SOURCE_INTERVAL, "1"))

                putString(Constants.SHARED_PREF_LIBRE_USER, bundle.getString(Constants.SHARED_PREF_LIBRE_USER, ""))
                putString(Constants.SHARED_PREF_LIBRE_PASSWORD, bundle.getString(Constants.SHARED_PREF_LIBRE_PASSWORD, ""))
                putBoolean(Constants.SHARED_PREF_LIBRE_RECONNECT, bundle.getBoolean(Constants.SHARED_PREF_LIBRE_RECONNECT, false))
                putBoolean(Constants.SHARED_PREF_LIBRE_AUTO_ACCEPT_TOU, bundle.getBoolean(Constants.SHARED_PREF_LIBRE_AUTO_ACCEPT_TOU, true))
                putString(Constants.SHARED_PREF_LIBRE_SERVER, bundle.getString(Constants.SHARED_PREF_LIBRE_SERVER, "io"))
                putString(Constants.SHARED_PREF_LIBRE_PATIENT_ID, bundle.getString(Constants.SHARED_PREF_LIBRE_PATIENT_ID, ""))

                putString(Constants.SHARED_PREF_NIGHTSCOUT_URL, bundle.getString(Constants.SHARED_PREF_NIGHTSCOUT_URL, ""))
                putString(Constants.SHARED_PREF_NIGHTSCOUT_SECRET, bundle.getString(Constants.SHARED_PREF_NIGHTSCOUT_SECRET, ""))
                putString(Constants.SHARED_PREF_NIGHTSCOUT_TOKEN, bundle.getString(Constants.SHARED_PREF_NIGHTSCOUT_TOKEN, ""))
                putBoolean(Constants.SHARED_PREF_NIGHTSCOUT_IOB_COB, bundle.getBoolean(Constants.SHARED_PREF_NIGHTSCOUT_IOB_COB, true))

                putString(Constants.SHARED_PREF_DEXCOM_SHARE_USER, bundle.getString(Constants.SHARED_PREF_DEXCOM_SHARE_USER, ""))
                putString(Constants.SHARED_PREF_DEXCOM_SHARE_PASSWORD, bundle.getString(Constants.SHARED_PREF_DEXCOM_SHARE_PASSWORD, ""))
                putBoolean(Constants.SHARED_PREF_DEXCOM_SHARE_USE_US_URL, bundle.getBoolean(Constants.SHARED_PREF_DEXCOM_SHARE_USE_US_URL, false))
                putBoolean(Constants.SHARED_PREF_DEXCOM_SHARE_RECONNECT, bundle.getBoolean(Constants.SHARED_PREF_DEXCOM_SHARE_RECONNECT, false))
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
            bundle.putBoolean(Constants.SHARED_PREF_LIBRE_AUTO_ACCEPT_TOU, sharedPref.getBoolean(Constants.SHARED_PREF_LIBRE_AUTO_ACCEPT_TOU, true))
            bundle.putString(Constants.SHARED_PREF_LIBRE_SERVER, sharedPref.getString(Constants.SHARED_PREF_LIBRE_SERVER, "io"))
            bundle.putString(Constants.SHARED_PREF_LIBRE_PATIENT_ID, sharedPref.getString(Constants.SHARED_PREF_LIBRE_PATIENT_ID, ""))

            bundle.putString(Constants.SHARED_PREF_NIGHTSCOUT_URL, sharedPref.getString(Constants.SHARED_PREF_NIGHTSCOUT_URL, ""))
            bundle.putString(Constants.SHARED_PREF_NIGHTSCOUT_SECRET, sharedPref.getString(Constants.SHARED_PREF_NIGHTSCOUT_SECRET, ""))
            bundle.putString(Constants.SHARED_PREF_NIGHTSCOUT_TOKEN, sharedPref.getString(Constants.SHARED_PREF_NIGHTSCOUT_TOKEN, ""))
            bundle.putBoolean(Constants.SHARED_PREF_NIGHTSCOUT_IOB_COB, sharedPref.getBoolean(Constants.SHARED_PREF_NIGHTSCOUT_IOB_COB, true))

            bundle.putString(Constants.SHARED_PREF_DEXCOM_SHARE_USER, sharedPref.getString(Constants.SHARED_PREF_DEXCOM_SHARE_USER, ""))
            bundle.putString(Constants.SHARED_PREF_DEXCOM_SHARE_PASSWORD, sharedPref.getString(Constants.SHARED_PREF_DEXCOM_SHARE_PASSWORD, ""))
            bundle.putBoolean(Constants.SHARED_PREF_DEXCOM_SHARE_USE_US_URL, sharedPref.getBoolean(Constants.SHARED_PREF_DEXCOM_SHARE_USE_US_URL, false))
            bundle.putBoolean(Constants.SHARED_PREF_DEXCOM_SHARE_RECONNECT, sharedPref.getBoolean(Constants.SHARED_PREF_DEXCOM_SHARE_RECONNECT, false))
            return bundle
        }
    }

    var lastState = SourceState.NONE
    var lastErrorCode: Int = -1

    fun setLastError(error: String, code: Int = -1, message: String = "")  {
        setState(SourceState.ERROR, error, code, message)
    }

    fun setState(state: SourceState, error: String = "", code: Int = -1, message: String = "") {
        when (state) {
            SourceState.NONE -> {
                Log.v(LOG_ID, "Set state for source $source: $state - $error ($code) - message: $message")
            }
            SourceState.CONNECTED -> {
                Log.i(LOG_ID, "Set connected for source $source")
                isFirstRequest = false
            }
            else -> {
                Log.w(LOG_ID, "Set state for source $source: $state - $error ($code) - message: $message")
            }
        }
        lastErrorCode = code
        lastState = state

        SourceStateData.setState(source, state, getErrorMessage(state, error, code), message)
    }

    private fun getErrorMessage(state: SourceState, error: String, code: Int): String {
        if (state == SourceState.ERROR && error.isNotEmpty()) {
            var result = ""
            if (code > 0) {
                result = code.toString() + ": "
            }
            result += error
            return result
        }
        return error
    }

    private fun isShortInterval(): Boolean {
        return when(lastState) {
            SourceState.NO_CONNECTION,
            SourceState.NO_NEW_VALUE -> true
            SourceState.ERROR -> lastErrorCode >= 500
            else -> false
        }
    }

    private fun isLongInterval(): Boolean {
        return when(lastState) {
            SourceState.ERROR -> lastErrorCode == 429
            else -> false
        }
    }

    open fun authenticate(): Boolean = true

    open fun reset() {}

    abstract fun getValue(): Boolean

    private fun executeRequest(firstCall: Boolean = true) {
        try {
            Log.d(LOG_ID, "getData called: firstCall: $firstCall")
            if (authenticate()) {
                firstGetValue = firstCall
                retry = false
                if(!getValue() && firstCall && retry)
                    executeRequest(false) // retry if internal client error or invalid session id
            }
        } catch(ex: SocketTimeoutException) {
            Log.e(LOG_ID, "Timeout occurs: ${ex.message}")
            reset()
            if(firstCall) {
                executeRequest(false)
            } else {
                setLastError("Timeout")
            }
        }
    }

    open fun needsInternet(): Boolean = true

    override fun execute(context: Context) {
        if (enabled) {
            if (needsInternet() && !HttpRequest.isConnected(context)) {
                Log.w(LOG_ID, "No internet connection")
                setState(SourceState.NO_CONNECTION)
                return
            }
            Log.d(LOG_ID, "Execute request for $source")
            try {
                executeRequest()
            } catch (ex: InterruptedException) {
                throw ex // re throw interruption
            } catch(ex: SocketTimeoutException) {
                Log.w(LOG_ID, "Timeout for $source: " + ex)
                setLastError("Timeout")
            } catch (ex: UnknownHostException) {
                Log.w(LOG_ID, "Internet connection issue for $source: " + ex)
                setState(SourceState.NO_CONNECTION)
            } catch (ex: Exception) {
                Log.e(LOG_ID, "Exception during execution for $source: " + ex)
                setLastError(ex.message.toString())
            }
            httpRequest.stop()
        }
    }

    open fun getNoNewValueInfo(time: Long): String = ""

    protected fun handleResult(extras: Bundle) {
        Log.d(LOG_ID, "handleResult for $source: ${Utils.dumpBundle(extras)}")
        if(!ReceiveData.hasNewValue(extras)) {
            if(extras.containsKey(ReceiveData.TIME)) {
                setState(
                    SourceState.NO_NEW_VALUE,
                    GlucoDataService.context!!.resources.getString(R.string.last_value_on_server, Utils.getUiTimeStamp(extras.getLong(ReceiveData.TIME))),
                    -1,
                    getNoNewValueInfo(extras.getLong(ReceiveData.TIME))
                )
            } else {
                setState(SourceState.NO_NEW_VALUE)
            }
            return
        }

        val intent = Intent(GlucoDataService.context!!, InternalActionReceiver::class.java)
        intent.action = Intents.GLUCODATA_ACTION
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        intent.setPackage(GlucoDataService.context!!.packageName)
        extras.putInt(Constants.EXTRA_SOURCE_INDEX, source.ordinal)
        intent.putExtras(extras)
        setState(SourceState.CONNECTED)
        GlucoDataService.context!!.sendBroadcast(intent)
        /*
        val done = AtomicBoolean(false)
        val active = AtomicBoolean(false)
        val task = Runnable {
            try {
                active.set(true)
                Log.d(LOG_ID, "handleResult for $source in main thread")
                val lastTime = ReceiveData.time
                val lastIobCobTime = ReceiveData.iobCobTime
                ReceiveData.handleIntent(GlucoDataService.context!!, source, extras)
                if (ReceiveData.time == lastTime && lastIobCobTime == ReceiveData.iobCobTime)
                    setState(SourceState.NO_NEW_VALUE)
                else
                    setState(SourceState.CONNECTED)
            } catch (ex: Exception) {
                Log.e(LOG_ID, "Exception during task run: " + ex)
                setLastError(ex.message.toString())
            }
            done.set(true)
        }
        val handler = Handler(Looper.getMainLooper())
        handler.post(task)
        var count = 0
        while (!done.get() && count < 30000) {
            Thread.sleep(10)
            count += 10
            if(count.mod(5000) == 0) {
                Log.w(LOG_ID, "Handle result for $source not finished after $count ms! Active: ${active.get()} Mainlopper: Queue-Idle: ${Looper.getMainLooper().queue.isIdle}")
            }
        }
        if(!done.get()) {
            Log.e(LOG_ID, "Handler for $source not finished after $count ms! Active: ${active.get()} - Stop it!")
            handler.removeCallbacksAndMessages(null)
            setLastError("Internal error!")
        }*/
        Log.d(LOG_ID, "handleResult for " + source + " done!")
    }

    private fun getErrorMessage(code: Int, message: String?): String {
        when(code) {
            429 -> return GlucoDataService.context!!.resources.getString(R.string.http_error_429)
            else -> return message ?: "Error"
        }
    }

    private fun getErrorInfo(code: Int): String {
        when(code) {
            429 -> return GlucoDataService.context!!.resources.getString(R.string.http_error_429_info)
            else -> return ""
        }
    }

    open fun checkErrorResponse(code: Int, message: String?, errorResponse: String? = null) {
        Log.d(LOG_ID, "checkErrorResponse: $code - $message - $errorResponse")
        if (code in 400..499) {
            reset() // reset token for client error -> trigger reconnect
            if(firstGetValue) {
                retry = true
                return
            }
        }
        var errorMessage = getErrorMessage(code, message)
        if(errorResponse != null) {
            errorMessage += "\n" + errorResponse
        }
        setLastError(errorMessage, code, getErrorInfo(code))
    }

    open fun getTrustAllCertificates(): Boolean = false


    override fun interrupt() {
        super.interrupt()
        if(httpRequest.connected) {
            Log.w(LOG_ID, "Disconnect current URL connection on interrupt!")
            httpRequest.stop()
        }
    }

    private fun checkResponse(responseCode: Int): String? {
        if (responseCode != HttpURLConnection.HTTP_OK) {
            checkErrorResponse(responseCode, httpRequest.responseMessage, httpRequest.responseError)
            return null
        }
        return httpRequest.response
    }

    protected fun httpGet(url: String, header: MutableMap<String, String>): String? {
        return checkResponse(httpRequest.get(url, header, getTrustAllCertificates()))
    }

    protected fun httpPost(url: String, header: MutableMap<String, String>, postData: String?): String? {
        return checkResponse(httpRequest.post(url, postData, header, getTrustAllCertificates()))
    }

    override fun getIntervalMinute(): Long {
        if (interval > 1L && isShortInterval()) {
            Log.d(LOG_ID, "Use short interval of 1 minute.")
            return 1   // retry after a minute
        }
        if (interval == 1L && isLongInterval()) {
            return 5  // increase interval for case of "429: Too many requests"
        }
        return interval
    }

    override fun getDelayMs(): Long = delaySec * 1000L

    override fun active(elapsetTimeMinute: Long): Boolean {
        return enabled
    }

    private fun setEnabled(newEnabled: Boolean): Boolean {
        Log.v(LOG_ID, "Set enabled=$newEnabled (old: $enabled) for $source")
        var changed = false
        if(newEnabled != enabled) {
            enabled = newEnabled
            Log.i(LOG_ID, "Set enabled=$enabled for $source")
            changed = true
        }
        if (!enabled && source == SourceStateData.lastSource)
            setState(SourceState.NONE)
        return changed
    }

    override fun checkPreferenceChanged(sharedPreferences: SharedPreferences, key: String?, context: Context): Boolean {
        Log.d(LOG_ID, "checkPreferenceChanged for $key")
        if(key == null) {
            setEnabled(sharedPreferences.getBoolean(enabledKey, false))
            interval = sharedPreferences.getString(Constants.SHARED_PREF_SOURCE_INTERVAL, "1")?.toLong() ?: 1L
            delaySec = sharedPreferences.getInt(Constants.SHARED_PREF_SOURCE_DELAY, 10).toLong()
            if(GlucoDataService.appSource == AppSource.WEAR_APP)
                delaySec += 5L  // add 5 seconds delay to receive by phone if connected
            return true
        } else {
            var result = false
            when(key) {
                enabledKey -> {
                    result = setEnabled(sharedPreferences.getBoolean(enabledKey, false))
                }
                Constants.SHARED_PREF_SOURCE_INTERVAL -> {
                    if (interval != (sharedPreferences.getString(Constants.SHARED_PREF_SOURCE_INTERVAL, "1")?.toLong() ?: 1L)) {
                        interval = sharedPreferences.getString(Constants.SHARED_PREF_SOURCE_INTERVAL, "1")?.toLong() ?: 1L
                        result = true
                    }
                }
                Constants.SHARED_PREF_SOURCE_DELAY -> {
                    if (delaySec != sharedPreferences.getInt(Constants.SHARED_PREF_SOURCE_DELAY, 10).toLong()) {
                        delaySec = sharedPreferences.getInt(Constants.SHARED_PREF_SOURCE_DELAY, 10).toLong()
                        if(GlucoDataService.appSource == AppSource.WEAR_APP)
                            delaySec += 5L // add 5 seconds delay to receive by phone if connected
                        result = true  // retrigger alarm after delay has changed
                    }
                }
            }
            return result
        }
    }

    protected fun getFirstNeedGraphValueTime(): Long {
        val firstLastPair = dbAccess.getFirstLastTimestamp()
        val minTime = System.currentTimeMillis() - Constants.DB_MAX_DATA_WEAR_TIME_MS    // 24h for init the first time
        if(firstLastPair.first == 0L || (isFirstRequest && firstLastPair.first < minTime)) {
            Log.i(LOG_ID, "First value is ${Utils.getElapsedTimeMinute(firstLastPair.first)} minutes old - try get older data on first request")
            return minTime
        }
        if(firstLastPair.second > 0L && Utils.getElapsedTimeMinute(firstLastPair.second) > interval) {
            Log.i(LOG_ID, "Last value is ${Utils.getElapsedTimeMinute(firstLastPair.second)} minutes old - try get newer data")
            return maxOf(firstLastPair.second + 30000, minTime)
        }
        return 0L
    }
}
