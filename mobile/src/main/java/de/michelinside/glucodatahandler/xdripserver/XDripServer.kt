package de.michelinside.glucodatahandler.xdripserver

import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService.Companion.sharedPref
import de.michelinside.glucodatahandler.common.utils.Log
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.database.GlucoseValue
import de.michelinside.glucodatahandler.common.database.dbAccess
import de.michelinside.glucodatahandler.common.utils.GlucoDataUtils
import io.ktor.server.engine.*
import io.ktor.server.cio.*
import io.ktor.server.request.uri
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.ServerSocket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.time.Instant

object XDripServer {
    private val LOG_ID = "GDH.XDripServer"

    private var server: EmbeddedServer<*, *>? = null
    private val Port = 17580
    private val NumRecords = 24

    fun startServer(): Boolean {
        if (!isPortOpen())
            return false

        return start()
    }

    fun stopServer() {
        try {
            if (isServerRunning()) {
                stopServer {
                    server = null
                    Log.i(LOG_ID, "XDrip+ server stopped")
                }
            }
        } catch (e: Exception) {
            Log.i(LOG_ID, "XDrip+ server failed to stop")
            server = null
        }
    }


    fun isPortOpen(port: Int = Port): Boolean {
        try {
            ServerSocket(port).use {
                Log.i(LOG_ID, "Port ${port} open.")
                return true
            }
        } catch (e: java.net.BindException) {
            Log.i(LOG_ID, "Port ${port} in use.")
        }

        return false
    }

    fun isServerRunning(): Boolean {
        return server != null
    }

    private fun start(): Boolean {
        var started = false

        try {
            server = embeddedServer(CIO, port = Port) {
                routing {
                    get("/sgv.json") {
                        Log.i(LOG_ID, "XDrip+ server request: ${call.request.uri}")
                        val brief = call.request.queryParameters["brief_mode"]?.lowercase() == "y"
                        val count: Int = call.request.queryParameters["count"]?.toIntOrNull() ?: 24
                        val sensor = call.request.queryParameters["sensor"]?.lowercase() == "y"
                        val reduced = sharedPref?.getBoolean(Constants.SHARED_PREF_XDRIP_SERVER_REDUCE_DATA, false) ?: false

                        val values = getGlucoseValues(brief)
                        val response = values.createResponse(brief, count, sensor, reduced)
                        call.respondText(response)
                    }
                    get("/pebble") {
                        Log.i(LOG_ID, "XDrip+ server request: ${call.request.uri}")
                        val values = getGlucoseValues(false)
                        val response = values.createPebbleResponse()
                        call.respondText(response)
                    }
                    get("/status.json") {
                        Log.i(LOG_ID, "XDrip+ server request: ${call.request.uri}")
                        val values = getGlucoseValues(false)
                        val response = values.createStatusResponse()
                        call.respondText(response)
                    }

                }
            }.start(wait = false)
            Log.i(LOG_ID, "XDrip+ server started")
            started = true
        } catch (e: Exception) {
            Log.i(LOG_ID, "Failed to start XDrip+ server")
        }

        return started
    }

