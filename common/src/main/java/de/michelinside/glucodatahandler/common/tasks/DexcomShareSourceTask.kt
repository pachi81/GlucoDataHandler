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
import de.michelinside.glucodatahandler.common.SourceState
import de.michelinside.glucodatahandler.common.database.GlucoseValue
import de.michelinside.glucodatahandler.common.database.dbAccess
import de.michelinside.glucodatahandler.common.notifier.DataSource
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.GlucoDataUtils
import de.michelinside.glucodatahandler.common.utils.Utils
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection


// API docu: https://gist.github.com/StephenBlackWasAlreadyTaken/adb0525344bedade1e25

class DexcomShareSourceTask : DataSourceTask(Constants.SHARED_PREF_DEXCOM_SHARE_ENABLED, DataSource.DEXCOM_SHARE) {
    private val LOG_ID = "GDH.Task.Source.DexcomShareTask"
    companion object {
        private var user = ""
        private var password = ""
        private var reconnect = false
        private var use_us_server = false
        private var sessionId: String? = null

        const val URL_DEFAULT = "https://shareous1.dexcom.com"
        const val URL_US = "https://share2.dexcom.com"
        const val USER_AGENT = "Dexcom Share/3.0.2.11 CFNetwork/711.2.23 Darwin/14.0.0"
        const val DEXCOM_PATH_GET_VALUE = "/ShareWebServices/Services/Publisher/ReadPublisherLatestGlucoseValues"
        const val DEXCOM_PATH_GET_SESSION_ID = "/ShareWebServices/Services/General/LoginPublisherAccountById"
        const val DEXCOM_PATH_AUTHENTICATE = "/ShareWebServices/Services/General/AuthenticatePublisherAccount"

        private const val INVALID_ID = "00000000-0000-0000-0000-000000000000"
    }

    private fun getUrl(endpoint: String, queryParameters: String? = null): String {
        var url = (if(use_us_server) URL_US else URL_DEFAULT) + endpoint
        Log.i(LOG_ID, "Using URL: " + url)
        if(!queryParameters.isNullOrEmpty()) {
            url += "?$queryParameters"
        }
        return url
    }

    private fun getHeader(withContent: Boolean = true): MutableMap<String, String> {
        val result = mutableMapOf(
            "user-agent" to USER_AGENT,
            "accept" to "application/json",
            "content-type" to "application/json"
        )
        if(!withContent) {
            result["Content-Length"] = "0"
        }
        return result
    }

    override fun reset() {
        Log.i(LOG_ID, "reset called")
        sessionId = null
        if (reconnect) {
            reconnect = false
            with(GlucoDataService.sharedPref!!.edit()) {
                putBoolean(Constants.SHARED_PREF_DEXCOM_SHARE_RECONNECT, false)
                apply()
            }
        }
    }

    override fun getValue(): Boolean {
        Log.v(LOG_ID, "getValue called")
        if(sessionId.isNullOrEmpty())
            return false
        var query = "sessionId=${sessionId}"
        val firstValueTime = getFirstNeedGraphValueTime()
        val count = if(firstValueTime > 0) Utils.getElapsedTimeMinute(firstValueTime) else 1
        if(count > 5)   // Dexcom has 5 minute interval
            query += "&minutes=${count}&maxCount=${count}"
        else
            query += "&minutes=30&maxCount=1"
        return handleValueResponse(httpPost(getUrl(DEXCOM_PATH_GET_VALUE, query), getHeader(false), ""), firstValueTime)
    }

    override fun authenticate() : Boolean {
        if(reconnect)
            reset()
        Log.v(LOG_ID, "authenticate called - sessionId: $sessionId")
        if(!sessionId.isNullOrEmpty())
            return true

        val json = JSONObject()
        json.put("accountName", user)
        json.put("password", password)
        json.put("applicationId", BuildConfig.DEXCOM_APPLICATION_ID)
        val accountId = handleIdResponse(httpPost(getUrl(DEXCOM_PATH_AUTHENTICATE), getHeader(), json.toString()))
        return getSessionId(accountId)
    }

