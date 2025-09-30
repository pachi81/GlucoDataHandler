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
                    val values = getGlucoseValues()
                    val response = values.createResponse()
                    call.respondText(response)
                }
            }
        }.start(wait = false)
    }


    fun List<GlucoseValue>.createResponse(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
        val sortedValues = this.sortedByDescending { it.timestamp } // oldest -> newest

        val entries = sortedValues.mapIndexed { index, glucose ->
            XDripSvgEntry(
                _id = "${ReceiveData.sensorID}#${index + 1}",
                dateString = dateFormat.format(Date(glucose.timestamp)),
                sysTime = dateFormat.format(Date(glucose.timestamp)),
                date = glucose.timestamp,
                sgv = glucose.value,
                delta = 0.0,
                filtered = glucose.value * 1000,
                unfiltered = glucose.value * 1000,
                device = "GDH",
                direction = "flat",
                noise = 1,
                rssi = 100,
                type = "svg",
                units_hint = if (index == 0) "mmol" else null
            )
        }

        return Json { prettyPrint = true }.encodeToString(entries)
    }

    fun getGlucoseValues(): List<GlucoseValue> {
        val values = dbAccess.getGlucoseValues(12)
        values.forEach { glucose ->
            Log.i(LOG_ID, "Glucose: ${glucose.value} ${glucose.timestamp}")
        }
        return values.takeLast(25)
    }

}