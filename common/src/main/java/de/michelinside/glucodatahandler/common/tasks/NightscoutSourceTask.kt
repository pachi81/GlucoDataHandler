package de.michelinside.glucodatahandler.common.tasks

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.util.Log
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.R
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.Utils
import de.michelinside.glucodatahandler.common.notifier.DataSource
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import okhttp3.Request
import okhttp3.Response
import java.net.UnknownHostException

class NightscoutSourceTask: DataSourceTask(Constants.SHARED_PREF_NIGHTSCOUT_ENABLED) {
    private val LOG_ID = "GlucoDataHandler.Task.NightscoutSourceTask"
    companion object {
        private var lastError = ""
        private var url = ""
        private var secret = ""
        private var token = ""
        private var retryOnError = false  // retry after a minute if there was a connection or server error
        const val ENDPOINT = "/api/v1/entries/current"
        fun getState(context: Context): String {
            val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            if (!sharedPref.getBoolean(Constants.SHARED_PREF_NIGHTSCOUT_ENABLED, false)) {
                return context.resources.getString(R.string.state_disabled)
            }
            if (lastError.isNotEmpty()) {
                return context.resources.getString(R.string.state_error).format(lastError)
            }
            return context.resources.getString(R.string.state_connected)
        }
    }
    override fun executeRequest(context: Context) {
        try {
            handleResponse(httpCall(createRequest()))
        } catch (ex: UnknownHostException) {
            Log.w(LOG_ID, "Internet connection issue: " + ex)
            setLastError("Connection issue", true)
        } catch (ex: Exception) {
            Log.e(LOG_ID, "Exception executeRequest: " + ex)
            setLastError(ex.message.toString())
        }
    }

    override fun isConnectionError(): Boolean {
        return retryOnError
    }

    override fun checkPreferenceChanged(sharedPreferences: SharedPreferences, key: String?, context: Context): Boolean {
        var result = false
        if (key == null) {
            url = sharedPreferences.getString(Constants.SHARED_PREF_NIGHTSCOUT_URL, "")!!.trim().trimEnd('/')
            secret = sharedPreferences.getString(Constants.SHARED_PREF_NIGHTSCOUT_SECRET, "")!!.trim()
            token = sharedPreferences.getString(Constants.SHARED_PREF_NIGHTSCOUT_TOKEN, "")!!
            result = true
        } else {
            when(key) {
                Constants.SHARED_PREF_NIGHTSCOUT_URL -> {
                    url = sharedPreferences.getString(Constants.SHARED_PREF_NIGHTSCOUT_URL, "")!!.trim().trimEnd('/')
                    result = true
                }
                Constants.SHARED_PREF_NIGHTSCOUT_SECRET -> {
                    secret = sharedPreferences.getString(Constants.SHARED_PREF_NIGHTSCOUT_SECRET, "")!!.trim()
                    result = true
                }
                Constants.SHARED_PREF_NIGHTSCOUT_TOKEN -> {
                    token = sharedPreferences.getString(Constants.SHARED_PREF_NIGHTSCOUT_TOKEN, "")!!
                    result = true
                }
            }
        }
        return super.checkPreferenceChanged(sharedPreferences, key, context) || result
    }

    private fun setLastError(error: String, retry: Boolean = false) {
        lastError = error
        retryOnError = retry
        if (error.isNotEmpty()) {
            Log.w(LOG_ID, error + " (retry: " + retry + ")")
            Handler(GlucoDataService.context!!.mainLooper).post {
                InternalNotifier.notify(GlucoDataService.context!!, NotifySource.SOURCE_STATE_CHANGE, null)
            }
        }
    }

    private fun getUrl(): String {
        var resultUrl = url + ENDPOINT
        if (token.isNotEmpty())
            resultUrl += "?token=" + token
        return resultUrl
    }

    private fun createRequest(): Request {
        val url = getUrl()
        Log.d(LOG_ID, "Create request for url " + url)
        val builder = Request.Builder()
            .url(url)
        if (secret.isNotEmpty()) {
            builder.addHeader("api-secret", secret)
        }
        return builder.build()
    }

    private fun handleResponse(response: Response) {
        if (!response.isSuccessful) {
            setLastError(response.code.toString() + ": " + response.message, response.code >= 500)
            return
        }
        // response look like this: "2023-11-17T11:40:49.000Z"	1700221249000	120	"Flat"	"SENSOR_ID"
        val body = response.body?.string()
        if (!body.isNullOrEmpty()) {
            Log.d(LOG_ID, "Handle response: " + body)
            val values = body.split("\t")
            if (values.size < 4) {
                setLastError("Error in response: " + body)
                return
            }
            val timeStamp = values[1].toLong()
            val glucose = values[2].toInt()
            val trend = values[3].trim('"')
            val sensor = values[4].trim('"')

            val glucoExtras = Bundle()
            glucoExtras.putLong(ReceiveData.TIME, timeStamp)
            if (ReceiveData.isMmol) {
                glucoExtras.putFloat(ReceiveData.GLUCOSECUSTOM, Utils.mgToMmol(glucose.toFloat()))
            } else {
                glucoExtras.putFloat(ReceiveData.GLUCOSECUSTOM, glucose.toFloat())
            }
            glucoExtras.putInt(ReceiveData.MGDL, glucose)
            glucoExtras.putString(ReceiveData.SERIAL, sensor)
            glucoExtras.putFloat(ReceiveData.RATE, ReceiveData.getRateFromLabel(trend))
            glucoExtras.putInt(ReceiveData.ALARM, 0)

            Handler(GlucoDataService.context!!.mainLooper).post {
                ReceiveData.handleIntent(GlucoDataService.context!!, DataSource.NIGHTSCOUT, glucoExtras)
            }
        }
    }
}