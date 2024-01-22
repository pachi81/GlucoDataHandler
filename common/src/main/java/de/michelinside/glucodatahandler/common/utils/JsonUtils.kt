package de.michelinside.glucodatahandler.common.utils

import android.util.Log
import org.json.JSONObject

object JsonUtils {
    private val LOG_ID = "GDH.Utils.JsonUtils"

    fun getFloat(key: String, jsonObject: JSONObject): Float {
        val string = jsonObject.optString(key)
        if (string.isNullOrEmpty())
            return Float.NaN
        try {
            return string.replace(',', '.').toFloat()
        } catch (ex: Exception) {
            Log.i(LOG_ID, "Error while converting '" + string + "' to float: " + ex.message)
        }
        return Float.NaN
    }
}