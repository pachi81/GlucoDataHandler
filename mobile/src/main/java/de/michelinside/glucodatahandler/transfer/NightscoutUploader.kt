package de.michelinside.glucodatahandler.transfer

import android.content.Context
import android.content.SharedPreferences
import de.michelinside.glucodatahandler.common.utils.Log


class NightscoutUploader: SharedPreferences.OnSharedPreferenceChangeListener {
    private val LOG_ID = "GDH.NightscoutUploader"
    private var url = ""
    private var secret = ""
    private var token = ""

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

    fun init(context: Context) {

    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        try {
            if (sharedPreferences != null) {
                Log.d(LOG_ID, "onSharedPreferenceChanged called for " + key)
            }
        } catch (ex: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged: " + ex)
        }
    }

}