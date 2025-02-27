package de.michelinside.glucodatahandler.common.tasks

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.database.GlucoseValue
import de.michelinside.glucodatahandler.common.database.dbAccess
import de.michelinside.glucodatahandler.common.notifier.DataSource
import de.michelinside.glucodatahandler.common.utils.GlucoDataUtils
import de.michelinside.glucodatahandler.common.utils.JsonUtils
import de.michelinside.glucodatahandler.common.utils.Utils
import org.json.JSONArray
import org.json.JSONObject
import java.lang.NumberFormatException

class NightscoutSourceTask: DataSourceTask(Constants.SHARED_PREF_NIGHTSCOUT_ENABLED, DataSource.NIGHTSCOUT) {
    private val LOG_ID = "GDH.Task.Source.NightscoutTask"
    companion object {
        private var url = ""
        private var secret = ""
        private var token = ""
        private var iob_cob_support = true
        const val PEBBLE_ENDPOINT = "/pebble"
        const val ENTRIES_ENDPOINT = "/api/v1/entries/current.json"
        const val GRAPHDATA_ENDPOINT = "/api/v1/entries/sgv.json?find[date][\$gt]=%d&count=%d"
    }

    override fun hasIobCobSupport(): Boolean {
        if (ReceiveData.source == DataSource.NIGHTSCOUT) {
            Log.d(LOG_ID, "Ignore IOB/COB request, as the last data source was Nightscout")
            return false
        }
        if (active(1L) && iob_cob_support)
            return true
        return false
    }

    override fun needsInternet(): Boolean {
        if(url.contains("127.0.0.1") || url.lowercase().contains("localhost")) {
            Log.v(LOG_ID, "Localhost detected!")
            return false
        }
        return true
    }

    private fun getGraphData(firstValueTime: Long): Boolean {
        val count = Utils.getElapsedTimeMinute(firstValueTime)
        Log.i(LOG_ID, "Getting up to $count graph data for time > ${Utils.getUiTimeStamp(firstValueTime)} - ($firstValueTime)")
        if(count > 0) {
            val (result, errorText) = handleEntriesResponse(httpGet(getUrl(GRAPHDATA_ENDPOINT.format(firstValueTime,count)), getHeader()), firstValueTime)
            if(errorText.isNotEmpty())
                Log.e(LOG_ID, "Error while getting graph data: $errorText")
            return result
        }
        return false
    }

    override fun getValue() : Boolean {
        val firstNeededValue = getFirstNeedGraphValueTime()
        if (firstNeededValue > 0) {
            if(getGraphData(firstNeededValue) && !iob_cob_support) {
                // data received, also no iob/cob to request
                return true
            }
        }
        if (!handlePebbleResponse(httpGet(getUrl(PEBBLE_ENDPOINT), getHeader()))) {
            if (!hasIobCobSupport() || ReceiveData.getElapsedTimeMinute() > 0) {
                // only check for new value, if there is no (otherwise it was only called for IOB/COB)
                val body = httpGet(getUrl(ENTRIES_ENDPOINT), getHeader())
                if (body == null && lastErrorCode >= 300)
                    return false
                val (result, errorText) = handleEntriesResponse(body)
                if (!result) {
                    setLastError(errorText)
                    return false
                }
                return true
            }
        }
        return false
    }

    override fun getTrustAllCertificates(): Boolean = true

    override fun checkPreferenceChanged(sharedPreferences: SharedPreferences, key: String?, context: Context): Boolean {
        var result = false
        if (key == null) {
            url = sharedPreferences.getString(Constants.SHARED_PREF_NIGHTSCOUT_URL, "")!!.trim().trimEnd('/')
            secret = Utils.encryptSHA1(sharedPreferences.getString(Constants.SHARED_PREF_NIGHTSCOUT_SECRET, "")!!)
            token = sharedPreferences.getString(Constants.SHARED_PREF_NIGHTSCOUT_TOKEN, "")!!
            iob_cob_support = sharedPreferences.getBoolean(Constants.SHARED_PREF_NIGHTSCOUT_IOB_COB, true)
            result = true
        } else {
            when(key) {
                Constants.SHARED_PREF_NIGHTSCOUT_URL -> {
                    url = sharedPreferences.getString(Constants.SHARED_PREF_NIGHTSCOUT_URL, "")!!.trim().trimEnd('/')
                    result = true
                }
                Constants.SHARED_PREF_NIGHTSCOUT_SECRET -> {
                    secret = Utils.encryptSHA1(sharedPreferences.getString(Constants.SHARED_PREF_NIGHTSCOUT_SECRET, "")!!)
                    result = true
                }
                Constants.SHARED_PREF_NIGHTSCOUT_TOKEN -> {
                    token = sharedPreferences.getString(Constants.SHARED_PREF_NIGHTSCOUT_TOKEN, "")!!
                    result = true
                }
                Constants.SHARED_PREF_NIGHTSCOUT_IOB_COB -> {
                    iob_cob_support = sharedPreferences.getBoolean(Constants.SHARED_PREF_NIGHTSCOUT_IOB_COB, true)
                    result = true
                }
            }
        }
        return super.checkPreferenceChanged(sharedPreferences, key, context) || result
    }

    private fun getUrl(endpoint: String): String {
        var resultUrl = url + endpoint
        if (token.isNotEmpty()) {
            if(resultUrl.contains("?"))
                resultUrl += "&token=" + token
            else
                resultUrl += "?token=" + token
        }
        return resultUrl
    }
    
    private fun getHeader(): MutableMap<String, String> {
        val result = mutableMapOf<String, String>()
        if (secret.isNotEmpty()) {
            result["api-secret"] = secret
        }
        return result
    }

