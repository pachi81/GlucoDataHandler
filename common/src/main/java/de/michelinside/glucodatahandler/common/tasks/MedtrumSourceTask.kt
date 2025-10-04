package de.michelinside.glucodatahandler.common.tasks

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
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

class MedtrumSourceTask() : MultiPatientSourceTask(Constants.SHARED_PREF_MEDTRUM_ENABLED, DataSource.MEDTRUM) {
    override val LOG_ID = "GDH.Task.Source.Medtrum"
    override var minInterval = 2L
    override val patientIdKey = Constants.SHARED_PREF_MEDTRUM_PATIENT_ID
    companion object {
        private var user = ""
        private var password = ""
        private var reconnect = false
        private var cookie = ""
        private var topLevelDomain = "eu"
        private var dataReceived = false   // mark this endpoint as already received data
        val patientData = mutableMapOf<String, String>()
        const val server = "https://easyview.medtrum.%s"
        const val LOGIN_ENDPOINT = "/mobile/ajax/login"
        const val LOGINDATA_ENDPOINT = "/mobile/ajax/logindata"
        const val MONITORLIST_ENDPOINT = "/mobile/ajax/monitor?flag=monitor_list"
        const val DOWNLOAD_ENDPOINT = "/mobile/ajax/download"
        const val GRAPHDATA_SUFFIX = "?flag=sg&st=%s&et=%s&user_name=%s"
    }

    private fun getUrl(endpoint: String): String {
        val url = server.format(topLevelDomain) + endpoint
        Log.i(LOG_ID, "Using URL: " + url)
        return url
    }

    private fun getHeader(): MutableMap<String, String> {
        val result = mutableMapOf(
            "DevInfo" to "Android ${Build.VERSION.RELEASE};${Build.MANUFACTURER} ${Build.DEVICE};Android ${Build.VERSION.RELEASE}",
            "AppTag" to "v=1.2.70(112);n=eyfo;p=android",
            "User-Agent" to "okhttp/3.5.0"
        )
        if (cookie.isNotEmpty()) {
            result["Cookie"] = cookie
        }
        Log.v(LOG_ID, "Header: ${result}")
        return result
    }

    override fun reset() {
        Log.i(LOG_ID, "reset called")
        super.reset()
        cookie = ""
        dataReceived = false
        patientData.clear()
        try {
            Log.d(LOG_ID, "Save reset")
            GlucoDataService.sharedPref!!.edit {
                putString(Constants.SHARED_PREF_MEDTRUM_COOKIE, cookie)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "save reset exception: " + exc.toString() )
        }
    }


    override fun authenticate(): Boolean {
        if (cookie.isNotEmpty() && reconnect) {
            reset()
            if (reconnect) {
                reconnect = false
                GlucoDataService.sharedPref!!.edit {
                    putBoolean(Constants.SHARED_PREF_MEDTRUM_RECONNECT, false)
                }
            }
        }
        if (cookie.isEmpty()) {
            val data = "apptype=Follow&user_type=M&platform=google&user_name=$user&password=$password"
            /*json.put("user_name", user)
            json.put("password", password)
            json.put("platform", "google")
            //json.put("push_platform", "google")
            json.put("user_type", "M")
            json.put("app_version", "1.2.70(112)")
            json.put("bundleID", "com.medtrum.easyfollowforandroidmg")
            json.put("device_name", Build.MODEL)*/
            //json.put("deviceToken", Firebase token...)
            if(!handleLoginResponse(httpPost(getUrl(LOGIN_ENDPOINT), getHeader(), data)))
                return false
        }
        return true
    }

    override fun getPatientData(): MutableMap<String, String> {
        return handleLoginDataResult(httpGet(getUrl(LOGINDATA_ENDPOINT), getHeader()))
    }

    override fun getPatientValue(patientId: String): Boolean {
        Log.d(LOG_ID, "Get value for patient ${getPatient(patientId)}")

        // if patient user is set and not graph data is needed, only get login data with current value
        // else get login data for patient data and addtional get graph data
        val firstNeededValue = getFirstNeedGraphValueTime()
        val minutes = if(firstNeededValue > 0) Utils.getElapsedTimeMinute(firstNeededValue) else 0
        Log.d(LOG_ID, "Get data for last $minutes minutes")
        handleMonitorListResponse(patientId, httpGet(getUrl(MONITORLIST_ENDPOINT), getHeader()))
        if(minutes <= 2L)
            return true
        // else get graph data for current patient
        handleGraphDataResponse(httpGet(getGraphUrl(patientId, firstNeededValue), getHeader()), firstNeededValue)
        return true
    }

