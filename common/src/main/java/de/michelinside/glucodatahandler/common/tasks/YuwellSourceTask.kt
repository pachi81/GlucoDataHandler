package de.michelinside.glucodatahandler.common.tasks

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
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
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.content.edit


// python example: https://gist.github.com/bruderjakob12/b492e5c0b32e421d6fc9ee6f86d5f2dd

class YuwellSourceTask() : MultiPatientSourceTask(Constants.SHARED_PREF_YUWELL_ENABLED, DataSource.YUWELL) {
    override val LOG_ID = "GDH.Task.Source.Yuwell"
    override var minInterval = 2L
    override val patientIdKey = Constants.SHARED_PREF_YUWELL_PATIENT_ID
    companion object {
        private var instance: YuwellSourceTask? = null
        private var user = ""
        private var password = ""
        private var reconnect = false
        private var token = ""
        private var refreshToken = ""
        private var dataReceived = false   // mark this endpoint as already received data
        val patientData: MutableMap<String, String> get() {
            if(instance == null)
                return mutableMapOf()
            return instance!!.patientData
        }
        const val server = "https://easyview.yuwell.%s"
        const val LOGIN_ENDPOINT = "/mobile/ajax/login"
    }

    init {
        Log.i(LOG_ID, "init called")
        instance = this
    }

    private fun getUrl(endpoint: String): String {
        val url = server + endpoint
        Log.i(LOG_ID, "Using URL: " + url)
        return url
    }

    private fun getHeader(): MutableMap<String, String> {
        val result = mutableMapOf(
            "User-Agent" to "okhttp/3.5.0"
        )
        Log.v(LOG_ID, "Header: ${result}")
        return result
    }

    override fun reset() {
        Log.i(LOG_ID, "reset called")
        super.reset()
        token = ""
        refreshToken = ""
        dataReceived = false
        try {
            Log.d(LOG_ID, "Save reset")
            GlucoDataService.sharedPref!!.edit {
                putString(Constants.SHARED_PREF_YUWELL_TOKEN, token)
                putString(Constants.SHARED_PREF_YUWELL_REFRESH_TOKEN, refreshToken)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "save reset exception: " + exc.toString() )
        }
    }


    override fun authenticate(): Boolean {
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
            val data = "apptype=Follow&user_type=M&platform=google&user_name=$user&password=$password"
            /*json.put("user_name", user)
            json.put("password", password)
            json.put("platform", "google")
            //json.put("push_platform", "google")
            json.put("user_type", "M")
            json.put("app_version", "1.2.70(112)")
            json.put("bundleID", "com.yuwell.easyfollowforandroidmg")
            json.put("device_name", Build.MODEL)*/
            //json.put("deviceToken", Firebase token...)
            if(!handleLoginResponse(httpPost(getUrl(LOGIN_ENDPOINT), getHeader(), data)))
                return false
        }
        return true
    }

    override fun getPatients(): MutableMap<String, String>? {
        // TODO
        return null
    }

    override fun getPatientValue(patientId: String): Boolean {
        Log.d(LOG_ID, "Get value for patient ${getPatient(patientId)}")

        // if patient user is set and not graph data is needed, only get login data with current value
        // else get login data for patient data and addtional get graph data
        val firstNeededValue = getFirstNeedGraphValueTime()
        val minutes = if(firstNeededValue > 0) Utils.getElapsedTimeMinute(firstNeededValue) else 0
        Log.d(LOG_ID, "Get data for last $minutes minutes")
        // TODO handleMonitorListResponse(patientId, httpGet(getUrl(MONITORLIST_ENDPOINT), getHeader()))
        if(minutes <= 2L)
            return true
        // else get graph data for current patient
        // TODO: handleGraphDataResponse(httpGet(getGraphUrl(patientId, firstNeededValue), getHeader()), firstNeededValue)
        return true
    }

    val sensitivData = mutableSetOf("birth_date", "real_name", "uid", "username", "serial", "user_name" )

    private fun replaceSensitiveData(body: String): String {
        try {
            // TODO
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

    private fun getErrorMessage(jsonObj: JSONObject): String {
        // TODO
        return "Error"
    }

    private fun checkResponse(body: String?): JSONObject? {
        if (body.isNullOrEmpty()) {
            return null
        }
        Log.i(LOG_ID, "Handle json response: " + replaceSensitiveData(body))
        val jsonObj = JSONObject(body)
        // TODO
        return jsonObj
    }


    private fun handleLoginResponse(body: String?): Boolean {
        // TODO
        if(checkResponse(body) != null) {
            val setToken = getHeaderField("Set-Token")
            if(!setToken.isNullOrEmpty()) {
                Log.d(LOG_ID, "Set token: $setToken")
                token = setToken
                GlucoDataService.sharedPref!!.edit {
                    putString(Constants.SHARED_PREF_YUWELL_TOKEN, token)
                }
                return true
            }
        }
        return false
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
            token = sharedPreferences.getString(Constants.SHARED_PREF_YUWELL_TOKEN, "")!!
            if(token.isNotEmpty()) {
                dataReceived = true
            }
            refreshToken = sharedPreferences.getString(Constants.SHARED_PREF_YUWELL_REFRESH_TOKEN, "")!!
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
