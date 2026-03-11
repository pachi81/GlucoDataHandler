package de.michelinside.glucodatahandler.common.tasks

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.core.content.edit
import com.google.gson.Gson
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.BuildConfig
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.R
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.database.GlucoseValue
import de.michelinside.glucodatahandler.common.database.dbAccess
import de.michelinside.glucodatahandler.common.notifier.DataSource
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.tasks.yuwell.DataBean
import de.michelinside.glucodatahandler.common.tasks.yuwell.encryption.AESTools
import de.michelinside.glucodatahandler.common.tasks.yuwell.encryption.AESTools.parse
import de.michelinside.glucodatahandler.common.tasks.yuwell.encryption.Base64Tools
import de.michelinside.glucodatahandler.common.utils.Log
import de.michelinside.glucodatahandler.common.utils.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import kotlin.math.max

class YuwellSourceTask() : MultiPatientSourceTask(Constants.SHARED_PREF_YUWELL_ENABLED, DataSource.YUWELL) {
    override val LOG_ID = "GDH.Task.Source.Yuwell"
    override var minInterval = 3
    override val patientIdKey = Constants.SHARED_PREF_YUWELL_PATIENT_ID
    companion object {
        private var instance: YuwellSourceTask? = null
        private var user = ""
        private var password = ""
        private var reconnect = false
        private var token = ""          // valid for 7 days
        private var tokenExpire = 0L
        private var refreshToken = ""   // valid for 4 weeks
        private var refreshTokenExpire = 0L
        private var userId = ""
        private var lastGlucoseId = -1
        private var lastBgICount = -1
        private var dataReceived = false   // mark this endpoint as already received data
        private var lastPatientResponse: String? = null
        private var forceLogoutError = false
        val patientData: MutableMap<String, String> get() {
            if(instance == null)
                return mutableMapOf()
            return instance!!.patientData
        }
        const val server = "https://anyapi-de.yuwell-poctech.com/NonCETestHttpOverseas/PostHttpServlet"
        private var androidIdData = ""
        private val androidId: String @SuppressLint("HardwareIds")
        get() {
            if(androidIdData.isEmpty()) {
                androidIdData = android.provider.Settings.Secure.getString(
                    GlucoDataService.context?.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                ) ?: "unknown"
            }
            return androidIdData
        }
    }

    enum class RequestType(val id: Int) {
        LOGIN(2),
        LOGOUT(3),
        FORCE_LOGOUT(5),
        FOLLOW(14),
        GLUCOSE_VALUES(15),
        REFRESH_TOKEN(23)
    }

    init {
        Log.i(LOG_ID, "init called")
        instance = this
    }

    private fun getHeader(): MutableMap<String, String> {
        val result = mutableMapOf(
            "User-Agent" to "okhttp/4.8.1",
            "Content-Type" to "application/json; charset=utf-8",
            "Accept-Encoding" to "gzip",
            "Accept-Language" to "en-US",
            "Host" to "anyapi-de.yuwell-poctech.com"
        )
        if(token.isNotEmpty()) {
            result["expectedGrantType"] = "password"
            result["token"] = token
        }
        Log.v(LOG_ID, "Header: ${result}")
        return result
    }

    override fun reset() {
        Log.i(LOG_ID, "reset called")
        super.reset()
        token = ""
        tokenExpire = 0L
        refreshToken = ""
        refreshTokenExpire = 0L
        userId = ""
        dataReceived = false
        lastGlucoseId = -1
        lastBgICount = -1
        try {
            Log.d(LOG_ID, "Save reset")
            GlucoDataService.sharedExtraPref!!.edit {
                putString(Constants.SHARED_PREF_YUWELL_TOKEN, token)
                putLong(Constants.SHARED_PREF_YUWELL_TOKEN_EXPIRE, tokenExpire)
                putString(Constants.SHARED_PREF_YUWELL_REFRESH_TOKEN, refreshToken)
                putLong(Constants.SHARED_PREF_YUWELL_REFRESH_TOKEN_EXPIRE, refreshTokenExpire)
                putString(Constants.SHARED_PREF_YUWELL_USER_ID, userId)
                putInt(Constants.SHARED_PREF_YUWELL_LAST_GLUCOSE_ID, lastGlucoseId)
                putInt(Constants.SHARED_PREF_YUWELL_LAST_BG_I_COUNT, lastBgICount)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "save reset exception: " + exc.toString() )
        }
    }