    val sensitivData = mutableSetOf("birth_date", "real_name", "uid", "username", "serial", "user_name" )

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

    private fun getErrorMessage(jsonObj: JSONObject): String {
        if(jsonObj.has("msg")) {
            return jsonObj.optString("msg", "Error")?: "Error"
        }
        return "Error"
    }

    private fun checkResponse(body: String?): JSONObject? {
        if (body.isNullOrEmpty()) {
            return null
        }
        Log.i(LOG_ID, "Handle json response: " + replaceSensitiveData(body))
        val jsonObj = JSONObject(body)
        if (jsonObj.has("res")) {
            val status = jsonObj.optString("res", "Err")
            if(status != "OK") {
                setLastError(getErrorMessage(jsonObj))
                if(cookie.isNotEmpty()) {
                    reset()
                    retry = true
                }
                return null
            }
        }
        return jsonObj
    }


    private fun handleLoginResponse(body: String?): Boolean {
        /* login response example:
            {
                "auto_mode_enable": false,
                "auto_mode_gestational": false,
                "created_by": "register",
                "device_option": "cgms,pump",
                "patch_enable": false,
                "res": "OK",
                "smart_run_enable": false,
                "smart_run_gestational": false,
                "uid": xxxxx,
                "usertype": "M"
            }
            Cookie is part of header "Set-Cookie"
         */
        if(checkResponse(body) != null) {
            val setCookie = getHeaderField("Set-Cookie")
            if(!setCookie.isNullOrEmpty()) {
                Log.d(LOG_ID, "Set cookie: $setCookie")
                cookie = setCookie
                GlucoDataService.sharedPref!!.edit {
                    putString(Constants.SHARED_PREF_MEDTRUM_COOKIE, cookie)
                }
                return true
            }
        }
        return false
    }

    private fun handleLoginDataResult(body: String?): MutableMap<String, String> {
        val jsonObject = checkResponse(body) ?: return mutableMapOf()
        val dataArray = jsonObject.optJSONArray("monitorlist")
        if(dataArray == null || dataArray.length() == 0) {
            setLastError(GlucoDataService.context!!.getString(R.string.source_no_patient))
            return mutableMapOf()
        }
        Log.d(LOG_ID, "Handle login data result with ${dataArray.length()} patients")
        // re-create patientData map
        patientData.clear()
        for (i in 0 until dataArray.length()) {
            val data = dataArray.getJSONObject(i)
            if(data.has("username") && data.has("real_name")) {
                val id = data.getString("username")
                val name = data.getString("real_name")
                Log.v(LOG_ID, "New patient found: $name")
                patientData[id] = name
            }
        }
        return patientData
    }

