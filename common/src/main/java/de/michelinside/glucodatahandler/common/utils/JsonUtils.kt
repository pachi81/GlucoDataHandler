package de.michelinside.glucodatahandler.common.utils

import android.os.Parcelable
import android.util.Log
import com.google.gson.Gson
import org.json.JSONObject

object JsonUtils {
    val LOG_ID = "GDH.Utils.JsonUtils"

    fun getFloat(key: String, jsonObject: JSONObject): Float {
        return Utils.parseFloatString(jsonObject.optString(key))
    }

    fun getParcelableAsJson(parcelable: Parcelable): String {
        return Gson().toJson(parcelable)
    }

    inline fun <reified T : Parcelable?> getParcelableFromJson(json: String?): T? {
        try {
            if(json != null)
                return Gson().fromJson(json, T::class.java)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Error parsing json $json: $exc")
        }
        return null
    }

}