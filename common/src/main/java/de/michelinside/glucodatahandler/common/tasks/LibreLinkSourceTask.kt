package de.michelinside.glucodatahandler.common.tasks

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import de.michelinside.glucodatahandler.common.utils.Log
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.R
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.database.GlucoseValue
import de.michelinside.glucodatahandler.common.database.dbAccess
import de.michelinside.glucodatahandler.common.notifier.DataSource
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.GlucoDataUtils
import de.michelinside.glucodatahandler.common.utils.Utils
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import androidx.core.content.edit


// API docu: https://libreview-unofficial.stoplight.io/

class LibreLinkSourceTask : MultiPatientSourceTask(Constants.SHARED_PREF_LIBRE_ENABLED, DataSource.LIBRELINK) {
    override val LOG_ID = "GDH.Task.Source.LibreLinkTask"
    override val patientIdKey = Constants.SHARED_PREF_LIBRE_PATIENT_ID
    companion object {
        private var instance: LibreLinkSourceTask? = null
        private var user = ""
        private var password = ""
        private var reconnect = false
        private var token = ""
        private var tokenExpire = 0L
        private var userId = ""
        private var region = ""
        private var topLevelDomain = "io"
        private var dataReceived = false   // mark this endpoint as already received data
        private var autoAcceptTOU = true
        val patientData: MutableMap<String, String> get() {
            if(instance == null)
                return mutableMapOf<String, String>()
            return instance!!.getPatientData()
        }
        const val server = "https://api.libreview.%s"
        const val region_server = "https://api-%s.libreview.%s"
        const val LOGIN_ENDPOINT = "/llu/auth/login"
        const val CONNECTION_ENDPOINT = "/llu/connections"
        const val GRAPH_ENDPOINT = "/llu/connections/%s/graph"
        const val ACCEPT_ENDPOINT = "/auth/continue/"
        const val ACCEPT_TERMS_TYPE = "tou"
        const val ACCEPT_TOKEN_TYPE = "pp"
    }

    init {
        Log.i(LOG_ID, "init called")
        instance = this
    }

    private fun getUrl(endpoint: String): String {
        val url = (if(region.isEmpty()) server.format(topLevelDomain) else region_server.format(region, topLevelDomain)) + endpoint
        Log.i(LOG_ID, "Using URL: " + url)
        return url
    }

    private fun getHeader(): MutableMap<String, String> {
        val result = mutableMapOf(
            "product" to "llu.android",
            "version" to "4.16.0",
            "Accept" to "application/json",
            "Content-Type" to "application/json",
            "cache-control" to "no-cache",
            "Account-Id" to Utils.encryptSHA256(userId)
        )
        if (token.isNotEmpty()) {
            result["Authorization"] = "Bearer " + token
        }
        Log.v(LOG_ID, "Header: ${result}")
        return result
    }

    override fun reset() {
        Log.i(LOG_ID, "reset called")
        super.reset()
        token = ""
        tokenExpire = 0L
        region = ""
        userId = ""
        dataReceived = false
        try {
            Log.d(LOG_ID, "Save reset")
            GlucoDataService.sharedPref!!.edit {
                putString(Constants.SHARED_PREF_LIBRE_TOKEN, token)
                putString(Constants.SHARED_PREF_LIBRE_USER_ID, userId)
                putLong(Constants.SHARED_PREF_LIBRE_TOKEN_EXPIRE, tokenExpire)
                putString(Constants.SHARED_PREF_LIBRE_REGION, region)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "save reset exception: " + exc.toString() )
        }
    }

    val sensitivData = mutableSetOf("id", "patientId", "firstName", "lastName", "did", "token", "deviceId", "email", "primaryValue", "secondaryValue" )

