package de.michelinside.glucodatahandler.common.tasks

import android.content.Context
import android.content.SharedPreferences
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
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.json.JSONObject
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone


// API docu: https://libreview-unofficial.stoplight.io/

class LibreViewSourceTask : DataSourceTask(Constants.SHARED_PREF_LIBRE_ENABLED) {
    private val LOG_ID = "GlucoDataHandler.Task.LibreViewSourceTask"
    companion object {
        private var lastError = ""
        private var user = ""
        private var password = ""
        private var reconnect = false
        private var token = ""
        private var tokenExpire = 0L
        private var region = ""
        const val server = "https://api.libreview.io"
        const val region_server = "https://api-%s.libreview.io"
        const val LOGIN_ENDPOINT = "/llu/auth/login"
        const val CONNECTION_ENDPOINT = "/llu/connections"
        fun getState(context: Context): String {
            val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            if (!sharedPref.getBoolean(Constants.SHARED_PREF_LIBRE_ENABLED, false)) {
                return context.resources.getString(R.string.state_disabled)
            }
            if (lastError.isNotEmpty()) {
                return context.resources.getString(R.string.state_error).format(lastError)
            }
            if (token.isNotEmpty())
                return context.resources.getString(R.string.state_connected)
            return context.resources.getString(R.string.state_not_connected)
        }
    }

    override fun executeRequest() {
        Log.i(LOG_ID, "getting data from libre view")
        try {
            lastError = ""
            getConnection()
        } catch (ex: Exception) {
            Log.e(LOG_ID, "Exception executeRequest: " + ex)
            lastError = ex.message.toString()
        }
    }

    private fun setLastError(error: String) {
        Log.w(LOG_ID, error)
        lastError = error
        Handler(GlucoDataService.context!!.mainLooper).post {
            // dummy broadcast to update main
            InternalNotifier.notify(GlucoDataService.context!!, NotifySource.SOURCE_STATE_CHANGE, null)
        }
    }

    private fun getUrl(endpoint: String): String {
        val url = if(region.isEmpty()) server else region_server.format(region)
        return url + endpoint
    }

    private fun createRequest(endpoint: String): Request {
        val url = getUrl(endpoint)
        Log.d(LOG_ID, "Create request for url " + url)
        val builder = Request.Builder()
            .addHeader("product", "llu.android")
            .addHeader("version", "4.7.0")
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .addHeader("cache-control", "no-cache")
            .url(url)
        if (token.isEmpty()) {
            val json = JSONObject()
            json.put("email", user)
            json.put("password", password)
            val body: RequestBody = RequestBody.create(
                "application/json".toMediaTypeOrNull(), json.toString()
            )
            builder.post(body)
        } else {
            builder.addHeader("Authorization", "Bearer " + token)
        }
        return builder.build()
    }

    private fun executeRequest(request: Request): Response {
        if (BuildConfig.DEBUG) {  // do not log personal data
            Log.d(LOG_ID, request.toString())
        }
        return getHttpClient().newCall(request).execute()
    }

    private fun checkResponse(response: Response): JSONObject? {
        if (!response.isSuccessful) {
            if(token.isNotEmpty() && response.code >= 400 && response.code < 500)
                token = ""  // trigger reconnect
            setLastError(response.code.toString() + ": " + response.message)
            return null
        }
        val body = response.body?.string()
        if (!body.isNullOrEmpty()) {
            if (BuildConfig.DEBUG) {  // do not log personal data
                Log.i(LOG_ID, "Handle json response: " + body)
            }
            val jsonObj = JSONObject(body)
            if (jsonObj.has("status")) {
                val status = jsonObj.optInt("status", -1)
                if(status != 0) {
                    if(jsonObj.has("error")) {
                        val error = jsonObj.optJSONObject("error")?.optString("message", "")
                        setLastError("Error status " + status + ": " + error)
                        return null
                    }
                    setLastError("Error status " + status)
                    return null
                }
            }
            if (jsonObj.has("data")) {
                return jsonObj
            }
        }
        setLastError("Missing data in response!")
        return null
    }

