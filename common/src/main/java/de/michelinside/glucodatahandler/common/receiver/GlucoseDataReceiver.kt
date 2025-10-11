package de.michelinside.glucodatahandler.common.receiver

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.util.Log
import de.michelinside.glucodatahandler.common.AppSource
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.R
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.SourceState
import de.michelinside.glucodatahandler.common.SourceStateData
import de.michelinside.glucodatahandler.common.database.GlucoseValue
import de.michelinside.glucodatahandler.common.database.dbAccess
import de.michelinside.glucodatahandler.common.notifier.DataSource
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

            Log.d(LOG_ID, "Glucose intent received with values ${Utils.dumpBundle(intent.extras)}")

            if (intent.extras!!.containsKey(Constants.EXTRA_SOURCE_PACKAGE)) {
                val packageSource = intent.extras!!.getString(Constants.EXTRA_SOURCE_PACKAGE, "")
                Log.d(LOG_ID, "Intent received from " + packageSource)
                if (packageSource == context.packageName) {
                    Log.d(LOG_ID, "Ignore received intent from itself!")
                    return
                }
            }

            if(ReceiveData.handleIntent(context, DataSource.JUGGLUCO, intent.extras)) {
                SourceStateData.setState(DataSource.JUGGLUCO, SourceState.NONE)
                checkHandleWebServerRequests(context)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Receive exception: " + exc.message.toString() )
            SourceStateData.setError(DataSource.JUGGLUCO, exc.message.toString())
        }
    }

    companion object {
        private val LOG_ID = "GDH.GlucoDataReceiver"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var webServerJob: Job? = null
        private var iobJob: Job? = null
        private var interval = -1L
        private var lastServerTime = 0L
        val JUGGLUCO_WEBSERVER = "http://127.0.0.1:17580"

        private val STREAM_DATA_ENDPOINT = "/x/stream?duration=%d&mg/dL"


        fun resetLastServerTime() {
            lastServerTime = 0L
        }

        fun checkHandleWebServerRequests(context: Context, handleNewValue: Boolean = false) {
            if(hasWebServerSupport()) {
                requestWebserverData(context, handleNewValue)
                requestIobData(context)
            }
        }

        private fun hasWebServerSupport(): Boolean {
            return (GlucoDataService.appSource != AppSource.WEAR_APP
                    && GlucoDataService.sharedPref?.getBoolean(Constants.SHARED_PREF_SOURCE_JUGGLUCO_ENABLED, true) == true
                    && GlucoDataService.sharedPref?.getBoolean(Constants.SHARED_PREF_SOURCE_JUGGLUCO_WEBSERVER_ENABLED, false) == true)
        }

        private fun checkResponse(responseCode: Int, httpRequest: HttpRequest): Boolean {
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val error = "Error $responseCode: ${httpRequest.responseMessage}\n${httpRequest.responseError}"
                SourceStateData.setError(DataSource.JUGGLUCO, error)
                return false
            }
            return true
        }

        private fun requestWebserverData(context: Context, handleNewValue: Boolean = false) {
            val maxTime = if(handleNewValue) System.currentTimeMillis() else ReceiveData.time
            if(webServerJob?.isActive != true && (interval <= 0 || abs(Utils.getTimeDiffMinute(lastServerTime, maxTime)) > interval || (ReceiveData.sensorStartTime == 0L && !ReceiveData.sensorID.isNullOrEmpty()))) {
                webServerJob = scope.launch {
                    try {
                        var retry = 0
                        var lastValueReceived = false
                        val values = mutableListOf<GlucoseValue>()
                        val firstValueTime = if(lastServerTime==0L)
                                if(GlucoDataService.appSource == AppSource.AUTO_APP)
                                    System.currentTimeMillis() - Constants.DB_MAX_DATA_GDA_TIME_MS
                                else
                                    System.currentTimeMillis()-Constants.DB_MAX_DATA_WEAR_TIME_MS
                            else if(abs(Utils.getTimeDiffMinute(lastServerTime, maxTime))>interval) lastServerTime
                            else 0L
                        while((!lastValueReceived || values.isEmpty()) && retry < 3) {
                            if(!handleNewValue || retry > 0) {
                                if(firstValueTime > 0)
                                    Thread.sleep(10000)
                                else
                                    Thread.sleep(5000)
                            }
                            values.clear()
                            retry += 1
                            // if the sensor start time is not set, get at least 10 minutes to be able to calculate the interval
                            val seconds = max(if(interval <= 0L) 600 else 120, if(firstValueTime > 0) (Utils.getElapsedTimeMinute(firstValueTime)+1) * 60 else 3600)
                            Log.i(LOG_ID, "${retry}. request webserver data for $seconds seconds with firstNeededValue=${Utils.getUiTimeStamp(firstValueTime)}, interval= and sensorStartTime=${Utils.getUiTimeStamp(ReceiveData.sensorStartTime)} and sensorID=${ReceiveData.sensorID} - last server time ${Utils.getUiTimeStamp( lastServerTime)}")
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
                                var intervalChanged = false
                                var lastSensor = ""
                                lines.forEach {
                                    val parts = it.split("\t")
                                    if(parts.size >= 7) {
                                        val sensor = GlucoDataUtils.checkSerial(parts[0])
                                        val time = parts[2].toLong() * 1000
                                        val age = parts[5].toLong()
                                        if(!intervalSet) {
                                            if((lastAge == 0L && lastTime == 0L) || lastSensor != sensor) {
                                                lastAge = age
                                                lastTime = time
                                                lastSensor = sensor ?: ""
                                                // get next data set
                                            } else {
                                                val ageDiff = abs(age - lastAge)
                                                val timeDiff = Utils.round(abs(time - lastTime).toFloat()/60000, 0).toInt()
                                                Log.d(LOG_ID, "Age diff: $ageDiff, time diff: $timeDiff -> new-interval: ${timeDiff/ageDiff} - cur-interval: $interval")
                                                intervalChanged = setInterval(timeDiff/ageDiff)
                                                intervalSet = true
                                            }
                                        }
                                        if(intervalSet) {
                                            if(Utils.getTimeDiffMinute(time, lastServerTime) <= interval) {
                                                lastServerTime = time
                                            } else if(Utils.getElapsedTimeMinute(time) > if(interval == 1L) 5 else (2*interval)) {
                                                Log.w(LOG_ID, "No older values received for ${Utils.getTimeDiffMinute(time, lastServerTime)} minutes - set last server time: ${Utils.getUiTimeStamp(time)}")
                                                lastServerTime = time
                                            } else {
                                                Log.d(LOG_ID, "Missing data for set interval $interval - diff time ${Utils.getTimeDiffMinute(time, lastServerTime)}")
                                            }
                                        }

                                        val value = parts[6].toInt()
                                        if(ReceiveData.sensorStartTime == 0L && sensor == ReceiveData.sensorID && interval > 0) {
                                            val startTime = time-(age*interval*60000)
                                            if(time > lastServerTime)
                                                lastServerTime = time // reset to new sensor
                                            Log.i(LOG_ID, "New sensor start time found for: $sensor, time=${Utils.getUiTimeStamp(startTime)} - last server time ${Utils.getUiTimeStamp( lastServerTime)}")
                                            ReceiveData.setSensorStartTime(sensor, startTime)
                                        }
                                        if(firstValueTime == 0L && interval > 0) {
                                            return@launch
                                        }
                                        if(firstValueTime > 0 && time/1000 < maxTime/1000 && time > firstValueTime) {
                                            values.add(GlucoseValue(time, value))
                                        }
                                        if(abs(Utils.getTimeDiffMinute(time, maxTime)) == 0L) {
                                            lastValueReceived = true  // current value must be part and also historical data
                                        }
                                    }
                                }
                                if(intervalChanged && firstValueTime > 0 && lastValueReceived && values.isEmpty()) {
                                    if(maxTime - (interval*60000) < firstValueTime)
                                        return@launch  // stop loop as there are already all data
                                }
                            }
                            Log.d(LOG_ID, "End of loop: retry=$retry, lastValueReceived=$lastValueReceived, values.size=${values.size}")
                        }
                        if(values.isNotEmpty()) {
                            Log.i(LOG_ID, "Add ${values.size} values to db - last server time ${Utils.getUiTimeStamp( lastServerTime)}")
                            dbAccess.addGlucoseValues(values)
                            if(handleNewValue) {
                                Log.d(LOG_ID, "Check last value for new one: ${Utils.getUiTimeStamp(values.last().timestamp)} - value: ${values.last().value}")
                                val glucoExtras = Bundle()
                                glucoExtras.putLong(ReceiveData.TIME, values.last().timestamp)
                                glucoExtras.putInt(ReceiveData.MGDL, values.last().value)
                                glucoExtras.putFloat(ReceiveData.RATE, Float.NaN)
                                Handler(context.mainLooper).post {
                                    try {
                                        ReceiveData.handleIntent(context, DataSource.JUGGLUCO, glucoExtras)
                                    } catch (exc: Exception) {
                                        Log.e(LOG_ID, "Handle new value exception: " + exc.message.toString() )
                                        SourceStateData.setError(DataSource.JUGGLUCO, exc.message.toString())
                                    }
                                }
                            }
                        } else {
                            Log.i(LOG_ID, "No values found after $retry retries - last server time ${Utils.getUiTimeStamp( lastServerTime)}")
                        }
                    } catch (exc: Exception) {
                        Log.e(LOG_ID, "Webserver exception: " + exc.message.toString() )
                        SourceStateData.setError(DataSource.JUGGLUCO, exc.message.toString())
                    }
                }
            } else if(abs(ReceiveData.getTimeDiffMinute(lastServerTime)) <= interval) {
                lastServerTime = ReceiveData.time
                Log.d(LOG_ID, "No webserver request needed, update last server time: ${Utils.getUiTimeStamp(lastServerTime)} - interval: $interval")
            }
        }

        private fun setInterval(newInterval: Long): Boolean {
            if(newInterval > 0 && interval != newInterval) {
                Log.i(LOG_ID, "Interval changed from $interval to $newInterval")
                if(newInterval == 1L || newInterval == 5L)
                    interval = newInterval
                else if(newInterval == 2L)
                    interval = 1
                else if(newInterval in 4L..6L)
                    interval = 5
                return true
            }
            return false
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
                            SourceStateData.setError(DataSource.JUGGLUCO, context.resources!!.getString(
                                R.string.invalid_iob_value))
                        }
                    } catch (exc: Exception) {
                        Log.e(LOG_ID, "IOB request exception: " + exc.message.toString() )
                        SourceStateData.setError(DataSource.JUGGLUCO, exc.message.toString())
                    }
                }
            }
        }
    }
}