    override fun checkErrorResponse(code: Int, message: String?, errorResponse: String?) {
        Log.e(LOG_ID, "Error $code received: $message - $errorResponse")
        if (code == HttpURLConnection.HTTP_INTERNAL_ERROR && errorResponse != null) {
            val obj = JSONObject(errorResponse)
            val errCode: String = obj.optString("Code")
            when(errCode) {
                "SessionNotValid",
                "SessionIdNotFound" -> {
                    reset()
                    if(firstGetValue) {
                        retry = true
                        return
                    }
                }
            }
            val errMessage: String = obj.optString("Message")
            setLastError("${errCode}: $errMessage", code)
        } else {
            super.checkErrorResponse(code, message, errorResponse)
        }
    }

    private fun handleValueResponse(body: String?, firstValueTime: Long = 0): Boolean {
        Log.i(LOG_ID, "handleValueResponse called: $body")
        if (body == null) {
            return false
        }
        if (body.isEmpty()) {
            if(BuildConfig.DEBUG) {
                return handleValueResponse("[\n" +
                        "                {\n" +
                        "                    \"WT\":\"Date(${System.currentTimeMillis()})\",\n" +
                        "                    \"ST\":\"Date(1638448498000)\",\n" +
                        "                    \"DT\":\"Date(1638448498000+0000)\",\n" +
                        "                    \"Value\":120,\n" +
                        "                    \"Trend\":\"FortyFiveUp\"\n" +
                        "                }\n" +
                        "            ]"
                )
            } else {
                setLastError("Missing data in response!", 500)
                reset()
                return false
            }
        }
        /*
            Array of:
             String DT; // device time
             String ST; // system (share) time
             String WT; //  World time / GMT
             double Trend; // 1-7
             double Value; // mg/dL
         */
        /*
            [
                {
                    "DT":"/Date(1638392742000)/",
                    "ST":"/Date(1638392742000)/",
                    "Trend":4,
                    "Value":193
                }
            ]
         */

        /*
            new format (Trend as String):
            [
                {
                    "WT":"Date(1638448498000)",
                    "ST":"Date(1638448498000)",
                    "DT":"Date(1638448498000+0000)",
                    "Value":235,
                    "Trend":"Flat"
                }
            ]
         */

        val dataArray = JSONArray(body)
        if(dataArray.length() == 0) {
            setState(SourceState.NO_NEW_VALUE, GlucoDataService.context!!.resources.getString(R.string.no_data_in_server_response))
            return false
        }
        var lastValueIndex = 0
        if(dataArray.length() > 1) {
            val lastTime = 0L
            Log.d(LOG_ID, "Handle ${dataArray.length()} entries in response as graph data - ${body.take(1000)}")
            val values = mutableListOf<GlucoseValue>()
            for(i in 0 until dataArray.length()) {
                try {
                    val data = dataArray.getJSONObject(i)
                    if(data.has("Value") && data.has("WT")) {
                        val value = data.getInt("Value")
                        val re = Regex("[^0-9]")
                        val worldTime = re.replace(data.getString("WT"), "").toLong()
                        if(worldTime < firstValueTime)
                            continue
                        values.add(GlucoseValue(worldTime, value))
                        if(worldTime > lastTime)
                            lastValueIndex = i
                    }
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Exception while parsing entry response: " + exc.message)
                }
            }
            Log.i(LOG_ID, "Add ${values.size} values to database")
            dbAccess.addGlucoseValues(values)
        }
        val data = dataArray.getJSONObject(lastValueIndex)
        if(data.has("Value") && data.has("Trend") && data.has("WT")) {
            val glucoExtras = Bundle()
            glucoExtras.putInt(ReceiveData.MGDL, data.getInt("Value"))
            val re = Regex("[^0-9]")
            val worldTime: String = re.replace(data.getString("WT"), "")
            glucoExtras.putLong(ReceiveData.TIME, worldTime.toLong())
            val trendInt = data.optInt("Trend")
            if(trendInt != 0) {
                glucoExtras.putFloat(ReceiveData.RATE, getRateFromTrend(trendInt))
            } else {
                glucoExtras.putFloat(ReceiveData.RATE, GlucoDataUtils.getRateFromLabel(data.getString("Trend")))
            }
            handleResult(glucoExtras)
            return true
        } else {
            setLastError("Invalid response data")
            Log.w(LOG_ID, "Unsupported format: $data")
        }
        return false
    }