    @SuppressLint("HardwareIds")
    override fun authenticate(): Boolean {
        lastPatientResponse = null
        forceLogoutError = false
        if (token.isNotEmpty() && reconnect) {
            reset()
            if (reconnect) {
                reconnect = false
                GlucoDataService.sharedPref!!.edit {
                    putBoolean(Constants.SHARED_PREF_YUWELL_RECONNECT, false)
                }
            }
        }
        if (token.isEmpty()) {
            val json = JSONObject()
            json.put("clientId", BuildConfig.YUWELL_CLIENT_ID)
            json.put("clientSecret", BuildConfig.YUWELL_CLIENT_SECRET)
            json.put("mac", androidId)
            json.put("email", user)
            json.put("password", password)
            Log.v(LOG_ID, "Login request: $json")
            val data = encrypt(RequestType.LOGIN, json.toString())?: return false
            Log.v(LOG_ID, "Login request encoded: $data")
            return handleLoginResponse(httpPost(server, getHeader(), data))
        }
        if(tokenExpire <= (System.currentTimeMillis() - 60000)) {
            Log.i(LOG_ID, "Token expired - refresh token")
            return refreshToken()
        }
        return true
    }

    override fun getPatients(): MutableMap<String, String>? {
        Log.v(LOG_ID, "Get patients request")
        val data = encrypt(RequestType.FOLLOW, createUserIdRequest()) ?: return null
        lastPatientResponse = httpPost(server, getHeader(), data)
        val jsonObject = checkResponse(lastPatientResponse)
        if(jsonObject != null && jsonObject.has("data")) {
           return getPatientData(jsonObject.optJSONArray("data"))
        }
        return null
    }

    private fun getPatientData(dataArray: JSONArray?): MutableMap<String, String> {
        if(dataArray == null || dataArray.length() == 0) {
            return mutableMapOf()
        }
        Log.i(LOG_ID, "Handle ${dataArray.length()} patients")
        val newPatientData = mutableMapOf<String, String>()
        for (i in 0 until dataArray.length()) {
            val data = dataArray.getJSONObject(i)
            if(data.has("email")) {
                val id = data.getString("email")
                var name = data.optString("nickName", "")
                if(name.isNullOrEmpty()) {
                    name = id
                }
                Log.v(LOG_ID, "New patient found: $name")
                newPatientData[id] = name.trim()
            }
        }
        return newPatientData
    }


    override fun getPatientValue(patientId: String): Boolean {
        Log.d(LOG_ID, "Get value for patient ${getPatient(patientId)}")

        // if patient user is set and not graph data is needed, only get login data with current value
        // else get login data for patient data and additional get graph data
        val firstNeededValue = getFirstNeedGraphValueTime()
        val minutes = if(firstNeededValue > 0) Utils.getElapsedTimeMinute(firstNeededValue) else 0
        Log.d(LOG_ID, "Get data for last $minutes minutes")

        val wearingRecordId = if(lastPatientResponse.isNullOrEmpty()) {
            val followRequest = encrypt(RequestType.FOLLOW, createUserIdRequest()) ?: return false
            handleFollowResponse(patientId, minutes, httpPost(server, getHeader(), followRequest))
        } else {
            handleFollowResponse(patientId, minutes, lastPatientResponse)
        }

        if(wearingRecordId.isNullOrEmpty())
            return false

        if(firstNeededValue > ReceiveData.time) {
            Log.v(LOG_ID, "No new value available")
            return true // no new value
        }

        if(minutes <= minInterval)
            return true

        // else get graph data for current patient
        Log.d(LOG_ID, "Get value for patient ${getPatient(patientId)} - last glucose id: $lastGlucoseId")
        val json = JSONObject()
        json.put("userId", userId)
        json.put("glucoseId", lastGlucoseId+1)
        json.put("wearingRecordId", wearingRecordId)
        Log.v(LOG_ID, "Value request: $json")
        val valueRequest = encrypt(RequestType.GLUCOSE_VALUES, json.toString())?: return false
        return handleValuesResponse(httpPost(server, getHeader(), valueRequest), firstNeededValue)
    }

