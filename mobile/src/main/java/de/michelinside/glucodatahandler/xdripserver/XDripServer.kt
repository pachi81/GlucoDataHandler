package de.michelinside.glucodatahandler.xdripserver

import android.util.Log
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.database.GlucoseValue
import de.michelinside.glucodatahandler.common.database.dbAccess
import de.michelinside.glucodatahandler.common.utils.GlucoDataUtils
import io.ktor.server.engine.*
import io.ktor.server.cio.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class XDripServer {

    private val LOG_ID = "GDH.XDripServer"

    fun startServer() {
        embeddedServer(CIO, port = 17580) {
            routing {
                get("/sgv.json") {
                    val brief = call.request.queryParameters["brief_mode"]?.lowercase() == "y"
                    val count: Int = call.request.queryParameters["count"]?.toIntOrNull() ?: 25

                    val values = getGlucoseValues()
                    val response = values.createResponse(brief, count)
                    call.respondText(response)
                }
                get("/pebble") {
                    val values = getGlucoseValues()
                    val response = values.createPebbleResponse()
                    call.respondText(response)
                }
                get("/status.json") {
                    val values = getGlucoseValues()
                    val response = values.createStatusResponse()
                    call.respondText(response)
                }

            }
        }.start(wait = false)
    }


    fun List<GlucoseValue>.createPebbleResponse(): String {
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

        val status = PebbleStatus(now = GlucoDataUtils.getGlucoseTime(System.currentTimeMillis()))
        val bgs = PebbleBg(
            bg.toString(),
            trend = 4,                  // Doesn't seem to change in Juggluco
            direction = "flat",         // Doesn't seem to change in Juggluco
            datetime = glucose.timestamp,
            bgdelta = GlucoDataUtils.mgToMmol(ReceiveData.delta).toString(),
            iob = iob.toString())

        val entry = PebbleEntry(status = listOf(status), bgs = listOf(bgs), cals = emptyList<Int>())
        return Json { prettyPrint = true }.encodeToString(entry)
    }


    fun List<GlucoseValue>.createStatusResponse(): String {
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

    fun List<GlucoseValue>.createResponse(brief: Boolean, count: Int): String {
        if (this.isEmpty()) {
            return "Error"
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
        val sortedValues = this.sortedByDescending { it.timestamp } // oldest -> newest
        val units = if (ReceiveData.isMmol) "mmol" else "mgdl"

        val entries = sortedValues.mapIndexed { index, glucose ->
            val prev = if (index < sortedValues.size-1) sortedValues[index + 1].value.toDouble() else null
            val delta = prev?.let { sortedValues[index].value - it } ?: 0.0

            Log.i(LOG_ID, "Glucose: ${glucose.value} ${GlucoDataUtils.mgToMmol(glucose.value.toFloat())} delta: ${delta} ${GlucoDataUtils.mgToMmol(delta.toFloat())}")

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
                delta = delta,
                filtered = if (brief) null else glucose.value * 1000,
                unfiltered = if (brief) null else glucose.value * 1000,
                device = if (brief) null else "GDH",
                direction = direction,
                noise = 1,
                rssi = if (brief) null else 100,
                type = if (brief) null else "svg",
                units_hint = units
            )
        }

        return Json { prettyPrint = true }.encodeToString(entries.take(count))
    }

    fun getGlucoseValues(): List<GlucoseValue> {
        val values = dbAccess.getGlucoseValues(12)
//        val values = dbAccess.getLiveValuesByTimeSpan(1);
//        values.forEach { glucose ->
//            Log.i(LOG_ID, "Glucose: ${glucose.value} ${glucose.timestamp}")
//        }
//        return values.takeLast(count)
        return values
    }

}