    private fun replaceSensitiveData(body: String): String {
        try {
            var result = body
            sensitivData.forEach {
                val groups = Regex("\"$it\":\"(.*?)\"").find(result)?.groupValues
                if(!groups.isNullOrEmpty() && groups.size > 1 && groups[1].isNotEmpty()) {
                    val replaceValue = groups[0].replace(groups[1], "---")
                    result = result.replace(groups[0], replaceValue)
                }
            }
            return result.take(1000)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "replaceSensitiveData exception: " + exc.toString() )
            return body
        }
    }

    /*
    After tou there will be pp for re-new token!
    Example for error 4 for tou:
    {
      "status": 4,
      "data": {
        "step": {
          "type": "tou",
          "componentName": "AcceptDocument",
          "props": {
            "reaccept": true,
            "titleKey": "Common.termsOfUse",
            "type": "tou"
          }
        },
        "user": {
          "accountType": "pat",
          "country": "CH",
          "uiLanguage": "en-US"
        },
        "authTicket": {
          "token": "the token here",
          "expires": 1719337966,
          "duration": 3600000
        }
      }
    }
     */

    private fun getStatusMessage(status: Int): String {
        return when(status) {
            2 -> GlucoDataService.context!!.getString(R.string.src_librelink_error2)
            4 -> GlucoDataService.context!!.getString(R.string.src_librelink_error4)
            else -> getErrorInfo(status)
        }
    }

    override fun getNoNewValueInfo(time: Long): String {
        if(Utils.getElapsedTimeMinute(time) > 5)
            return GlucoDataService.context!!.resources.getString(R.string.libre_no_new_value_info)
        return ""
    }

    private fun getError4Message(type: String? = null): String {
        if(type.isNullOrEmpty()) {
            return getStatusMessage(4)
        }
        if(type == ACCEPT_TERMS_TYPE && !autoAcceptTOU) {
            return GlucoDataService.context!!.getString(R.string.src_librelink_error4_tou)
        }
        return GlucoDataService.context!!.getString(R.string.src_librelink_error4_type, type)
    }

    private fun handleError4(jsonObj: JSONObject, lastType: String = ""): JSONObject? {
        // handle error 4 which contains the steps to do, like accept user terms or accept token
        reset()
        if (jsonObj.has("data")) {
            val data = jsonObj.getJSONObject("data")
            checkRedirect(data)
            if(data.has("step")) {
                val step = data.getJSONObject("step")
                Log.i(LOG_ID, "Handle error 4 step: $step")
                if(step.has("type")) {
                    val type = step.getString("type")
                    Log.i(LOG_ID, "Handle error 4 with type: $type - lastType: $lastType")
                    getToken(data)
                    if(token.isEmpty()) {
                        setLastError(step.optString("componentName"), 4, getError4Message(type))
                        return null
                    }
                    if(lastType != type && type == ACCEPT_TOKEN_TYPE || (type == ACCEPT_TERMS_TYPE && autoAcceptTOU )) {
                        // send accept request and re-login
                        Log.i(LOG_ID, "Send accept request for type $type")
                        return checkResponse(httpPost(getUrl(ACCEPT_ENDPOINT + type), getHeader(), null), type)
                    } else {
                        setLastError(step.optString("componentName"), 4, getError4Message(type))
                    }
                    return null
                }
            }
        }
        // else
        setLastError(getErrorMessage(jsonObj), 4, getStatusMessage(4))
        return null
    }

    private fun getErrorMessage(jsonObj: JSONObject): String {
        if(jsonObj.has("error")) {
            val errorObj = jsonObj.optJSONObject("error")
            if(errorObj?.has("message") == true)
                return errorObj.optString("message", "Error")?: "Error"
        }
        if(jsonObj.has("data")) {
            val dataObj = jsonObj.optJSONObject("data")
            if(dataObj?.has("message") == true)
                return dataObj.optString("message", "Error")?: "Error"
        }
        return "Error"
    }

    private fun checkResponse(body: String?, lastError4Type: String = ""): JSONObject? {
        if (body.isNullOrEmpty()) {
            return null
        }
        Log.i(LOG_ID, "Handle json response: " + replaceSensitiveData(body))
        val jsonObj = JSONObject(body)
        if (jsonObj.has("status")) {
            val status = jsonObj.optInt("status", -1)
            if (status == 4) {
                return handleError4(jsonObj, lastError4Type)
            } else if(status != 0) {
                if(jsonObj.has("error")) {
                    setLastError(getErrorMessage(jsonObj), status, getStatusMessage(status))
                    return null
                }
                setLastError(GlucoDataService.context!!.resources.getString(R.string.source_state_error), status)
                return null
            }
        }
        if (jsonObj.has("data")) {
            return jsonObj
        }
        setLastError(GlucoDataService.context!!.resources.getString(R.string.missing_data), 500)
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
                if(checkRedirect(data))
                    return authenticate()
                getToken(data)
            }
        }
        return token.isNotEmpty()
    }

    private fun checkRedirect(data: JSONObject): Boolean {
        Log.d(LOG_ID, "Check for redirect")
        if (data.has("redirect") && data.optBoolean("redirect")) {
            if (data.has("region")) {
                region = data.optString("region", "")
                Log.i(LOG_ID, "Handle redirect to region: " + region)
                saveRegion()
                return true
            } else {
                setLastError(GlucoDataService.context!!.resources.getString(R.string.invalid_redirect), 500)
            }
        }
        return false
    }

    private fun getToken(data: JSONObject) {
        Log.d(LOG_ID, "Check for new token token")
        if(data.has("user")) {
            val user = data.optJSONObject("user")
            if(user != null && user.has("id")) {
                userId = user.optString("id")
                Log.i(LOG_ID, "User ID set!")
                GlucoDataService.sharedPref!!.edit {
                    putString(Constants.SHARED_PREF_LIBRE_USER_ID, userId)
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
                    GlucoDataService.sharedPref!!.edit {
                        putString(Constants.SHARED_PREF_LIBRE_TOKEN, token)
                        putLong(Constants.SHARED_PREF_LIBRE_TOKEN_EXPIRE, tokenExpire)
                        putBoolean(Constants.SHARED_PREF_LIBRE_RECONNECT, false)
                    }
                }
            }
        }
    }

    private fun saveRegion() {
        try {
            Log.d(LOG_ID, "Save region $region")
            GlucoDataService.sharedPref!!.edit {
                putString(Constants.SHARED_PREF_LIBRE_REGION, region)
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
                GlucoDataService.sharedPref!!.edit {
                    putBoolean(Constants.SHARED_PREF_LIBRE_RECONNECT, false)
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

    override fun getPatientData(): MutableMap<String, String> {
        return handleConnectionResponse(httpGet(getUrl(CONNECTION_ENDPOINT), getHeader()))
    }

    override fun getPatientValue(patientId: String): Boolean {
        return handleGraphResponse(httpGet(getUrl(GRAPH_ENDPOINT.format(patientId)), getHeader()))
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

    private fun handleGraphResponse(body: String?): Boolean {
        val jsonObject = checkResponse(body)
        if (jsonObject != null && jsonObject.has("data")) {
            val data = jsonObject.optJSONObject("data")
            if(data != null) {
                if(data.has("graphData")) {
                    parseGraphData(data.optJSONArray("graphData"))
                }
                if(data.has("connection")) {
                    val connection = data.optJSONObject("connection")
                    if(connection!=null)
                        return parseGlucoseData(connection)
                }
            }
        }

        Log.e(LOG_ID, "No data found in response: ${replaceSensitiveData(body!!)}")
        setLastError(GlucoDataService.context!!.resources.getString(R.string.missing_data), -1, GlucoDataService.context!!.resources.getString(R.string.provide_logs))
        reset()
        retry = true
        return false
    }

    private fun parseGraphData(graphData: JSONArray?) {
        try {
            if(graphData != null && graphData.length() > 0) {
                val firstTime = getFirstNeedGraphValueTime()
                Log.d(LOG_ID, "Parse graph data for ${graphData.length()} entries with time >= ${Utils.getUiTimeStamp(firstTime)}")
                if(firstTime > 0L) {
                    val values = mutableListOf<GlucoseValue>()
                    for (i in 0 until graphData.length()) {
                        val glucoseData = graphData.optJSONObject(i)
                        if(glucoseData != null) {
                            val parsedUtc = parseUtcTimestamp(glucoseData.optString("FactoryTimestamp"))
                            val parsedLocal = parseLocalTimestamp(glucoseData.optString("Timestamp"))
                            val time = if (parsedUtc > 0) parsedUtc else parsedLocal
                            if(time >= firstTime) {
                                val value = glucoseData.optInt("ValueInMgPerDl")
                                if(GlucoDataUtils.isGlucoseValid(value))
                                    values.add(GlucoseValue(time, value))
                            }
                        }
                    }
                    Log.d(LOG_ID, "Add ${values.size} values to db")
                    dbAccess.addGlucoseValues(values)
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "parseGraphData exception: " + exc.toString() )
        }
    }

    private fun handleConnectionResponse(body: String?): MutableMap<String, String> {
        /*  used for getting patient id
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
                        setLastError(GlucoDataService.context!!.resources.getString(R.string.source_no_patient))
                        reset()
                    } else {
                        setLastError(GlucoDataService.context!!.getString(R.string.src_libre_setup_librelink))
                    }
                    return mutableMapOf()
                }
                return getPatientData(array)
            } else {
                Log.e(LOG_ID, "No data array found in response: ${replaceSensitiveData(body!!)}")
                setLastError(GlucoDataService.context!!.resources.getString(R.string.missing_data), -1, GlucoDataService.context!!.resources.getString(R.string.provide_logs))
                reset()
                retry = true
            }
        }
        return mutableMapOf()
    }

    private fun parseGlucoseData(data: JSONObject): Boolean {
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
                glucoExtras.putString(ReceiveData.SERIAL, "LibreLink")
                if (data.has("sensor")) {
                    val sensor = data.optJSONObject("sensor")
                    if (sensor != null && sensor.has("sn"))
                        glucoExtras.putString(ReceiveData.SERIAL, sensor.optString("sn"))
                    if(sensor != null && sensor.has("a")) {
                        val age = sensor.optLong("a")
                        if(age > 0L) {
                            Log.d(LOG_ID, "Sensor start-time: $age - ${Utils.getUiTimeStamp(age*1000)}")
                            glucoExtras.putLong(ReceiveData.SENSOR_START_TIME, age*1000)
                        }
                    }
                }
                dataReceived = true
                handleResult(glucoExtras)
                return true
            }
        } else {
            Log.e(LOG_ID, "No glucoseMeasurement found in response: ${replaceSensitiveData(data.toString())}")
        }
        return false
    }

    private fun getPatientData(dataArray: JSONArray): MutableMap<String, String> {
        Log.i(LOG_ID, "Parse ${dataArray.length()} patient data")
        // create patientData map
        val newPatientData = mutableMapOf<String, String>()
        for (i in 0 until dataArray.length()) {
            val data = dataArray.getJSONObject(i)
            if(data.has("patientId")) {
                val id = data.getString("patientId")
                val name: String
                if(data.has("firstName") || data.has("lastName"))
                    name = data.optString("firstName", "") + " " +  data.optString("lastName", "")
                else {
                    Log.w(LOG_ID, "no name found for patient!")
                    name = id
                }
                Log.v(LOG_ID, "New patient found: $name")
                newPatientData[id] = name.trim()
            } else {
                Log.e(LOG_ID, "No patientId found in data: $data")
            }
        }
        return newPatientData
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
            autoAcceptTOU = sharedPreferences.getBoolean(Constants.SHARED_PREF_LIBRE_AUTO_ACCEPT_TOU, true)
            topLevelDomain = sharedPreferences.getString(Constants.SHARED_PREF_LIBRE_SERVER, "io")?: "io"
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
                Constants.SHARED_PREF_LIBRE_AUTO_ACCEPT_TOU -> {
                    if (autoAcceptTOU != sharedPreferences.getBoolean(Constants.SHARED_PREF_LIBRE_AUTO_ACCEPT_TOU, true)) {
                        autoAcceptTOU = sharedPreferences.getBoolean(Constants.SHARED_PREF_LIBRE_AUTO_ACCEPT_TOU, true)
                        Log.i(LOG_ID, "Auto accept TOU changed to $autoAcceptTOU")
                        if(autoAcceptTOU && lastErrorCode == 4)
                            trigger = true
                    }
                }
                Constants.SHARED_PREF_LIBRE_SERVER -> {
                    if (topLevelDomain != sharedPreferences.getString(Constants.SHARED_PREF_LIBRE_SERVER, "io")) {
                        topLevelDomain = sharedPreferences.getString(Constants.SHARED_PREF_LIBRE_SERVER, "io")?: "io"
                        if(topLevelDomain.isEmpty())
                            topLevelDomain = "io"
                        Log.i(LOG_ID, "Top level domain changed to $topLevelDomain")
                        reset()
                        trigger = true
                    }
                }
            }
        }
        return super.checkPreferenceChanged(sharedPreferences, key, context) || trigger
    }
}