    private fun handleMonitorListResponse(patientId: String, body: String?): Boolean {
        /*
            {
                "monitorlist": [{
                        "pump_status": {
                            "iob": 0.45,
                            "updateTime": 1752563491
                        },
                        "real_name": "User Name",
                        "sensor_status": {
                            "glucose": 5.9,
                            "glucoseRate": 4,
                            "nextSequenceNeedCalibrate": 22061,  <- if smaller than sequence -> calibration neeeded!
                            "platform": "google",
                            "rssi": 0,
                            "sensorId": 23,
                            "sensorLifetimeTotalCount": 10079,
                            "sequence": 727,    <- 2x = sensor run time
                            "serial": ---,
                            "updateTime": 1752563388
                        },
                        "uid": 12345,
                        "username": "user@mail.com"  <- patientId!


            glucose: 0.0 wenn kein Wert verfügbar ist (Kalibrierung notwendig oder Aufwärmphase für 30 Min -> sequence 15)

            glucoseRate:
            1-3 steigend
            4-6 fallend
            0 und 8 gleichbleibend
            7 unbekannt


            serial: toHex -> Serial des Behälters
            sensorId: ID des sensors -> serial-sensorId

            sequence: Laufzeit des Sensors in 2 Minuten Interval

            nextSequenceNeedCalibrate: Wann wieder kalibriert werden muss
        */

        val jsonObject = checkResponse(body) ?: return false
        val dataArray = jsonObject.optJSONArray("monitorlist")
        if(dataArray == null || dataArray.length() == 0) {
            setLastError(GlucoDataService.context!!.getString(R.string.source_no_patient))
            return false
        }
        Log.d(LOG_ID, "Handle monitorlist result with ${dataArray.length()} patients - cur patient: $patientId")
        for (i in 0 until dataArray.length()) {
            val data = dataArray.getJSONObject(i)
            if(data.has("sensor_status") && data.has("username") && data.getString("username") == patientId) {
                val sensorData = data.getJSONObject("sensor_status")
                if(sensorData.has("glucose") && sensorData.has("updateTime")) {
                    val glucose = sensorData.getDouble("glucose").toFloat()
                    if(glucose == 0.0F) {
                        if(sensorData.has("sequence")) {
                            val sequence = sensorData.getInt("sequence")
                            if(sequence <= 15) {
                                setLastError(GlucoDataService.context!!.getString(R.string.source_new_sensor_starting))
                                return false
                            }
                            if(sensorData.has("nextSequenceNeedCalibrate")) {
                                val nextCalibration = sensorData.getInt("nextSequenceNeedCalibrate")
                                if(sequence >= nextCalibration) {
                                    setLastError(GlucoDataService.context!!.getString(R.string.source_sensor_need_calibration))
                                    return false
                                }
                            }
                        }
                        setLastError(GlucoDataService.context!!.getString(R.string.source_no_valid_value))
                        return false
                    }
                    val glucoExtras = Bundle()
                    glucoExtras.putLong(ReceiveData.TIME, sensorData.getLong("updateTime") * 1000)
                    glucoExtras.putFloat(ReceiveData.GLUCOSECUSTOM, glucose)
                    if(GlucoDataUtils.isMmolValue(glucose))
                        glucoExtras.putInt(ReceiveData.MGDL, GlucoDataUtils.mmolToMg(glucose).toInt())
                    else
                        glucoExtras.putInt(ReceiveData.MGDL, glucose.toInt())
                    if(sensorData.has("glucoseRate"))
                        glucoExtras.putFloat(ReceiveData.RATE, getRateFromTrend(sensorData.getInt("glucoseRate")))
                    if(sensorData.has("serial") && sensorData.has("sensorId"))
                        glucoExtras.putString(ReceiveData.SERIAL, Integer.toHexString(sensorData.getInt("serial")).padStart(8, '0').uppercase() + "-" + sensorData.getInt("sensorId").toString())
                    if(sensorData.has("sequence")) {
                        val runTimeMin = sensorData.getInt("sequence") * 2
                        glucoExtras.putLong(ReceiveData.SENSOR_START_TIME, ((System.currentTimeMillis()/60000) - runTimeMin) * 60000)
                    }
                    if(data.has("pump_status")) {
                        val pumpStatus = data.getJSONObject("pump_status")
                        if(pumpStatus.has("iob")) {
                            glucoExtras.putFloat(ReceiveData.IOB, pumpStatus.getDouble("iob").toFloat())
                            if(pumpStatus.has("updateTime"))
                                glucoExtras.putLong(ReceiveData.IOBCOB_TIME, pumpStatus.getLong("updateTime") * 1000)
                        }
                    }
                    handleResult(glucoExtras)
                    return true
                } else {
                    setLastError(GlucoDataService.context!!.getString(R.string.source_invalid_patient))
                    return false
                }
            }
        }
        setLastError(GlucoDataService.context!!.getString(R.string.source_no_patient))
        return false
    }

