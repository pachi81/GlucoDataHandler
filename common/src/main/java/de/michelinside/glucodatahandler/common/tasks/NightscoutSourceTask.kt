package de.michelinside.glucodatahandler.common.tasks

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.Utils
import de.michelinside.glucodatahandler.common.notifier.DataSource

class NightscoutSourceTask: DataSourceTask(Constants.SHARED_PREF_NIGHTSCOUT_ENABLED, DataSource.NIGHTSCOUT) {
    private val LOG_ID = "GlucoDataHandler.Task.NightscoutSourceTask"
    companion object {
        private var url = ""
        private var secret = ""
        private var token = ""
        const val ENDPOINT = "/api/v1/entries/current"
    }
    override fun executeRequest(context: Context) {
        handleResponse(httpGet(getUrl(), getHeader()))
    }

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

    private fun getUrl(): String {
        var resultUrl = url + ENDPOINT
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

    private fun handleResponse(body: String?) {
        if (!body.isNullOrEmpty()) {
            Log.d(LOG_ID, "Handle response: " + body)
            val values = body.split("\t")
            if (values.size < 4) {
                setLastError(source, "Error in response: " + body)
                return
            }
            val timeStamp = values[1].toLong()
            val glucose = values[2].toInt()
            val trend = values[3].trim('"')
            val sensor = values[4].trim('"')

            val glucoExtras = Bundle()
            glucoExtras.putLong(ReceiveData.TIME, timeStamp)
            if (ReceiveData.isMmol) {
                glucoExtras.putFloat(ReceiveData.GLUCOSECUSTOM, Utils.mgToMmol(glucose.toFloat()))
            } else {
                glucoExtras.putFloat(ReceiveData.GLUCOSECUSTOM, glucose.toFloat())
            }
            glucoExtras.putInt(ReceiveData.MGDL, glucose)
            glucoExtras.putString(ReceiveData.SERIAL, sensor)
            glucoExtras.putFloat(ReceiveData.RATE, ReceiveData.getRateFromLabel(trend))
            glucoExtras.putInt(ReceiveData.ALARM, 0)

            handleResult(glucoExtras)
        }
    }
}