    private fun handleIdResponse(body: String?): String? {
        Log.v(LOG_ID, "handleLoginResponse called for body $body")
        if(body != null) {
            val id = body.replace("\"", "")
            /*if(BuildConfig.DEBUG)
                return id*/
            if (id != INVALID_ID) {
                return id
            }
            setLastError("Invalid user ID received!")
            Log.w(LOG_ID, "Invalid ID received: $id")
        }
        return null
    }

    private fun getSessionId(accountId: String?) : Boolean {
        Log.v(LOG_ID, "getSessionId called for accountId $accountId")
        if(accountId == null)
            return false
        val json = JSONObject()
        json.put("accountId", accountId)
        json.put("password", password)
        json.put("applicationId", BuildConfig.DEXCOM_APPLICATION_ID)
        sessionId = handleIdResponse(httpPost(getUrl(DEXCOM_PATH_GET_SESSION_ID), getHeader(), json.toString()))
        Log.d(LOG_ID, "Using sessionId: $sessionId")
        return !sessionId.isNullOrEmpty()
    }

    private fun getRateFromTrend(trend: Int): Float {
        return when(trend) {
            1 -> 3F
            2 -> 2F
            3 -> 1F
            4 -> 0F
            5 -> -1F
            6 -> -2F
            7 -> -3F
            else -> Float.NaN
        }
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
            user = sharedPreferences.getString(Constants.SHARED_PREF_DEXCOM_SHARE_USER, "")!!.trim()
            password = sharedPreferences.getString(Constants.SHARED_PREF_DEXCOM_SHARE_PASSWORD, "")!!.trim()
            use_us_server = sharedPreferences.getBoolean(Constants.SHARED_PREF_DEXCOM_SHARE_USE_US_URL, false)
            InternalNotifier.notify(GlucoDataService.context!!, NotifySource.SOURCE_STATE_CHANGE, null)
            trigger = true
        } else {
            when(key) {
                Constants.SHARED_PREF_DEXCOM_SHARE_USER -> {
                    if (user != sharedPreferences.getString(Constants.SHARED_PREF_DEXCOM_SHARE_USER, "")) {
                        user = sharedPreferences.getString(Constants.SHARED_PREF_DEXCOM_SHARE_USER, "")!!.trim()
                        reset()
                        InternalNotifier.notify(GlucoDataService.context!!, NotifySource.SOURCE_STATE_CHANGE, null)
                        trigger = true
                    }
                }
                Constants.SHARED_PREF_DEXCOM_SHARE_PASSWORD -> {
                    if (password != sharedPreferences.getString(Constants.SHARED_PREF_DEXCOM_SHARE_PASSWORD, "")) {
                        password = sharedPreferences.getString(Constants.SHARED_PREF_DEXCOM_SHARE_PASSWORD, "")!!.trim()
                        reset()
                        InternalNotifier.notify(GlucoDataService.context!!, NotifySource.SOURCE_STATE_CHANGE, null)
                        trigger = true
                    }
                }
                Constants.SHARED_PREF_DEXCOM_SHARE_USE_US_URL -> {
                    if (use_us_server != sharedPreferences.getBoolean(Constants.SHARED_PREF_DEXCOM_SHARE_USE_US_URL, false)) {
                        use_us_server = sharedPreferences.getBoolean(Constants.SHARED_PREF_DEXCOM_SHARE_USE_US_URL, false)
                        InternalNotifier.notify(GlucoDataService.context!!, NotifySource.SOURCE_STATE_CHANGE, null)
                        trigger = true
                    }
                }
                Constants.SHARED_PREF_DEXCOM_SHARE_RECONNECT -> {
                    if (reconnect != sharedPreferences.getBoolean(Constants.SHARED_PREF_DEXCOM_SHARE_RECONNECT, false)) {
                        reconnect = sharedPreferences.getBoolean(Constants.SHARED_PREF_DEXCOM_SHARE_RECONNECT, false)
                        Log.d(LOG_ID, "Reconnect triggered")
                        trigger = true
                    }
                }
            }
        }
        return super.checkPreferenceChanged(sharedPreferences, key, context) || trigger
    }
}
