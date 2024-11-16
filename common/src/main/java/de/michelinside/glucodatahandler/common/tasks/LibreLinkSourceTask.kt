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
import de.michelinside.glucodatahandler.common.SourceState
import de.michelinside.glucodatahandler.common.notifier.DataSource
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.Utils
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone


// API docu: https://libreview-unofficial.stoplight.io/

class LibreLinkSourceTask : DataSourceTask(Constants.SHARED_PREF_LIBRE_ENABLED, DataSource.LIBRELINK) {
    private val LOG_ID = "GDH.Task.Source.LibreLinkTask"
    companion object {
        private var user = ""
        private var password = ""
        private var reconnect = false
        private var token = ""
        private var tokenExpire = 0L
        private var userId = ""
        private var region = ""
        private var patientId = ""
        private var dataReceived = false   // mark this endpoint as already received data
        val patientData = mutableMapOf<String, String>()
        const val server = "https://api.libreview.io"
        const val region_server = "https://api-%s.libreview.io"
        const val LOGIN_ENDPOINT = "/llu/auth/login"
        const val CONNECTION_ENDPOINT = "/llu/connections"
    }

    private fun getUrl(endpoint: String): String {
        val url = (if(region.isEmpty()) server else region_server.format(region)) + endpoint
        Log.i(LOG_ID, "Using URL: " + url)
        return url
    }

    private fun getHeader(): MutableMap<String, String> {
        val result = mutableMapOf(
            "product" to "llu.android",
            "version" to "4.12.0",
            "Accept" to "application/json",
            "Content-Type" to "application/json",
            "cache-control" to "no-cache"
        )
        if (token.isNotEmpty()) {
            result["Authorization"] = "Bearer " + token
            if(userId.isNotEmpty()) {
                result["Account-Id"] = Utils.encryptSHA256(userId)
            }
        }
        return result
    }

    override fun reset() {
        Log.i(LOG_ID, "reset called")
        token = ""
        userId = ""
        tokenExpire = 0L
        region = ""
        dataReceived = false
        patientData.clear()
        try {
            Log.d(LOG_ID, "Save reset")
            with (GlucoDataService.sharedPref!!.edit()) {
                putString(Constants.SHARED_PREF_LIBRE_TOKEN, token)
                putString(Constants.SHARED_PREF_LIBRE_USER_ID, userId)
                putLong(Constants.SHARED_PREF_LIBRE_TOKEN_EXPIRE, tokenExpire)
                putString(Constants.SHARED_PREF_LIBRE_REGION, region)
                apply()
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "save reset exception: " + exc.toString() )
        }
    }

    val sensitivData = mutableSetOf("id", "patientId", "firstName", "lastName", "did", "sn", "token", "deviceId", "email", "primaryValue", "secondaryValue" )

    private fun replaceSensitiveData(body: String): String {
        var result = body
        sensitivData.forEach {
            val groups = Regex("\"$it\":\"(.*?)\"").find(result)?.groupValues
            if(!groups.isNullOrEmpty() && groups.size > 1 && groups[1].isNotEmpty()) {
                val replaceValue = groups[0].replace(groups[1], "---")
                result = result.replace(groups[0], replaceValue)
            }
        }
        return result
    }

