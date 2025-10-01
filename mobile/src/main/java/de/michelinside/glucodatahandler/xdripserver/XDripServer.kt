package de.michelinside.glucodatahandler.xdripserver

import android.util.Log
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.database.GlucoseValue
import de.michelinside.glucodatahandler.common.database.dbAccess
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

                    val values = getGlucoseValues(count)
                    val response = values.createResponse(brief)
                    call.respondText(response)
                }
            }
        }.start(wait = false)
    }


    fun List<GlucoseValue>.createResponse(brief: Boolean): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
        val sortedValues = this.sortedByDescending { it.timestamp } // oldest -> newest


        val entries = sortedValues.mapIndexed { index, glucose ->
            XDripSvgEntry(
                _id = if (brief) null else "${ReceiveData.sensorID}#${index + 1}",
                dateString = if (brief) null else dateFormat.format(Date(glucose.timestamp)),
                sysTime = if (brief) null else dateFormat.format(Date(glucose.timestamp)),
                date = glucose.timestamp,
                sgv = glucose.value,
                delta = 0.0,
                filtered = if (brief) null else glucose.value * 1000,
                unfiltered = if (brief) null else glucose.value * 1000,
                device = if (brief) null else "GDH",
                direction = "flat",
                noise = 1,
                rssi = if (brief) null else 100,
                type = if (brief) null else "svg",
                units_hint = if (index == 0) "mmol" else null
            )
        }

        return Json { prettyPrint = true }.encodeToString(entries)
    }

    fun getGlucoseValues(count: Int): List<GlucoseValue> {
        val values = dbAccess.getGlucoseValues(12)
        values.forEach { glucose ->
            Log.i(LOG_ID, "Glucose: ${glucose.value} ${glucose.timestamp}")
        }
        return values.takeLast(count)
    }

}