    private fun handleLoginResponse(response: Response): Boolean {
        /* for redirect:
        {
          "status": 0,
          "data": {
            "redirect": true,
            "region": "de"
          }
        }
        ++++
        login:
        {
          "status": 0,
          "data": {
            ...
            "authTicket": {
              "token": "***",
              "expires": 1715196134,
              "duration": 15552000000
            },
            ...
          }
        }
        */
        val jsonObject = checkResponse(response)
        if (jsonObject != null) {
            val data = jsonObject.optJSONObject("data")
            if (data != null) {
                if (data.has("redirect") && data.optBoolean("redirect")) {
                    if (data.has("region")) {
                        region = data.optString("region", "")
                        Log.i(LOG_ID, "Handle redirect to region: " + region)
                        return login()
                    } else {
                        lastError = "redirect without region!!!"
                    }
                }
                if (data.has("authTicket")) {
                    val authTicket = data.optJSONObject("authTicket")
                    if (authTicket != null) {
                        if (authTicket.has("token")) {
                            token = authTicket.optString("token", "")
                            tokenExpire = authTicket.optLong("expires", 0L) * 1000
                            if (token.isNotEmpty()) {
                                Log.i(LOG_ID, "Login succeeded! Token expires at " + DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT).format(
                                    Date(tokenExpire)
                                ))
                            }
                            with(GlucoDataService.sharedPref!!.edit()) {
                                putString(Constants.SHARED_PREF_LIBRE_TOKEN, token)
                                putLong(Constants.SHARED_PREF_LIBRE_TOKEN_EXPIRE, tokenExpire)
                                putBoolean(Constants.SHARED_PREF_LIBRE_RECONNECT, false)
                                apply()
                            }
                        }
                    }
                }
            }
        }
        return token.isNotEmpty()
    }

    private fun login(): Boolean {
        try {
            if (token.isNotEmpty() && (reconnect || tokenExpire <= System.currentTimeMillis())) {
                Log.i(LOG_ID, "Token expired!")
                token = ""
                if (reconnect) {
                    reconnect = false
                    with(GlucoDataService.sharedPref!!.edit()) {
                        putBoolean(Constants.SHARED_PREF_LIBRE_RECONNECT, false)
                        apply()
                    }
                }
            }
            if (token.isEmpty()) {
                return handleLoginResponse(executeRequest(createRequest(LOGIN_ENDPOINT)))
            }
            return true
        } catch (ex: Exception) {
            Log.e(LOG_ID, "Exception during login: " + ex)
            lastError = ex.message.toString()
        }
        return false
    }

    private fun getRateFromTrend(trend: Int): Float {
        return when(trend) {
            1 -> -2F
            2 -> -1F
            3 -> 0F
            4 -> 1F
            5 -> 2F
            else -> Float.NaN
        }
    }

    private fun parseUtcTimestamp(time: String): Long {
        val format = SimpleDateFormat("M/d/y h:m:s a", Locale.ENGLISH)
        format.setTimeZone(TimeZone.getTimeZone("UTC"))
        val date = format.parse(time)
        format.setTimeZone(TimeZone.getDefault())
        Log.d(LOG_ID, "UTC: " + time + " - local: " +  date?.toString())
        return date?.time?:0
    }

    private fun parseLocalTimestamp(time: String): Long {
        val format = SimpleDateFormat("M/d/y h:m:s a", Locale.ENGLISH)
        return format.parse(time)!!.time
    }

    private fun handleGlucoseResponse(response: Response) {
        /*
            {
              "status": 0,
              "data": [
                {
                  "patientId": "xxx",
                  "sensor": {
                    "deviceId": "",
                    "sn": "xxx"
                  },
                  "glucoseMeasurement": {
                    "FactoryTimestamp": "11/10/2023 3:45:16 PM",
                    "Timestamp": "11/10/2023 4:45:16 PM",
                    "type": 1,
                    "ValueInMgPerDl": 180,
                    "TrendArrow": 3,   // 1 - down -> 5 - up
                    "TrendMessage": null,
                    "MeasurementColor": 2,
                    "GlucoseUnits": 0,  // 0 - mmol/l | 1 - mg/dl
                    "Value": 10,
                    "isHigh": false,
                    "isLow": false
                  }
                }
              ]
            }
        */
        val jsonObject = checkResponse(response)
        if (jsonObject != null) {
            val array = jsonObject.optJSONArray("data")
            if (array != null) {
                if (array.length() == 0) {
                    setLastError(GlucoDataService.context!!.getString(R.string.src_libre_setup_librelink))
                    return
                }
                val data = array.optJSONObject(0)
                if(data.has("glucoseMeasurement")) {
                    val glucoseData = data.optJSONObject("glucoseMeasurement")
                    if (glucoseData != null) {
                        val glucoExtras = Bundle()
                        val parsedUtc = parseUtcTimestamp(glucoseData.optString("FactoryTimestamp"))
                        val parsedLocal = parseLocalTimestamp(glucoseData.optString("Timestamp"))
                        Log.d(LOG_ID, "UTC->local: " + parsedUtc + " - local: " + parsedLocal)
                        if (parsedUtc > 0)
                            glucoExtras.putLong(ReceiveData.TIME, parsedUtc)
                        else
                            glucoExtras.putLong(ReceiveData.TIME, parsedLocal)
                        glucoExtras.putFloat(ReceiveData.GLUCOSECUSTOM, glucoseData.optDouble("Value").toFloat())
                        glucoExtras.putInt(ReceiveData.MGDL, glucoseData.optInt("ValueInMgPerDl"))
                        glucoExtras.putFloat(ReceiveData.RATE, getRateFromTrend(glucoseData.optInt("TrendArrow")))
                        if (glucoseData.optBoolean("isHigh"))
                            glucoExtras.putInt(ReceiveData.ALARM, 6)
                        else if (glucoseData.optBoolean("isLow"))
                            glucoExtras.putInt(ReceiveData.ALARM, 7)
                        else
                            glucoExtras.putInt(ReceiveData.ALARM, 0)

                        glucoExtras.putString(ReceiveData.SERIAL, "LibreLink")
                        if (data.has("sensor")) {
                            val sensor = data.optJSONObject("sensor")
                            if (sensor != null && sensor.has("sn"))
                                glucoExtras.putString(ReceiveData.SERIAL, sensor.optString("sn"))
                        }
                        Handler(GlucoDataService.context!!.mainLooper).post {
                            ReceiveData.handleIntent(GlucoDataService.context!!, DataSource.LIBREVIEW, glucoExtras)
                        }
                    }
                }
            }
        }
    }

    private fun getConnection(firstCall: Boolean = true) {
        if (login()) {
            handleGlucoseResponse(executeRequest(createRequest(CONNECTION_ENDPOINT)))
            if (firstCall && token.isEmpty() && lastError.isNotEmpty())
                getConnection(false) // retry
        }
    }

    override fun checkPreferenceChanged(sharedPreferences: SharedPreferences, key: String?, context: Context): Boolean {
        if (key == null) {
            user = sharedPreferences.getString(Constants.SHARED_PREF_LIBRE_USER, "")!!.trim()
            password = sharedPreferences.getString(Constants.SHARED_PREF_LIBRE_PASSWORD, "")!!.trim()
            token = sharedPreferences.getString(Constants.SHARED_PREF_LIBRE_TOKEN, "")!!
            tokenExpire = sharedPreferences.getLong(Constants.SHARED_PREF_LIBRE_TOKEN_EXPIRE, 0L)
            region = sharedPreferences.getString(Constants.SHARED_PREF_LIBRE_REGION, "")!!
            InternalNotifier.notify(GlucoDataService.context!!, NotifySource.SOURCE_STATE_CHANGE, null)
        } else {
            when(key) {
                Constants.SHARED_PREF_LIBRE_USER -> {
                    if (user != sharedPreferences.getString(Constants.SHARED_PREF_LIBRE_USER, "")) {
                        user = sharedPreferences.getString(Constants.SHARED_PREF_LIBRE_USER, "")!!.trim()
                        token = ""
                        InternalNotifier.notify(GlucoDataService.context!!, NotifySource.SOURCE_STATE_CHANGE, null)
                    }
                }
                Constants.SHARED_PREF_LIBRE_PASSWORD -> {
                    if (password != sharedPreferences.getString(Constants.SHARED_PREF_LIBRE_PASSWORD, "")) {
                        password = sharedPreferences.getString(Constants.SHARED_PREF_LIBRE_PASSWORD, "")!!.trim()
                        token = ""
                        InternalNotifier.notify(GlucoDataService.context!!, NotifySource.SOURCE_STATE_CHANGE, null)
                    }
                }
                Constants.SHARED_PREF_LIBRE_RECONNECT -> {
                    if (reconnect != sharedPreferences.getBoolean(Constants.SHARED_PREF_LIBRE_RECONNECT, false)) {
                        reconnect = sharedPreferences.getBoolean(Constants.SHARED_PREF_LIBRE_RECONNECT, false)
                    }
                }
            }
        }
        return super.checkPreferenceChanged(sharedPreferences, key, context)
    }
}