    private fun checkResponse(body: String?): JSONObject? {
        if (body.isNullOrEmpty()) {
            return null
        }
        Log.d(LOG_ID, "Handle json response: " + replaceSensitiveData(body))
        val jsonObj = JSONObject(body)
        if (jsonObj.has("status")) {
            val status = jsonObj.optInt("status", -1)
            if(status != 0) {
                if(jsonObj.has("error")) {
                    val error = jsonObj.optJSONObject("error")?.optString("message", "")
                    setLastError(error?: "Error", status)
                    return null
                }
                setLastError("Error", status)
                return null
            }
        }
        if (jsonObj.has("data")) {
            return jsonObj
        }
        setLastError("Missing data in response!", 500)
        reset()
        retry = true
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
          "user": {
              "id": "***",
              "firstName": "xxx",
              "lastName": "yyy",
              ...
            },
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
                        saveRegion()
                        return authenticate()
                    } else {
                        setLastError("redirect without region!!!", 500)
                    }
                }
                if(data.has("user")) {
                    val user = data.optJSONObject("user")
                    if(user != null && user.has("id")) {
                        userId = user.optString("id")
                        Log.i(LOG_ID, "User ID set!")
                        with(GlucoDataService.sharedPref!!.edit()) {
                            putString(Constants.SHARED_PREF_LIBRE_USER_ID, userId)
                            apply()
                        }
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

    private fun saveRegion() {
        try {
            Log.d(LOG_ID, "Save region $region")
            with (GlucoDataService.sharedPref!!.edit()) {
                putString(Constants.SHARED_PREF_LIBRE_REGION, region)
                apply()
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "saveRegion exception: " + exc.toString() )
        }
    }

    override fun authenticate(): Boolean {
        if (token.isNotEmpty() && (reconnect || tokenExpire <= System.currentTimeMillis())) {
            if (!reconnect)
                Log.i(LOG_ID, "Token expired!")
            reset()
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

    override fun getValue(): Boolean {
        return handleGlucoseResponse(httpGet(getUrl(CONNECTION_ENDPOINT), getHeader()))
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

    private fun handleGlucoseResponse(body: String?): Boolean {
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
                    Log.w(LOG_ID, "Empty data array in response: ${replaceSensitiveData(body!!)}")
                    if(dataReceived) {
                        setLastError("Missing data! Please send logs to developer.")
                        reset()
                    } else {
                        setLastError(GlucoDataService.context!!.getString(R.string.src_libre_setup_librelink))
                    }
                    return false
                }
                val data = getPatientData(array)
                if (data == null) {
                    setState(SourceState.NO_NEW_VALUE)
                    return false
                }
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
                        dataReceived = true
                        handleResult(glucoExtras)
                        return true
                    }
                }
            } else {
                Log.e(LOG_ID, "No data array found in response: ${replaceSensitiveData(body!!)}")
                setLastError("Invalid response! Please send logs to developer.")
                reset()
                retry = true
            }
        }
        return false
    }

    private fun getPatientData(dataArray: JSONArray): JSONObject? {
        if(dataArray.length() > patientData.size) {
            // create patientData map
            val checkPatienId = patientData.isEmpty() && patientId.isEmpty()
            patientData.clear()
            for (i in 0 until dataArray.length()) {
                val data = dataArray.getJSONObject(i)
                if(data.has("patientId") && data.has("firstName") && data.has("lastName")) {
                    val id = data.getString("patientId")
                    val name = data.getString("firstName") + " " +  data.getString("lastName")
                    Log.v(LOG_ID, "New patient found: $name")
                    patientData[id] = name
                }
            }
            if (checkPatienId && !patientData.keys.contains(patientId)) {
                patientId = ""
                with (GlucoDataService.sharedPref!!.edit()) {
                    putString(Constants.SHARED_PREF_LIBRE_PATIENT_ID, "")
                    apply()
                }
            }
            Handler(GlucoDataService.context!!.mainLooper).post {
                InternalNotifier.notify(GlucoDataService.context!!, NotifySource.PATIENT_DATA_CHANGED, null)
            }
        }
        if(patientId.isNotEmpty()) {
            for (i in 0 until dataArray.length()) {
                val data = dataArray.getJSONObject(i)
                if (data.has("patientId") && data.getString("patientId") == patientId) {
                    return data
                }
            }
            return null
        }
        // default: use first one
        return dataArray.optJSONObject(0)
    }

    override fun interrupt() {
        super.interrupt()
        Log.w(LOG_ID, "Interrupt called!")
        reset()
    }

    override fun checkPreferenceChanged(sharedPreferences: SharedPreferences, key: String?, context: Context): Boolean {
        Log.v(LOG_ID, "checkPreferenceChanged called for $key")
        var trigger = false
        if (key == null) {
            user = sharedPreferences.getString(Constants.SHARED_PREF_LIBRE_USER, "")!!.trim()
            password = sharedPreferences.getString(Constants.SHARED_PREF_LIBRE_PASSWORD, "")!!.trim()
            token = sharedPreferences.getString(Constants.SHARED_PREF_LIBRE_TOKEN, "")!!
            if(token.isNotEmpty()) {
                dataReceived = true
                userId = sharedPreferences.getString(Constants.SHARED_PREF_LIBRE_USER_ID, "")!!
            }
            tokenExpire = sharedPreferences.getLong(Constants.SHARED_PREF_LIBRE_TOKEN_EXPIRE, 0L)
            region = sharedPreferences.getString(Constants.SHARED_PREF_LIBRE_REGION, "")!!
            patientId = sharedPreferences.getString(Constants.SHARED_PREF_LIBRE_PATIENT_ID, "")!!
            InternalNotifier.notify(GlucoDataService.context!!, NotifySource.SOURCE_STATE_CHANGE, null)
            trigger = true
        } else {
            when(key) {
                Constants.SHARED_PREF_LIBRE_USER -> {
                    if (user != sharedPreferences.getString(Constants.SHARED_PREF_LIBRE_USER, "")) {
                        user = sharedPreferences.getString(Constants.SHARED_PREF_LIBRE_USER, "")!!.trim()
                        reset()
                        InternalNotifier.notify(GlucoDataService.context!!, NotifySource.SOURCE_STATE_CHANGE, null)
                        trigger = true
                    }
                }
                Constants.SHARED_PREF_LIBRE_PASSWORD -> {
                    if (password != sharedPreferences.getString(Constants.SHARED_PREF_LIBRE_PASSWORD, "")) {
                        password = sharedPreferences.getString(Constants.SHARED_PREF_LIBRE_PASSWORD, "")!!.trim()
                        reset()
                        InternalNotifier.notify(GlucoDataService.context!!, NotifySource.SOURCE_STATE_CHANGE, null)
                        trigger = true
                    }
                }
                Constants.SHARED_PREF_LIBRE_RECONNECT -> {
                    if (reconnect != sharedPreferences.getBoolean(Constants.SHARED_PREF_LIBRE_RECONNECT, false)) {
                        reconnect = sharedPreferences.getBoolean(Constants.SHARED_PREF_LIBRE_RECONNECT, false)
                        Log.d(LOG_ID, "Reconnect triggered")
                        trigger = true
                    }
                }
                Constants.SHARED_PREF_LIBRE_PATIENT_ID -> {
                    if (patientId != sharedPreferences.getString(Constants.SHARED_PREF_LIBRE_PATIENT_ID, "")) {
                        patientId = sharedPreferences.getString(Constants.SHARED_PREF_LIBRE_PATIENT_ID, "")!!
                        Log.d(LOG_ID, "PatientID changed to $patientId")
                        trigger = true
                    }
                }
            }
        }
        return super.checkPreferenceChanged(sharedPreferences, key, context) || trigger
    }
}
