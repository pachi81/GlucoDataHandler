package de.michelinside.glucodatahandler.common.receiver

import android.content.Context
import android.content.Intent
import android.util.Log
import de.michelinside.glucodatahandler.common.AppSource
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.SourceState
import de.michelinside.glucodatahandler.common.SourceStateData
import de.michelinside.glucodatahandler.common.database.GlucoseValue
import de.michelinside.glucodatahandler.common.database.dbAccess
import de.michelinside.glucodatahandler.common.notifier.DataSource
import de.michelinside.glucodatahandler.common.tasks.DataSourceTask
import de.michelinside.glucodatahandler.common.utils.GlucoDataUtils
import de.michelinside.glucodatahandler.common.utils.HttpRequest
import de.michelinside.glucodatahandler.common.utils.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.net.HttpURLConnection


open class GlucoseDataReceiver: NamedBroadcastReceiver() {
    private val LOG_ID = "GDH.GlucoseDataReceiver"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var webServerJob: Job? = null
    override fun getName(): String {
        return LOG_ID
    }

    override fun onReceiveData(context: Context, intent: Intent) {
        try {
            val action = intent.action
            if (action != Constants.GLUCODATA_BROADCAST_ACTION) {
                Log.e(LOG_ID, "action=" + action + " != " + Constants.GLUCODATA_BROADCAST_ACTION)
                return
            }

            if (intent.extras == null) {
                Log.e(LOG_ID, "No extras in intent!")
                return
            }

            if (intent.extras!!.containsKey(Constants.EXTRA_SOURCE_PACKAGE)) {
                val packageSource = intent.extras!!.getString(Constants.EXTRA_SOURCE_PACKAGE, "")
                Log.d(LOG_ID, "Intent received from " + packageSource)
                if (packageSource == context.packageName) {
                    Log.d(LOG_ID, "Ignore received intent from itself!")
                    return
                }
            }

            val firstNeededValue = DataSourceTask.getFirstNeedGraphValueTime(1L, false)

            if(ReceiveData.handleIntent(context, DataSource.JUGGLUCO, intent.extras)) {
                SourceStateData.setState(DataSource.JUGGLUCO, SourceState.NONE)
                if(GlucoDataService.appSource != AppSource.WEAR_APP && GlucoDataService.sharedPref?.getBoolean(Constants.SHARED_PREF_SOURCE_JUGGLUCO_WEBSERVER_ENABLED, false) == true) {
                    requestWebserverData(firstNeededValue)
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Receive exception: " + exc.message.toString() )
            SourceStateData.setError(DataSource.JUGGLUCO, exc.message.toString())
        }
    }

    private val JUGGLUCO_WEBSERVER = "http://127.0.0.1:17580/x/stream?duration=%d&mg/dL"

    private fun requestWebserverData(firstValueTime: Long) {
        if(webServerJob?.isActive == false && (firstValueTime > 0L || (ReceiveData.sensorStartTime == 0L && !ReceiveData.sensorID.isNullOrEmpty()))) {
            Log.i(LOG_ID, "Request webserver data with firstNeededValue=${Utils.getUiTimeStamp(firstValueTime)} and sensorStartTime=${Utils.getUiTimeStamp(ReceiveData.sensorStartTime)} and sensorID=${ReceiveData.sensorID}")
            webServerJob = scope.launch {
                try {
                    val seconds = if(firstValueTime > 0) Utils.getElapsedTimeMinute(firstValueTime) * 60 else 3600
                    val httpRequest = HttpRequest()
                    val responseCode = httpRequest.get(JUGGLUCO_WEBSERVER.format(seconds))
                    Log.d(LOG_ID, "Requesting $seconds seconds data returns code: $responseCode")
                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        val error = "Error $responseCode: ${httpRequest.responseMessage}\n${httpRequest.responseError}"
                        SourceStateData.setError(DataSource.JUGGLUCO, error)
                        return@launch
                    }
                    val result = httpRequest.response
                    Log.d(LOG_ID, "Webserver result: $result")
                    if(result.isNullOrEmpty()) {
                        return@launch
                    }
                    val lines = result.lines()
                    Log.d(LOG_ID, "Webserver lines: ${lines.size}")
                    val values = mutableListOf<GlucoseValue>()
                    lines.forEach {
                        val parts = it.split("\t")
                        Log.d(LOG_ID, "Webserver parts: $parts")
                        if(parts.size >= 7) {
                            val sensor = GlucoDataUtils.checkSerial(parts[0])
                            val time = parts[2].toLong() * 1000
                            val age = parts[5].toLong()
                            val startTime = time-(age*60000)
                            val value = parts[6].toInt()
                            Log.d(LOG_ID, "Webserver sensor=$sensor, time=${Utils.getUiTimeStamp(time)}, startTime=${Utils.getUiTimeStamp(startTime)}, value=$value")
                            if(ReceiveData.sensorStartTime == 0L && sensor == ReceiveData.sensorID) {
                                ReceiveData.setSensorStartTime(sensor, startTime)
                                if(firstValueTime == 0L)
                                    return@launch  // stop handling data
                            }
                            if(firstValueTime > 0L) {
                                values.add(GlucoseValue(time, value))
                            }
                        }
                    }
                    if(values.isNotEmpty()) {
                        Log.i(LOG_ID, "Add ${values.size} values to db")
                        dbAccess.addGlucoseValues(values)
                    }
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Webserver exception: " + exc.message.toString() )
                    SourceStateData.setError(DataSource.JUGGLUCO, exc.message.toString())
                }
            }
        }
    }
}