    fun stopServer(onStopped: (() -> Unit)? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            server?.stop(1000, 1000, TimeUnit.MILLISECONDS)
            withContext(Dispatchers.Main) {
                onStopped?.invoke()
            }
        }
    }

    private fun List<GlucoseValue>.createPebbleResponse(): String {
        if (this.isEmpty()) {
            return "Error"
        }
        val glucose = this.first()
        var bg = glucose.value.toFloat();
        if (!GlucoDataUtils.isMmolValue(bg)) {
            bg = GlucoDataUtils.mgToMmol(bg)        // Seems to be always mmol in Juggluco
        }
        var iob = ReceiveData.iob
        iob = if (iob.isNaN()) 0.0f else iob

        val status =
            PebbleStatus(now = GlucoDataUtils.getGlucoseTime(System.currentTimeMillis()))
        val bgs = PebbleBg(
            bg.toString(),
            trend = 4,                  // Doesn't seem to change in Juggluco
            direction = "flat",         // Doesn't seem to change in Juggluco
            datetime = glucose.timestamp,
            bgdelta = GlucoDataUtils.mgToMmol(ReceiveData.delta).toString(),
            iob = iob.toString()
        )

        val entry =
            PebbleEntry(status = listOf(status), bgs = listOf(bgs), cals = emptyList<Int>())
        return Json { prettyPrint = true }.encodeToString(entry)
    }


    private fun List<GlucoseValue>.createStatusResponse(): String {
        val units = if (ReceiveData.isMmol) "mmol/L" else "mg/dL"
        var min = ReceiveData.low;
        min = if (GlucoDataUtils.isMmolValue(min)) GlucoDataUtils.mmolToMg(min) else min
        var max = ReceiveData.high;
        max = if (GlucoDataUtils.isMmolValue(max)) GlucoDataUtils.mmolToMg(max) else max

        val response = SettingsEntry(
            settings = SettingsData(
                units = units,
                thresholds = SettingsThresholds(
                    bgHigh = max.toInt(),
                    bgLow = min.toInt()
                )
            )
        )

        return Json { prettyPrint = true }.encodeToString(response)
    }

    private fun List<GlucoseValue>.createResponse(
        brief: Boolean,
        count: Int,
        sensor: Boolean,
        reduced: Boolean
    ): String {
        if (this.isEmpty()) {
            return "Error"
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
        val units = if (ReceiveData.isMmol) "mmol" else "mgdl"
        var sensorStatus = getSensorAge(ReceiveData.sensorStartTime)

        val entries = this.mapIndexed { index, glucose ->
            val prev =
                if (index < this.size - 1) this[index + 1].value.toDouble() else null
            val delta = prev?.let { this[index].value - it } ?: 0.0

//            Log.i(
//                LOG_ID,
//                "Glucose: ${glucose.value} ${GlucoDataUtils.mgToMmol(glucose.value.toFloat())} delta: ${delta} ${
//                    GlucoDataUtils.mgToMmol(delta.toFloat())
//                }"
//            )

            val direction = when {
                GlucoDataUtils.mgToMmol(delta.toFloat()) >= 3.0 -> "DoubleUp"
                GlucoDataUtils.mgToMmol(delta.toFloat()) >= 2.0 -> "FortyFiveUp"
                GlucoDataUtils.mgToMmol(delta.toFloat()) >= 1.0 -> "SingleUp"
                GlucoDataUtils.mgToMmol(delta.toFloat()) <= -1.0 -> "SingleDown"
                GlucoDataUtils.mgToMmol(delta.toFloat()) <= -2.0 -> "FortyFiveDown"
                GlucoDataUtils.mgToMmol(delta.toFloat()) <= -3.0 -> "DoubleDown"
                else -> "Flat"
            }

            XDripSvgEntry(
                _id = if (brief) null else "${ReceiveData.sensorID}#${index + 1}",
                dateString = if (brief) null else dateFormat.format(Date(glucose.timestamp)),
                sysTime = if (brief) null else dateFormat.format(Date(glucose.timestamp)),
                date = glucose.timestamp,
                sgv = glucose.value,
                delta = if (index > 0 && brief && reduced) null else delta,
                filtered = if (brief) null else glucose.value * 1000,
                unfiltered = if (brief) null else glucose.value * 1000,
                device = if (brief) null else "GDH",
                direction = if (index > 0 && brief && reduced) null else direction,
                noise = if (index > 0 && brief && reduced) null else 1,
                rssi = if (brief) null else 100,
                type = if (brief) null else "svg",
                units_hint = if (index == 0) units else null,
                sensor_status = if (index == 0 && sensor) sensorStatus else null
            )
        }

        return Json { prettyPrint = true }.encodeToString(entries.take(count))
    }

    private fun getSensorAge(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diffMs = now - timestamp

        val sdf = SimpleDateFormat("dd-MM-yy HH:mm", Locale.getDefault())
        val dateStr = sdf.format(Date(timestamp))

        val days = TimeUnit.MILLISECONDS.toDays(diffMs)
        val hours = TimeUnit.MILLISECONDS.toHours(diffMs) % 24

        val ageStr = "(${days}d ${hours}h)"
        return "$dateStr $ageStr"
    }

    private fun getGlucoseValues(brief: Boolean): List<GlucoseValue> {
        val values: List<GlucoseValue> = runBlocking {
            dbAccess.getLiveValuesByTimeSpan(2).first()
        }

        val sorted = values.sortedByDescending { it.timestamp }
//        for (glucose in sorted) {
//            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
//            Log.i(LOG_ID, "Sorted - ${dateFormat.format(Date(glucose.timestamp))}")
//        }

        if (brief) {
            val filtered = sorted.filterList()
//            for (glucose in filtered) {
//                val dateFormat =
//                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
//                Log.i(LOG_ID, "Filtered - ${dateFormat.format(Date(glucose.timestamp))}")
//            }
            return filtered
        } else
            return sorted.take(NumRecords)
    }


    private fun List<GlucoseValue>.filterList(
    intervalMinutes: Int = 5,
    toleranceSeconds: Int = 30
    ): List<GlucoseValue> {
        if (isEmpty()) return emptyList()

        val sorted = this.sortedBy { it.timestamp } // ascending timestamps
        val result = mutableListOf<GlucoseValue>()

        val intervalMs = intervalMinutes * 60 * 1000L
        val toleranceMs = toleranceSeconds * 1000L

        // Always keep the first reading
        var lastKeptTimestamp = sorted.first().timestamp
        result.add(sorted.first())

        for (glucose in sorted.drop(1)) {
            val diff = glucose.timestamp - lastKeptTimestamp

            // Keep if it's within tolerance or after the interval
            if (diff >= intervalMs - toleranceMs) {
                result.add(glucose)
                lastKeptTimestamp = glucose.timestamp
            }
        }

        // Reverse to match original input order
        return result.reversed()
    }

}