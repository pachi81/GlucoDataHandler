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
        val (result, errorText) = handleEntriesResponse(httpGet(getUrl(ENTRIES_ENDPOINT), getHeader()))
        if (!result) {
            setLastError(source, errorText)
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
            glucoExtras.putLong(ReceiveData.TIME, jsonObject.getLong("date"))
            glucoExtras.putInt(ReceiveData.MGDL, jsonObject.getInt("sgv"))
            glucoExtras.putFloat(ReceiveData.RATE, GlucoDataUtils.getRateFromLabel(jsonObject.getString("direction")))
            if(jsonObject.has("device"))
                glucoExtras.putString(ReceiveData.SERIAL, jsonObject.getString("device"))

            handleResult(glucoExtras)
            return Pair(true, "")
        }
        return Pair(false, "No data in response!")
    }
}