    val sensitivData = mutableSetOf("password", "userUID", "refreshToken", "userId", "userEmail", "email", "clientId", "clientSecret", "mac", "userIdSubscribe")

    private fun replaceSensitiveData(body: String): String {
        try {
            var result = body.take(1100)
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

    private fun encrypt(type: RequestType, body: String?): String? {
        val key = AESTools.generateKey()
        if(key == null || body == null)
            return null
        val jsonObject = JSONObject()
        jsonObject.put("id", type.id)
        jsonObject.put("body", Base64Tools.format(AESTools.format(body, key)))
        jsonObject.put("key", Base64Tools.format(key))
        return jsonObject.toString()
    }

    private fun decrypt(dataBean: DataBean): String? {
        if(dataBean.key.isEmpty() || dataBean.body.isEmpty()) {
            Log.e(LOG_ID, "Invalid response received: key=${dataBean.key} - body=${dataBean.body.take(100)}")
            setLastError("Invalid response received!")
            return null
        }

        val decryptedBody = dataBean.body.replace("\n", "")
        val decryptedKey = dataBean.key.replace("\n", "")

        val data = Base64.getDecoder().decode(decryptedBody)
        val key = Base64.getDecoder().decode(decryptedKey)

        return parse(data, key)
    }

    private fun getErrorMessage(jsonObj: JSONObject): String {
        if(jsonObj.has("message")) {
            return jsonObj.getString("message")
        }
        return "Error"
    }

    private fun checkResponse(body: String?): JSONObject? {
        if (body.isNullOrEmpty()) {
            return null
        }
        Log.v(LOG_ID, "Handle json response: ${body.take(2000)}")
        val gson = Gson()
        val beanResponse = gson.fromJson(body, DataBean::class.java)

        val response = decrypt(beanResponse)
        if (response.isNullOrEmpty()) {
            return null
        }
        Log.i(LOG_ID, "Handle json response for id ${beanResponse.id}: " + replaceSensitiveData(response))
        val jsonObj = JSONObject(response)
        if(jsonObj.has("code")) {
            val code = jsonObj.getInt("code")
            if(code != 200) {
                if(code == 301) {
                    if(refreshToken()) {
                        retry = true
                        return null
                    }
                }
                if(code == 303) {
                    Log.w(LOG_ID, "User was logged out - disable it")
                    reset()
                    forceLogoutError = true
                    GlucoDataService.sharedPref!!.edit {
                        putBoolean(Constants.SHARED_PREF_YUWELL_ENABLED, false)
                    }
                    retry = false
                    return null
                }
                if(code == 423) {
                    Log.w(LOG_ID, "Too many requests!")
                    retry = false
                    return null
                }
                setLastError(getErrorMessage(jsonObj), code)
                reset()
                retry = true
                return null
            }
        }
        return jsonObj
    }

    private fun refreshToken(): Boolean {
        if(refreshToken.isEmpty() || userId.isEmpty())
            return false
        Log.d(LOG_ID, "Refresh token")
        val json = JSONObject()
        json.put("userId", userId)
        json.put("refreshToken", refreshToken)
        json.put("clientId", BuildConfig.YUWELL_CLIENT_ID)
        json.put("clientSecret", BuildConfig.YUWELL_CLIENT_SECRET)
        json.put("password", password)
        Log.v(LOG_ID, "Login request: $json")
        val data = encrypt(RequestType.REFRESH_TOKEN, json.toString())?: return false
        return handleRefreshTokenResponse(httpPost(server, getHeader(), data))
    }

    private fun handleRefreshTokenResponse(body: String?): Boolean {
        val jsonObj = checkResponse(body)?: return false
        if(!jsonObj.has("data")) {
            setLastError("Invalid respone received!")
            return false
        }
        val accessToken = jsonObj.getJSONObject("data")

        if(!accessToken.has("accessToken") || !accessToken.has("refreshToken")) {
            setLastError("Invalid login-response received!")
            return false
        }
        token = accessToken.getString("accessToken")
        tokenExpire = accessToken.getLong("expiresAt")
        refreshToken = accessToken.getString("refreshToken")
        refreshTokenExpire = accessToken.getLong("refreshExpiresAt")
        Log.d(LOG_ID, "Token expires at ${Utils.getUiTimeStamp(tokenExpire)} - refresh token expires at ${Utils.getUiTimeStamp(refreshTokenExpire)}")
        GlucoDataService.sharedExtraPref!!.edit {
            putString(Constants.SHARED_PREF_YUWELL_TOKEN, token)
            putLong(Constants.SHARED_PREF_YUWELL_TOKEN_EXPIRE, tokenExpire)
            putString(Constants.SHARED_PREF_YUWELL_REFRESH_TOKEN, refreshToken)
            putLong(Constants.SHARED_PREF_YUWELL_REFRESH_TOKEN_EXPIRE, refreshTokenExpire)
        }
        return !token.isEmpty()
    }

    private fun handleLoginResponse(body: String?): Boolean {
        val jsonObj = checkResponse(body)?: return false
        if(!jsonObj.has("data")) {
            setLastError("Invalid login-response received!")
            return false
        }
        val data = jsonObj.getJSONObject("data")
        if(!data.has("accessToken") || !data.has("user")) {
            setLastError("Invalid login-response received!")
            return false
        }
        val accessToken = data.getJSONObject("accessToken")
        val user = data.getJSONObject("user")

        if(!accessToken.has("accessToken") || !accessToken.has("refreshToken") || !user.has("userId")) {
            setLastError("Invalid login-response received!")
            return false
        }

        userId = user.getString("userId")
        token = accessToken.getString("accessToken")
        tokenExpire = accessToken.getLong("expiresAt")
        refreshToken = accessToken.getString("refreshToken")
        refreshTokenExpire = accessToken.getLong("refreshExpiresAt")
        Log.d(LOG_ID, "Token expires at ${Utils.getUiTimeStamp(tokenExpire)} - refresh token expires at ${Utils.getUiTimeStamp(refreshTokenExpire)}")
        GlucoDataService.sharedExtraPref!!.edit {
            putString(Constants.SHARED_PREF_YUWELL_TOKEN, token)
            putLong(Constants.SHARED_PREF_YUWELL_TOKEN_EXPIRE, tokenExpire)
            putString(Constants.SHARED_PREF_YUWELL_REFRESH_TOKEN, refreshToken)
            putLong(Constants.SHARED_PREF_YUWELL_REFRESH_TOKEN_EXPIRE, refreshTokenExpire)
            putString(Constants.SHARED_PREF_YUWELL_USER_ID, userId)
        }
        return !token.isEmpty()
    }


    private fun handleFollowResponse(patientId: String, minutes: Long, body: String?): String? {
        val jsonObj = checkResponse(body) ?: return null
        if(jsonObj.has("data")) {
            val dataArray = jsonObj.optJSONArray("data")
            if(dataArray != null && dataArray.length() > 0) {
                Log.d(LOG_ID, "Handle ${dataArray.length()} patients")
                val newPatientData = mutableMapOf<String, String>()
                for (i in 0 until dataArray.length()) {
                    val data = dataArray.getJSONObject(i)
                    if(data.has("wearingRecordId") && data.has("email")) {
                        val id = data.getString("email")
                        if(id != patientId)
                            continue
                        val timestamp = data.getLong("time")
                        val value = data.getInt("gluMgValue")
                        val trend = data.getInt("trend")
                        val customValue = data.getDouble("glucose").toFloat()
                        val wearingRecordId = data.optString("wearingRecordId")

                        val glucoExtras = Bundle()
                        glucoExtras.putLong(ReceiveData.TIME, timestamp)
                        glucoExtras.putFloat(ReceiveData.GLUCOSECUSTOM, customValue)
                        glucoExtras.putInt(ReceiveData.MGDL, value)
                        glucoExtras.putFloat(ReceiveData.RATE, getRateFromTrend(trend))
                        if(!wearingRecordId.isNullOrEmpty()) {
                            val result = wearingRecordId.split("_")
                            if(result.size == 2) {
                                val applicatorId = result[0]
                                val startTime = result[1].toLong()
                                glucoExtras.putString(ReceiveData.SERIAL, applicatorId)
                                glucoExtras.putLong(ReceiveData.SENSOR_START_TIME, startTime)
                                if(applicatorId != ReceiveData.sensorID || startTime != ReceiveData.sensorStartTime) {
                                    Log.i(LOG_ID, "New sensor detected, reset last values")
                                    lastGlucoseId = -1
                                    lastBgICount = -1
                                }
                            }
                        }
                        handleResult(glucoExtras)
                        if(data.has("bgICount")) {
                            val bgICount = data.getInt("bgICount")
                            if(bgICount < lastBgICount) {
                                Log.i(LOG_ID, "New BG I count detected, reset last values")
                                lastGlucoseId = -1
                                lastBgICount = -1
                            }
                            var changed = false
                            if(lastGlucoseId <= 0) {
                                lastGlucoseId = max( -1, bgICount-(minutes/3).toInt())
                                lastBgICount = bgICount
                                changed = true
                            } else if(lastBgICount != bgICount) {
                                if(lastBgICount in 1..<bgICount && minutes<=minInterval) {
                                    lastGlucoseId += bgICount-lastBgICount
                                }
                                lastBgICount = bgICount
                                changed = true
                            }
                            if(changed) {
                                Log.i(LOG_ID, "Setting last glucose id: $lastGlucoseId and lastBgICount $lastBgICount - minutes: $minutes")
                                GlucoDataService.sharedExtraPref!!.edit {
                                    putInt(Constants.SHARED_PREF_YUWELL_LAST_GLUCOSE_ID, lastGlucoseId)
                                    putInt(Constants.SHARED_PREF_YUWELL_LAST_BG_I_COUNT, lastBgICount)
                                }
                            }
                        }
                        return wearingRecordId
                    }
                }
            }
            if(dataArray != null && dataArray.length() > 0) {
                Log.e(LOG_ID, "Patient not found in response, try to handle current patients")
                retry = handlePatientData(getPatientData(dataArray))
                return null
            }
        }
        setLastError("Patient data not found on server!")
        return null
    }

    private fun handleValuesResponse(body: String?, firstNeededValue: Long): Boolean {
        val jsonObj = checkResponse(body)?: return false
        if(!jsonObj.has("data")) {
            setLastError("Invalid value-response received!")
            return false
        }
        val data = jsonObj.getJSONObject("data")
        if(!data.has("glucoseData")) {
            setLastError("Invalid value-response received!")
            return false
        }

        val glucoseData = data.optJSONArray("glucoseData")
        if(glucoseData == null || glucoseData.length() == 0) {
            return true
        }

        val values = mutableListOf<GlucoseValue>()
        var lastValueTime = ReceiveData.time
        var ignoreCount = 0L
        val curLastGlucoseId = lastGlucoseId
        for (i in 0 until glucoseData.length()) {
            val glucoseValue = glucoseData.optJSONObject(i)
            if(glucoseValue != null && glucoseValue.has("glucoseId") && glucoseValue.has("glucoseDate") && glucoseValue.has("glucoseValueMg")) {
                try {
                    val glucoseId = glucoseValue.getInt("glucoseId")
                    if(glucoseId > lastGlucoseId) {
                        lastGlucoseId = glucoseId
                    }
                    val timestamp = glucoseValue.getLong("glucoseDate")
                    if(timestamp >= firstNeededValue) {
                        val value = glucoseValue.getInt("glucoseValueMg")
                        values.add(GlucoseValue(timestamp, value))
                        if(timestamp > lastValueTime ) {
                            lastValueTime = timestamp
                        }
                    } else {
                        ignoreCount++
                    }
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Handle exception for value $glucoseValue: " + exc.toString() )
                }
            }
        }
        if(curLastGlucoseId != lastGlucoseId) {
            GlucoDataService.sharedExtraPref!!.edit {
                putInt(Constants.SHARED_PREF_YUWELL_LAST_GLUCOSE_ID, lastGlucoseId)
            }
        }
        Log.d(LOG_ID, "Add ${values.size} values to db - ignored: $ignoreCount")
        dbAccess.addGlucoseValues(values)
        return true
    }

    private fun getRateFromTrend(trend: Int): Float {
        if(trend == 10)
            return Float.NaN
        return trend.toFloat()
    }

    private fun createUserIdRequest(): String? {
        if(userId.isNullOrEmpty())
            return null
        val json = JSONObject()
        json.put("userId", userId)
        return json.toString()
    }

    override fun disable() {
        try {
            if(forceLogoutError) {
                setLastError("Error", 303, GlucoDataService.context!!.getString(R.string.src_yuwell_logout_error))
                return
            }
            Log.i(LOG_ID, "Disable called - trigger logout")
            val data = encrypt(RequestType.LOGOUT, createUserIdRequest()) ?: return
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                Log.v(LOG_ID, "Logout request: $data")
                checkResponse(httpPost(server, getHeader(), data))
                reset()
                // todo: check in authenticate, if logout is still active!
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Logout exception: " + exc.toString() )
        }
        reset()
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
            user = sharedPreferences.getString(Constants.SHARED_PREF_YUWELL_USER, "")!!.trim()
            password = sharedPreferences.getString(Constants.SHARED_PREF_YUWELL_PASSWORD, "")!!.trim()
            // internal stuff from extra-pref
            token = GlucoDataService.sharedExtraPref!!.getString(Constants.SHARED_PREF_YUWELL_TOKEN, "")!!
            if(token.isNotEmpty()) {
                dataReceived = true
            }
            tokenExpire = GlucoDataService.sharedExtraPref!!.getLong(Constants.SHARED_PREF_YUWELL_TOKEN_EXPIRE, 0L)
            refreshToken = GlucoDataService.sharedExtraPref!!.getString(Constants.SHARED_PREF_YUWELL_REFRESH_TOKEN, "")!!
            refreshTokenExpire = GlucoDataService.sharedExtraPref!!.getLong(Constants.SHARED_PREF_YUWELL_REFRESH_TOKEN_EXPIRE, 0L)
            userId = GlucoDataService.sharedExtraPref!!.getString(Constants.SHARED_PREF_YUWELL_USER_ID, "")!!
            lastGlucoseId = GlucoDataService.sharedExtraPref!!.getInt(Constants.SHARED_PREF_YUWELL_LAST_GLUCOSE_ID, -1)
            lastBgICount = GlucoDataService.sharedExtraPref!!.getInt(Constants.SHARED_PREF_YUWELL_LAST_BG_I_COUNT, -1)
            Log.d(LOG_ID, "Using lastGlucoseId: $lastGlucoseId and lastBgICount $lastBgICount")
            InternalNotifier.notify(GlucoDataService.context!!, NotifySource.SOURCE_STATE_CHANGE, null)
            trigger = true
        } else {
            when(key) {
                Constants.SHARED_PREF_YUWELL_USER -> {
                    if (user != sharedPreferences.getString(Constants.SHARED_PREF_YUWELL_USER, "")) {
                        user = sharedPreferences.getString(Constants.SHARED_PREF_YUWELL_USER, "")!!.trim()
                        reset()
                        InternalNotifier.notify(GlucoDataService.context!!, NotifySource.SOURCE_STATE_CHANGE, null)
                        trigger = true
                    }
                }
                Constants.SHARED_PREF_YUWELL_PASSWORD -> {
                    if (password != sharedPreferences.getString(Constants.SHARED_PREF_YUWELL_PASSWORD, "")) {
                        password = sharedPreferences.getString(Constants.SHARED_PREF_YUWELL_PASSWORD, "")!!.trim()
                        reset()
                        InternalNotifier.notify(GlucoDataService.context!!, NotifySource.SOURCE_STATE_CHANGE, null)
                        trigger = true
                    }
                }
                Constants.SHARED_PREF_YUWELL_RECONNECT -> {
                    if (reconnect != sharedPreferences.getBoolean(Constants.SHARED_PREF_YUWELL_RECONNECT, false)) {
                        reconnect = sharedPreferences.getBoolean(Constants.SHARED_PREF_YUWELL_RECONNECT, false)
                        Log.d(LOG_ID, "Reconnect triggered")
                        trigger = true
                    }
                }
            }
        }
        return super.checkPreferenceChanged(sharedPreferences, key, context) || trigger
    }
}
