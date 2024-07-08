package de.michelinside.glucodatahandler.common.utils

import org.json.JSONObject

object JsonUtils {
    private val LOG_ID = "GDH.Utils.JsonUtils"

    fun getFloat(key: String, jsonObject: JSONObject): Float {
        return Utils.parseFloatString(jsonObject.optString(key))
    }
}