    private fun handleEntriesResponse(body: String?, firstValueTime: Long = 0) : Pair<Boolean, String> {
        if (!body.isNullOrEmpty()) {
            Log.d(LOG_ID, "Handle entries response: " + body.take(1000))
            val jsonEntries = JSONArray(body)
            if (jsonEntries.length() <= 0) {
                return Pair(false, "No entries in body: " + body)
            }

            val jsonObject = jsonEntries.getJSONObject(0)
            val type: String? = if (jsonObject.has("type") ) jsonObject.getString("type") else null
            if (type == null || type != "sgv") {
                return Pair(false, "Unsupported type '" + type + "' found in response: " + body.take(100))
            }

            if(!jsonObject.has("date") || !jsonObject.has("sgv") || !jsonObject.has("direction"))
                return Pair(false, "Missing values in response: " + body.take(100))

            val valueTime = jsonObject.getLong("date")
            if(valueTime < firstValueTime)
                return Pair(true, "")   // no new value
            val glucoExtras = Bundle()
            setSgv(glucoExtras, jsonObject)
            setRate(glucoExtras, jsonObject)
            glucoExtras.putLong(ReceiveData.TIME, valueTime)
            if(jsonObject.has("device"))
                glucoExtras.putString(ReceiveData.SERIAL, jsonObject.getString("device"))

            try {
                if(jsonEntries.length() > 1) {
                    val values = mutableListOf<GlucoseValue>()
                    for (i in 0 until jsonEntries.length()) {
                        val jsonEntry = jsonEntries.getJSONObject(i)
                        var glucose = JsonUtils.getFloat("sgv", jsonEntry)
                        if (GlucoDataUtils.isMmolValue(glucose))
                            glucose = GlucoDataUtils.mmolToMg(glucose)
                        val time = jsonEntry.getLong("date")
                        if(!glucose.isNaN() && time > 0 && time >= firstValueTime) {
                            values.add(GlucoseValue(time, glucose.toInt()))
                        }
                    }
                    Log.i(LOG_ID, "Add ${values.size} values to database")
                    dbAccess.addGlucoseValues(values)
                }
            } catch (exc: Exception) {
                Log.e(LOG_ID, "Exception while parsing entries response: " + exc.message)
            }

            handleResult(glucoExtras)
            return Pair(true, "")
        }
        return Pair(false, "No data in response!")
    }

    private fun handlePebbleResponse(body: String?) : Boolean {
        try {
            if (!body.isNullOrEmpty()) {
                Log.d(LOG_ID, "Handle pebble response: " + body)
                val jsonBody = JSONObject(body)
                val jsonEntries = jsonBody.optJSONArray("bgs")
                if (jsonEntries == null || jsonEntries.length() <= 0) {
                    Log.w(LOG_ID, "No entries in body: " + body)
                    return false
                }

                val jsonObject = jsonEntries.getJSONObject(0)

                if (!jsonObject.has("datetime") || !jsonObject.has("sgv") || (!jsonObject.has("trend") && !jsonObject.has(
                        "direction"
                    ))
                ) {
                    Log.w(LOG_ID, "Missing values in response: " + body)
                    return false
                }

                val glucoExtras = Bundle()
                glucoExtras.putLong(ReceiveData.TIME, jsonObject.getLong("datetime"))
                setSgv(glucoExtras, jsonObject)
                setRate(glucoExtras, jsonObject)
                if (jsonObject.has("device"))
                    glucoExtras.putString(ReceiveData.SERIAL, jsonObject.getString("device"))
                if (iob_cob_support) {
                    if (jsonObject.has("iob"))
                        glucoExtras.putFloat(ReceiveData.IOB, JsonUtils.getFloat("iob", jsonObject))
                    if (jsonObject.has("cob"))
                        glucoExtras.putFloat(ReceiveData.COB, Utils.getCobValue(JsonUtils.getFloat("cob", jsonObject)))
                } else {
                    glucoExtras.putFloat(ReceiveData.IOB, Float.NaN)
                    glucoExtras.putFloat(ReceiveData.COB, Float.NaN)
                    glucoExtras.putLong(ReceiveData.IOBCOB_TIME, 0L)
                }

                handleResult(glucoExtras)
                return true
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Exception while parsing pebble response " + body + " - " + exc.message)
        }
        return false
    }

    private fun setSgv( bundle: Bundle, jsonObject: JSONObject) {
        val glucose = JsonUtils.getFloat("sgv", jsonObject)
        if (glucose.isNaN())
            throw NumberFormatException("Invalid sgv format '" + jsonObject.optString("sgv") + "'")
        if (GlucoDataUtils.isMmolValue(glucose)) {
            bundle.putInt(ReceiveData.MGDL, GlucoDataUtils.mmolToMg(glucose).toInt())
            bundle.putFloat(ReceiveData.GLUCOSECUSTOM, glucose)
        } else {
            bundle.putInt(ReceiveData.MGDL, glucose.toInt())
        }
    }

    private fun setRate( bundle: Bundle, jsonObject: JSONObject) {
        if (jsonObject.has("trend"))
            bundle.putFloat(ReceiveData.RATE, getRateFromTrend(jsonObject.getInt("trend")))
        else
            bundle.putFloat(ReceiveData.RATE, GlucoDataUtils.getRateFromLabel(jsonObject.getString("direction")))
    }

    private fun getRateFromTrend(trend: Int): Float {
        return when(trend) {
            1 -> 4F
            2 -> 2F
            3 -> 1F
            4 -> 0F
            5 -> -1F
            6 -> -2F
            7 -> -4f
            else -> Float.NaN
        }
    }
}
