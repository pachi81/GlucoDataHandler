package de.michelinside.glucodatahandler.common.receiver

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
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
import de.michelinside.glucodatahandler.common.tasks.NightscoutSourceTask
import de.michelinside.glucodatahandler.common.utils.GlucoDataUtils
import de.michelinside.glucodatahandler.common.utils.HttpRequest
import de.michelinside.glucodatahandler.common.utils.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import kotlin.math.abs
import kotlin.math.max


open class GlucoseDataReceiver: NamedBroadcastReceiver() {
    private val LOG_ID = "GDH.GlucoseDataReceiver"
    override fun getName(): String {
        return LOG_ID
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var webServerJob: Job? = null
        private var iobJob: Job? = null
        private var interval = -1L
        val JUGGLUCO_WEBSERVER = "http://127.0.0.1:17580"
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

            Log.d(LOG_ID, "Glucose intent received with values ${Utils.dumpBundle(intent.extras)}")

            if (intent.extras!!.containsKey(Constants.EXTRA_SOURCE_PACKAGE)) {
                val packageSource = intent.extras!!.getString(Constants.EXTRA_SOURCE_PACKAGE, "")
                Log.d(LOG_ID, "Intent received from " + packageSource)
                if (packageSource == context.packageName) {
                    Log.d(LOG_ID, "Ignore received intent from itself!")
                    return
                }
            }

            val firstNeededValue = DataSourceTask.getFirstNeedGraphValueTime(if(interval>0L) interval else 1L, false)

            if(ReceiveData.handleIntent(context, DataSource.JUGGLUCO, intent.extras)) {
                SourceStateData.setState(DataSource.JUGGLUCO, SourceState.NONE)
                if(GlucoDataService.appSource != AppSource.WEAR_APP && GlucoDataService.sharedPref?.getBoolean(Constants.SHARED_PREF_SOURCE_JUGGLUCO_WEBSERVER_ENABLED, false) == true) {
                    requestWebserverData(firstNeededValue)
                    requestIobData(context)
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Receive exception: " + exc.message.toString() )
            SourceStateData.setError(DataSource.JUGGLUCO, exc.message.toString())
        }
    }


    private val STREAM_DATA_ENDPOINT = "/x/stream?duration=%d&mg/dL"

    private fun checkResponse(responseCode: Int, httpRequest: HttpRequest): Boolean {
        if (responseCode != HttpURLConnection.HTTP_OK) {
            val error = "Error $responseCode: ${httpRequest.responseMessage}\n${httpRequest.responseError}"
            SourceStateData.setError(DataSource.JUGGLUCO, error)
            return false
        }
        return true
    }

    private fun requestWebserverData(firstValueTime: Long) {
        if(webServerJob?.isActive != true && (firstValueTime > 0L || interval <= 0 || (ReceiveData.sensorStartTime == 0L && !ReceiveData.sensorID.isNullOrEmpty()))) {
            webServerJob = scope.launch {
                try {
                    var retry = 0
                    var lastValueReceived = false
                    val values = mutableListOf<GlucoseValue>()
                    while((!lastValueReceived || values.isEmpty()) && retry < 5) {
                        if(firstValueTime > 0)
                            Thread.sleep(10000)
                        else
                            Thread.sleep(5000)
                        values.clear()
                        retry += 1
                        // if the sensor start time is not set, get at least 10 minutes to be able to calculate the interval
                        val seconds = max(if(interval <= 0L) 600 else 120, if(firstValueTime > 0) (Utils.getElapsedTimeMinute(firstValueTime)+1) * 60 else 3600)
                        Log.i(LOG_ID, "${retry}. request webserver data for $seconds seconds with firstNeededValue=${Utils.getUiTimeStamp(firstValueTime)} and sensorStartTime=${Utils.getUiTimeStamp(ReceiveData.sensorStartTime)} and sensorID=${ReceiveData.sensorID}")
                        val httpRequest = HttpRequest()
                        val responseCode = httpRequest.get(JUGGLUCO_WEBSERVER + STREAM_DATA_ENDPOINT.format(seconds))
                        if (!checkResponse(responseCode, httpRequest)) {
                            return@launch
                        }
                        val result = httpRequest.response
                        Log.d(LOG_ID, "Webserver result: ${result?.take(1000)}")
                        if(!result.isNullOrEmpty()) {
                            val lines = result.lines()
                            var lastAge = 0L
                            var lastTime = 0L
                            var intervalSet = false
                            lines.forEach {
                                val parts = it.split("\t")
                                if(parts.size >= 7) {
                                    val sensor = GlucoDataUtils.checkSerial(parts[0])
                                    val time = parts[2].toLong() * 1000
                                    val age = parts[5].toLong()
                                    if(!intervalSet) {
                                        if(lastAge == 0L && lastTime == 0L) {
                                            lastAge = age
                                            lastTime = time
                                            // get next data set
                                        } else {
                                            val ageDiff = abs(age - lastAge)
                                            val timeDiff = Utils.round(abs(time - lastTime).toFloat()/60000, 0).toInt()
                                            Log.d(LOG_ID, "Age diff: $ageDiff, time diff: $timeDiff -> interval: ${timeDiff/ageDiff}")
                                            setInterval(timeDiff/ageDiff)
                                            intervalSet = true
                                        }
                                    }
                                    val value = parts[6].toInt()
                                    if(ReceiveData.sensorStartTime == 0L && sensor == ReceiveData.sensorID && interval > 0) {
                                        val startTime = time-(age*interval*60000)
                                        ReceiveData.setSensorStartTime(sensor, startTime)
                                        if(firstValueTime == 0L)
                                            return@launch  // stop handling data
                                    }
                                    if(firstValueTime == 0L && interval > 0) {
                                        return@launch
                                    }
                                    if(firstValueTime > 0 && time/1000 < ReceiveData.time/1000 && time > firstValueTime) {
                                        values.add(GlucoseValue(time, value))
                                    } else if(time/1000 == ReceiveData.time/1000) {
                                        lastValueReceived = true  // current value must be part and also historical data
                                    }
                                }
                            }
                        }
                        Log.d(LOG_ID, "End of loop: retry=$retry, lastValueReceived=$lastValueReceived, values.size=${values.size}")
                    }
                    if(values.isNotEmpty()) {
                        Log.i(LOG_ID, "Add ${values.size} values to db")
                        dbAccess.addGlucoseValues(values)
                    } else {
                        Log.i(LOG_ID, "No values found after $retry retries")
                    }
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Webserver exception: " + exc.message.toString() )
                    SourceStateData.setError(DataSource.JUGGLUCO, exc.message.toString())
                }
            }
        }
    }

    private fun setInterval(newInterval: Long) {
        if(interval != newInterval) {
            Log.i(LOG_ID, "Interval changed from $interval to $newInterval")
            interval = newInterval
        }
    }

    private fun requestIobData(context: Context) {
        val iobSupport = GlucoDataService.sharedPref?.getBoolean(Constants.SHARED_PREF_SOURCE_JUGGLUCO_WEBSERVER_IOB_SUPPORT, false) == true
        if(iobJob?.isActive != true && iobSupport ) {
            iobJob = scope.launch {
                try {
                    Log.i(LOG_ID, "Request IOB data")
                    val httpRequest = HttpRequest()
                    val responseCode = httpRequest.get(JUGGLUCO_WEBSERVER + NightscoutSourceTask.PEBBLE_ENDPOINT)
                    if (!checkResponse(responseCode, httpRequest)) {
                        return@launch
                    }
                    val result = httpRequest.response
                    Log.d(LOG_ID, "IOB result: ${result?.take(1000)}")
                    if(result.isNullOrEmpty()) {
                        return@launch
                    }
                    val bundle = Bundle()
                    if(NightscoutSourceTask.parsePebbleIobCob(JSONObject(result), bundle)) {
                        Log.d(LOG_ID, "Parsed IOB data: ${Utils.dumpBundle(bundle)}")
                        Handler(context.mainLooper).post {
                            try {
                                ReceiveData.handleIobCob(context, DataSource.JUGGLUCO, bundle)
                            } catch (exc: Exception) {
                                Log.e(LOG_ID, "Handle IOB exception: " + exc.message.toString() )
                                SourceStateData.setError(DataSource.JUGGLUCO, exc.message.toString())
                            }
                        }
                    } else {
                        Log.w(LOG_ID, "Could not parse IOB data from Juggluco: ${result.take(1000)}")
                        SourceStateData.setError(DataSource.JUGGLUCO, "Could not parse IOB data from Juggluco.")
                    }
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "IOB request exception: " + exc.message.toString() )
                    SourceStateData.setError(DataSource.JUGGLUCO, exc.message.toString())
                }
            }
        }
    }
}