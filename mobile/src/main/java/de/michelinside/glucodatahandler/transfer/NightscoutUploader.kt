package de.michelinside.glucodatahandler.transfer

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import de.michelinside.glucodatahandler.common.BuildConfig
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.database.GlucoseValue
import de.michelinside.glucodatahandler.common.database.dbAccess
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.Log
import de.michelinside.glucodatahandler.common.utils.Utils
import de.michelinside.glucodatahandler.R
import androidx.core.content.edit
import de.michelinside.glucodatahandler.common.SourceState
import de.michelinside.glucodatahandler.common.utils.HttpRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject


object NightscoutUploader: SharedPreferences.OnSharedPreferenceChangeListener, NotifierInterface {
    private val LOG_ID = "GDH.NightscoutUploader"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var url = ""
    private var secret = ""
    private var token = ""
    private var lastUploadTime: Long = 0
    private val ENTRIES_ENDPOINT = "/api/v1/entries"

    var lastError = ""
        private set

    var state = SourceState.NONE
        private set(value) {
            if (field != value) {
                Log.i(LOG_ID, "State changed from $field to $value")
                field = value
                InternalNotifier.notify(GlucoDataService.context!!, NotifySource.UPDATE_MAIN, null)
            }
        }

    private fun getUrl(): String {
        var resultUrl = url + ENTRIES_ENDPOINT
        if (token.isNotEmpty()) {
            if(resultUrl.contains("?"))
                resultUrl += "&token=" + token
            else
                resultUrl += "?token=" + token
        }
        return resultUrl
    }

    private fun getHeader(): MutableMap<String, String> {
        val result = mutableMapOf(
            "Content-Type" to "application/json",
            "Accept" to "application/json"
        )
        if (secret.isNotEmpty()) {
            result["api-secret"] = secret
        }
        return result
    }

    fun enable(context: Context) {
        state = SourceState.NONE
        InternalNotifier.addNotifier( context, this,
            mutableSetOf(
                NotifySource.DB_DATA_CHANGED
            )
        )
        val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
        sharedPref.registerOnSharedPreferenceChangeListener(this)
        onSharedPreferenceChanged(sharedPref, null)
        val sharedExtraPref = context.getSharedPreferences(Constants.SHARED_PREF_EXTRAS_TAG, Context.MODE_PRIVATE)
        lastUploadTime = sharedExtraPref.getLong(Constants.SHARED_PREF_NIGHTSCOUT_UPLOAD_TIME, 0L)
        Log.i(LOG_ID, "enabled")
    }

    fun disable(context: Context) {
        InternalNotifier.remNotifier(
            GlucoDataService.context!!, this
        )
        val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
        sharedPref.unregisterOnSharedPreferenceChangeListener(this)
        state = SourceState.NONE
        Log.i(LOG_ID, "disabled")
    }

    fun uploadValues(context: Context): Boolean {
        try {
            if (HttpRequest.isLocalHost(url) && !HttpRequest.isConnected(context)) {
                Log.w(LOG_ID, "No internet connection")
                state = SourceState.NO_CONNECTION
                return false
            }
            val minTime = maxOf(lastUploadTime + 1, System.currentTimeMillis() - Constants.DB_MAX_DATA_WEAR_TIME_MS)
            Log.d(LOG_ID, "Upload values since ${Utils.getUiTimeStamp(minTime)}")
            return uploadGlucoseData(context, dbAccess.getGlucoseValues(minTime))
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Error uploading last values: ${exc.message}")
        }
        return false
    }