    private fun handleGraphDataResponse(body: String?, firstTime: Long) {
        /* response format:
            {
              "data": [
                [
                  "[uid]-[serial]-8-5051",
                  1752480092.0,
                  11.7,
                  4.8,
                  "C",
                  0
                ],
                [
                  "[uid]-[serial]-8-5052",
                  1752480212.0,
                  11.89,
                  4.9,
                  "C",
                  0
                ],
                [
                  "[uid]-[serial]-8-5053",
                  1752480332.0,
                  11.39,
                  4.7,
                  "C",
                  0
                ]
              ],
              "flag": "sg",
              "res": "OK",
              "user_name": "---"
            }
         */
        val jsonObject = checkResponse(body) ?: return
        val graphData = jsonObject.optJSONArray("data")
        if(graphData == null || graphData.length() == 0) {
            return
        }

        Log.d(LOG_ID, "Parse graph data for ${graphData.length()} entries with time >= ${Utils.getUiTimeStamp(firstTime)}")
        if(firstTime > 0L) {
            val values = mutableListOf<GlucoseValue>()
            for (i in 0 until graphData.length()) {
                val dataArray = graphData.optJSONArray(i)
                if(dataArray != null) {
                    val time = (dataArray.get(1) as Double).toLong() * 1000
                    if(time >= firstTime) {
                        val value = (dataArray.get(3) as Double).toFloat()
                        if(GlucoDataUtils.isGlucoseValid(value)) {
                            if(GlucoDataUtils.isMmolValue(value))
                                values.add(GlucoseValue(time, GlucoDataUtils.mmolToMg(value).toInt()))
                            else
                                values.add(GlucoseValue(time, value.toInt()))
                        }
                    }
                }
            }
            Log.d(LOG_ID, "Add ${values.size} values to db")
            dbAccess.addGlucoseValues(values)
        }
    }

    private fun getRateFromTrend(trend: Int): Float {
        if(trend in 1..3) {
            return trend.toFloat()
        }
        if(trend in 4..6) {
            return (3-trend).toFloat()
        }
        if(trend == 0 || trend == 8) {
            return 0F
        }
        return Float.NaN
    }

    private val format = SimpleDateFormat("yyyy-MM-dd%20HH:mm:ss", Locale.ENGLISH)
    private fun getGraphUrl(patientId: String, startTime: Long): String {
        val start = format.format(Date(startTime))
        val end = format.format(Date(System.currentTimeMillis()))
        val url = getUrl(DOWNLOAD_ENDPOINT) + GRAPHDATA_SUFFIX.format(start, end, patientId)
        Log.d(LOG_ID, "Get graph data with URL $url")
        return url
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
            user = sharedPreferences.getString(Constants.SHARED_PREF_MEDTRUM_USER, "")!!.trim()
            password = sharedPreferences.getString(Constants.SHARED_PREF_MEDTRUM_PASSWORD, "")!!.trim()
            cookie = sharedPreferences.getString(Constants.SHARED_PREF_MEDTRUM_COOKIE, "")!!
            if(cookie.isNotEmpty()) {
                dataReceived = true
            }
            topLevelDomain = sharedPreferences.getString(Constants.SHARED_PREF_MEDTRUM_SERVER, "eu")?: "eu"
            InternalNotifier.notify(GlucoDataService.context!!, NotifySource.SOURCE_STATE_CHANGE, null)
            trigger = true
        } else {
            when(key) {
                Constants.SHARED_PREF_MEDTRUM_USER -> {
                    if (user != sharedPreferences.getString(Constants.SHARED_PREF_MEDTRUM_USER, "")) {
                        user = sharedPreferences.getString(Constants.SHARED_PREF_MEDTRUM_USER, "")!!.trim()
                        reset()
                        InternalNotifier.notify(GlucoDataService.context!!, NotifySource.SOURCE_STATE_CHANGE, null)
                        trigger = true
                    }
                }
                Constants.SHARED_PREF_MEDTRUM_PASSWORD -> {
                    if (password != sharedPreferences.getString(Constants.SHARED_PREF_MEDTRUM_PASSWORD, "")) {
                        password = sharedPreferences.getString(Constants.SHARED_PREF_MEDTRUM_PASSWORD, "")!!.trim()
                        reset()
                        InternalNotifier.notify(GlucoDataService.context!!, NotifySource.SOURCE_STATE_CHANGE, null)
                        trigger = true
                    }
                }
                Constants.SHARED_PREF_MEDTRUM_RECONNECT -> {
                    if (reconnect != sharedPreferences.getBoolean(Constants.SHARED_PREF_MEDTRUM_RECONNECT, false)) {
                        reconnect = sharedPreferences.getBoolean(Constants.SHARED_PREF_MEDTRUM_RECONNECT, false)
                        Log.d(LOG_ID, "Reconnect triggered")
                        trigger = true
                    }
                }
                Constants.SHARED_PREF_MEDTRUM_SERVER -> {
                    if (topLevelDomain != sharedPreferences.getString(Constants.SHARED_PREF_MEDTRUM_SERVER, "io")) {
                        topLevelDomain = sharedPreferences.getString(Constants.SHARED_PREF_MEDTRUM_SERVER, "io")?: "io"
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
