package de.michelinside.glucodatahandler.common.tasks

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import de.michelinside.glucodatahandler.common.BuildConfig
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.R
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.notifier.DataSource
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import org.json.JSONObject
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone


// API docu: https://libreview-unofficial.stoplight.io/

class LibreViewSourceTask : DataSourceTask(Constants.SHARED_PREF_LIBRE_ENABLED, DataSource.LIBREVIEW) {
    private val LOG_ID = "GDH.Task.LibreViewSourceTask"
    companion object {
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
    }

    override fun executeRequest(context: Context) {
        Log.i(LOG_ID, "getting data from libre view")
        getConnection()
    }

    private fun getUrl(endpoint: String): String {
        val url = if(region.isEmpty()) server else region_server.format(region)
        return url + endpoint
    }

    private fun getHeader(): MutableMap<String, String> {
        val result = mutableMapOf(
            "product" to "llu.android",
            "version" to "4.7.0",
            "Accept" to "application/json",
            "Content-Type" to "application/json",
            "cache-control" to "no-cache"
        )
        if (token.isNotEmpty()) {
            result["Authorization"] = "Bearer " + token
        }
        return result
    }

    private fun checkResponse(body: String?): JSONObject? {
        if (body.isNullOrEmpty()) {
            if (lastErrorCode in 400..499)
                token = "" // reset token for client error -> trigger reconnect
            return null
        }
        if (BuildConfig.DEBUG) {  // do not log personal data
            Log.i(LOG_ID, "Handle json response: " + body)
        }
        val jsonObj = JSONObject(body)
        if (jsonObj.has("status")) {
            val status = jsonObj.optInt("status", -1)
            if(status != 0) {
                if(jsonObj.has("error")) {
                    val error = jsonObj.optJSONObject("error")?.optString("message", "")
                    setLastError(source, error?: "Error", status)
                    return null
                }
                setLastError(source, "Error", status)
                return null
            }
        }
        if (jsonObj.has("data")) {
            return jsonObj
        }
        setLastError(source, "Missing data in response!", 500)
        return null
    }

    private fun handleLoginResponse(body: String?): Boolean {
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
        val jsonObject = checkResponse(body)
        if (jsonObject != null) {
            val data = jsonObject.optJSONObject("data")
            if (data != null) {
                if (data.has("redirect") && data.optBoolean("redirect")) {
                    if (data.has("region")) {
                        region = data.optString("region", "")
                        Log.i(LOG_ID, "Handle redirect to region: " + region)
                        return login()
                    } else {
                        setLastError(source, "redirect without region!!!", 500)
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
            val json = JSONObject()
            json.put("email", user)
            json.put("password", password)
            return handleLoginResponse(httpPost(getUrl(LOGIN_ENDPOINT), getHeader(), json.toString()))
        }
        return true
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

    private fun handleGlucoseResponse(body: String?) {
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
        val jsonObject = checkResponse(body)
        if (jsonObject != null) {
            val array = jsonObject.optJSONArray("data")
            if (array != null) {
                if (array.length() == 0) {
                    setLastError(source, GlucoDataService.context!!.getString(R.string.src_libre_setup_librelink))
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
                        handleResult(glucoExtras)
                    }
                }
            }
        }
    }

    private fun getConnection(firstCall: Boolean = true) {
        if (login()) {
            handleGlucoseResponse(httpGet(getUrl(CONNECTION_ENDPOINT), getHeader()))
            if (firstCall && token.isEmpty() && lastError.isNotEmpty())
                getConnection(false) // retry if internal client error (not for server error)
        }
    }

    override fun checkPreferenceChanged(sharedPreferences: SharedPreferences, key: String?, context: Context): Boolean {
        var trigger = false
        if (key == null) {
            user = sharedPreferences.getString(Constants.SHARED_PREF_LIBRE_USER, "")!!.trim()
            password = sharedPreferences.getString(Constants.SHARED_PREF_LIBRE_PASSWORD, "")!!.trim()
            token = sharedPreferences.getString(Constants.SHARED_PREF_LIBRE_TOKEN, "")!!
            tokenExpire = sharedPreferences.getLong(Constants.SHARED_PREF_LIBRE_TOKEN_EXPIRE, 0L)
            region = sharedPreferences.getString(Constants.SHARED_PREF_LIBRE_REGION, "")!!
            InternalNotifier.notify(GlucoDataService.context!!, NotifySource.SOURCE_STATE_CHANGE, null)
            trigger = true
        } else {
            when(key) {
                Constants.SHARED_PREF_LIBRE_USER -> {
                    if (user != sharedPreferences.getString(Constants.SHARED_PREF_LIBRE_USER, "")) {
                        user = sharedPreferences.getString(Constants.SHARED_PREF_LIBRE_USER, "")!!.trim()
                        token = ""
                        InternalNotifier.notify(GlucoDataService.context!!, NotifySource.SOURCE_STATE_CHANGE, null)
                        trigger = true
                    }
                }
                Constants.SHARED_PREF_LIBRE_PASSWORD -> {
                    if (password != sharedPreferences.getString(Constants.SHARED_PREF_LIBRE_PASSWORD, "")) {
                        password = sharedPreferences.getString(Constants.SHARED_PREF_LIBRE_PASSWORD, "")!!.trim()
                        token = ""
                        InternalNotifier.notify(GlucoDataService.context!!, NotifySource.SOURCE_STATE_CHANGE, null)
                        trigger = true
                    }
                }
                Constants.SHARED_PREF_LIBRE_RECONNECT -> {
                    if (reconnect != sharedPreferences.getBoolean(Constants.SHARED_PREF_LIBRE_RECONNECT, false)) {
                        reconnect = sharedPreferences.getBoolean(Constants.SHARED_PREF_LIBRE_RECONNECT, false)
                        trigger = true
                    }
                }
            }
        }
        return super.checkPreferenceChanged(sharedPreferences, key, context) || trigger
    }
}