    private fun uploadGlucoseData(context: Context, glucoseValues: List<GlucoseValue>): Boolean {
        Log.d(LOG_ID, "Uploading ${glucoseValues.size} values to $url")
        try {
            if(glucoseValues.isNotEmpty()) {
                scope.launch {
                    try {
                        // create JSON from values
                        val jsonEntries = JSONArray()
                        val device = context.resources.getString(R.string.app_name)
                        glucoseValues.forEach {
                            val jsonEntry = JSONObject()
                            jsonEntry.put("sgv", it.value)
                            jsonEntry.put("date", it.timestamp)
                            jsonEntry.put("device", device)
                            jsonEntry.put("type", "sgv")
                            jsonEntries.put(jsonEntry)
                        }
                        val httpRequest = HttpRequest()
                        val response = httpRequest.post(getUrl(), jsonEntries.toString(), getHeader(), true)
                        if(response == 200) {
                            Log.i(LOG_ID, "Successfully uploaded ${glucoseValues.size} values from ${Utils.getUiTimeStamp(glucoseValues.first().timestamp)} to ${Utils.getUiTimeStamp(glucoseValues.last().timestamp)}")
                            updateLastValueTime(context,glucoseValues.last().timestamp)
                            state = SourceState.CONNECTED
                        } else {
                            Log.e(LOG_ID, "Error uploading glucose data: $response - msg: ${httpRequest.responseMessage} - error: ${httpRequest.responseError}")
                            lastError = "$response: ${httpRequest.responseMessage} - ${httpRequest.responseError}"
                            state = SourceState.ERROR
                        }
                    } catch (e: Exception) {
                        Log.e(LOG_ID, "Error uploading glucose data: ${e.message}")
                        lastError = e.message?: e.toString()
                        state = SourceState.ERROR
                    }
                }
                return true
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Error uploading values: ${exc.message}")
        }
        return false
    }


    private fun updateLastValueTime(context: Context, time: Long) {
        if(time > lastUploadTime) {
            Log.i(LOG_ID, "Updating last value time to ${Utils.getUiTimeStamp(time)}")
            val sharedExtraPref = context.getSharedPreferences(Constants.SHARED_PREF_EXTRAS_TAG, Context.MODE_PRIVATE)
            lastUploadTime = time
            sharedExtraPref.edit {
                putLong(Constants.SHARED_PREF_NIGHTSCOUT_UPLOAD_TIME, lastUploadTime)
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        try {
            if (sharedPreferences != null) {
                Log.d(LOG_ID, "onSharedPreferenceChanged called for " + key)
                if (key == null) {
                    url = sharedPreferences.getString(Constants.SHARED_PREF_NIGHTSCOUT_UPLOAD_URL, "")!!.trim().trimEnd('/')
                    if(BuildConfig.DEBUG && url.isEmpty())
                        url = BuildConfig.NIGHTSCOUT_DEBUG_SERVER
                    secret = Utils.encryptSHA1(sharedPreferences.getString(Constants.SHARED_PREF_NIGHTSCOUT_UPLOAD_SECRET, "")!!)
                    token = sharedPreferences.getString(Constants.SHARED_PREF_NIGHTSCOUT_UPLOAD_TOKEN, "")!!
                } else {
                    when(key) {
                        Constants.SHARED_PREF_NIGHTSCOUT_UPLOAD_URL -> {
                            url = sharedPreferences.getString(Constants.SHARED_PREF_NIGHTSCOUT_UPLOAD_URL, "")!!.trim().trimEnd('/')
                        }
                        Constants.SHARED_PREF_NIGHTSCOUT_UPLOAD_SECRET -> {
                            secret = Utils.encryptSHA1(sharedPreferences.getString(Constants.SHARED_PREF_NIGHTSCOUT_UPLOAD_SECRET, "")!!)
                        }
                        Constants.SHARED_PREF_NIGHTSCOUT_UPLOAD_TOKEN -> {
                            token = sharedPreferences.getString(Constants.SHARED_PREF_NIGHTSCOUT_UPLOAD_TOKEN, "")!!
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged: " + ex)
        }
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            Log.d(LOG_ID, "OnNotifyData - dataSource: $dataSource")
            if(dataSource == NotifySource.DB_DATA_CHANGED && extras != null) {
                val startTime = extras.getLong(Constants.EXTRA_START_TIME)
                val endTime = extras.getLong(Constants.EXTRA_END_TIME)
                if(startTime > 0 && endTime > 0 && startTime <= endTime) {
                    uploadGlucoseData(context, dbAccess.getGlucoseValuesInRange(startTime, endTime+1))
                } else {
                    Log.w(LOG_ID, "Invalid time range: $startTime - $endTime")
                }
            } else {
                Log.w(LOG_ID, "Unknown dataSource: $dataSource")
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception for $dataSource: " + exc.message.toString())
        }
    }

}