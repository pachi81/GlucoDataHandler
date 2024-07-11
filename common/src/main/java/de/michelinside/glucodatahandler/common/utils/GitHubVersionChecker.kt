package de.michelinside.glucodatahandler.common.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import org.json.JSONObject
import java.net.HttpURLConnection
import kotlinx.coroutines.*
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

class GitHubVersionChecker(val repo: String, val curVersion: String, val context: Context) {
    private val LOG_ID = "GDH.Utils.GitHubVersionChecker"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val LATEST = "latest"
    private val LATEST_TIME = "latest_time"

    private val TAG_VERSION = "tag_name"
    private val TAG_URL = "html_url"
    private val TAG_CONTENT = "body"
    private var newVersionAvailable = false


    private val BASE_URL = "https://api.github.com/repos/pachi81/%s/releases/latest"
    private val endpoint: String get() = BASE_URL.format(repo)

    private var latestVersionObject: JSONObject? = null
    private var latestVersionTime: Long = 0L
    private var lastCheckTime: Long = 0L
    private val sharedPref: SharedPreferences = context.getSharedPreferences(LOG_ID, Context.MODE_PRIVATE)
    private var checkVersionActive = AtomicBoolean(false)

    init {
        loadLatest()
    }

    private fun getHeader(): MutableMap<String, String> {
        val result = mutableMapOf(
            "user-agent" to repo,
            "accept" to "application/json",
            "content-type" to "application/json"
        )
        return result
    }

    private suspend fun requestLatest() {
        try {
            Log.v(LOG_ID, "requestLatest called")
            lastCheckTime = System.currentTimeMillis()
            if(HttpRequest.isConnected(context)) {
                val httpRequest = HttpRequest()
                if (httpRequest.get(endpoint, getHeader()) == HttpURLConnection.HTTP_OK) {
                    Log.v(LOG_ID, "Received data: ${httpRequest.response}")
                    latestVersionObject = JSONObject(httpRequest.response!!)
                    latestVersionTime = System.currentTimeMillis()
                    saveLatest()
                    checkForNewVersion()
                    Log.i(LOG_ID, "Received GitHub version $version - new: $hasNewVersion")
                    checkVersionActive.set(false)
                    if (hasNewVersion) {
                        withContext(Dispatchers.Main) {
                            InternalNotifier.notify(context, NotifySource.NEW_VERSION_AVAILABLE, null)
                        }
                    }
                } else {
                    Log.e(LOG_ID, "Error ${httpRequest.code}: ${httpRequest.responseMessage}\n${httpRequest.responseError}")
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Error requesting latest version: ${exc.message}")
            latestVersionObject = null
        }
    }

    private fun checkForNewVersion() {
        newVersionAvailable = if (version.isNotEmpty()) {
            val currentVersion = Utils.getVersion(curVersion)
            val compare = Utils.compareVersion(curVersion, version)
            Log.v(LOG_ID, "Compare new version $version with current version $currentVersion: $compare")
            compare < 0
        } else {
            false
        }
    }

    private fun saveLatest() {
        Log.v(LOG_ID, "saveLatest called")
        if(latestVersionObject != null) {
            with(sharedPref.edit()) {
                putString(LATEST, latestVersionObject.toString())
                putLong(LATEST_TIME,latestVersionTime)
                apply()
            }
        }
    }

    private fun loadLatest() {
        Log.v(LOG_ID, "loadLatest called")
        try {
            val latest = sharedPref.getString(LATEST, null)
            if (!latest.isNullOrEmpty()) {
                latestVersionTime = sharedPref.getLong(LATEST_TIME, 0L)
                latestVersionObject = JSONObject(latest)
                checkForNewVersion()
                Log.i(LOG_ID, "Loaded GitHub version $version - new: $hasNewVersion")
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Error loading latest: ${exc.message}")
        }
    }

    private fun duration(time: Long): Duration {
        return Duration.ofMillis(System.currentTimeMillis()-time)
    }

    private fun checkObsolete(time: Long, minDuration: Duration): Boolean {
        return duration(time) >= minDuration
    }

    private fun isObsolete(days: Long): Boolean {
        if(latestVersionTime > 0 && latestVersionObject != null) {
            return checkObsolete(latestVersionTime, Duration.ofDays(days))
        }
        return true
    }

    private fun getStringFromObject(tag: String): String {
        if(latestVersionObject != null) {
            return latestVersionObject!!.optString(tag, "")
        }
        return ""
    }

    val version: String get() {
        return Utils.getVersion(getStringFromObject(TAG_VERSION))
    }

    val url: String get() {
        return getStringFromObject(TAG_URL)
    }

    val content: String get() {
        return getStringFromObject(TAG_CONTENT)
    }

    private fun canCheckVersion(days: Long): Boolean {
        return !checkVersionActive.get() && isObsolete(days) && checkObsolete(lastCheckTime, Duration.ofDays(1))
    }

    val hasNewVersion: Boolean get() = newVersionAvailable

    fun checkVersion(days: Long = 7) {
        try {
            Log.v(LOG_ID, "checkVersion called")
            if(canCheckVersion(days)) {
                checkVersionActive.set(true)
                scope.launch() {
                    requestLatest()
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Error requesting latest version: ${exc.message}")
            latestVersionObject = null
        }
    }

}
