package de.michelinside.glucodatahandler.common.tasks

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.notifier.DataSource
import de.michelinside.glucodatahandler.common.utils.GlucoDataUtils
import org.json.JSONArray
import org.json.JSONObject

class NightscoutSourceTask: DataSourceTask(Constants.SHARED_PREF_NIGHTSCOUT_ENABLED, DataSource.NIGHTSCOUT) {
    private val LOG_ID = "GDH.Task.NightscoutSourceTask"
    companion object {
        private var url = ""
        private var secret = ""
        private var token = ""
        const val PEBBLE_ENDPOINT = "/pebble"
        const val ENTRIES_ENDPOINT = "/api/v1/entries/current.json"
    }
    override fun executeRequest(context: Context) {
        if (!handlePebbleResponse(httpGet(getUrl(PEBBLE_ENDPOINT), getHeader()))) {
            val (result, errorText) = handleEntriesResponse(httpGet(getUrl(ENTRIES_ENDPOINT), getHeader()))
            if (!result) {
                setLastError(source, errorText)
            }
        }
    }

    override fun getTrustAllCertificates(): Boolean = true

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

    private fun getUrl(endpoint: String): String {
        var resultUrl = url + endpoint
        if (token.isNotEmpty())
            resultUrl += "?token=" + token
        return resultUrl
    }
    
    private fun getHeader(): MutableMap<String, String> {
        val result = mutableMapOf<String, String>()
        if (secret.isNotEmpty()) {
            result["api-secret"] = secret
        }
        return result
    }

    private fun handleEntriesResponse(body: String?) : Pair<Boolean, String> {
        if (!body.isNullOrEmpty()) {
            Log.d(LOG_ID, "Handle entries response: " + body)
            val jsonEntries = JSONArray(body)
            if (jsonEntries.length() <= 0) {
                return Pair(false, "No entries in body: " + body)
            }

            val jsonObject = jsonEntries.getJSONObject(0)
            val type: String? = if (jsonObject.has("type") ) jsonObject.getString("type") else null
            if (type == null || type != "sgv") {
                return Pair(false, "Unsupported type '" + type + "' found in response: " + body)
            }

            if(!jsonObject.has("date") || !jsonObject.has("sgv") || !jsonObject.has("direction"))
                return Pair(false, "Missing values in response: " + body)

            val glucoExtras = Bundle()
            setSgv(glucoExtras, jsonObject)
            setRate(glucoExtras, jsonObject)
            glucoExtras.putLong(ReceiveData.TIME, jsonObject.getLong("date"))
            if(jsonObject.has("device"))
                glucoExtras.putString(ReceiveData.SERIAL, jsonObject.getString("device"))

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
                if (jsonObject.has("iob"))
                    glucoExtras.putFloat(ReceiveData.IOB, jsonObject.getDouble("iob").toFloat())
                if (jsonObject.has("cob"))
                    glucoExtras.putFloat(ReceiveData.COB, jsonObject.getDouble("cob").toFloat())

                handleResult(glucoExtras)
                return true
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Exception while parsing pebble response " + body + " - " + exc.message)
        }
        return false
    }

    private fun setSgv( bundle: Bundle, jsonObject: JSONObject) {
        val sgv = jsonObject.getString("sgv").replace(',', '.')
        val glucose = sgv.toFloat()
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
            1 -> -4F
            2 -> -2F
            3 -> -1F
            4 -> 0F
            5 -> 1F
            6 -> 2F
            7 -> 4f
            else -> Float.NaN
        }
    }
}
