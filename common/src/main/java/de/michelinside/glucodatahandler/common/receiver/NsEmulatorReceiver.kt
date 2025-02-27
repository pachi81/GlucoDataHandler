package de.michelinside.glucodatahandler.common.receiver

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.SourceState
import de.michelinside.glucodatahandler.common.SourceStateData
import de.michelinside.glucodatahandler.common.notifier.DataSource
import de.michelinside.glucodatahandler.common.utils.GlucoDataUtils
import de.michelinside.glucodatahandler.common.utils.Utils
import org.json.JSONArray


open class NsEmulatorReceiver: NamedBroadcastReceiver() {
    private val LOG_ID = "GDH.NsEmulatorReceiver"

    override fun getName(): String {
        return LOG_ID
    }

    override fun onReceiveData(context: Context, intent: Intent) {
        try {
            val extras = intent.extras
            Log.i(LOG_ID, "onReceive called for " + intent.action + ": " + Utils.dumpBundle(extras))
            if (extras != null) {
                val collection = extras.getString("collection")
                if (collection == "entries") {
                    val jsonData = extras.getString("data")
                    if(!jsonData.isNullOrEmpty()) {
                        val jsonArray = JSONArray(jsonData)
                        Log.i(LOG_ID, "${jsonArray.length()} values included")
                        if (jsonArray.length() == 1) { // otherwise it is OOP data! -> ignore it
                            val jsonObject = jsonArray.getJSONObject(0)
                            if(jsonObject.has("type") && jsonObject.getString("type") == "sgv") {
                                if (jsonObject.has("sgv") && jsonObject.has("date")) {
                                    val glucoExtras = Bundle()
                                    val sgv = jsonObject.getDouble("sgv").toFloat()
                                    glucoExtras.putLong(ReceiveData.TIME, jsonObject.getLong("date"))
                                    if(GlucoDataUtils.isMmolValue(sgv)) {
                                        glucoExtras.putInt(ReceiveData.MGDL, GlucoDataUtils.mmolToMg(sgv).toInt())
                                        glucoExtras.putFloat(ReceiveData.GLUCOSECUSTOM, sgv)
                                    } else {
                                        glucoExtras.putInt(ReceiveData.MGDL, sgv.toInt())
                                    }
                                    glucoExtras.putFloat(ReceiveData.RATE, GlucoDataUtils.getRateFromLabel(jsonObject.optString("direction")))
                                    glucoExtras.putInt(ReceiveData.ALARM, 0)
                                    if(jsonObject.has("device")) {
                                        glucoExtras.putString(ReceiveData.SERIAL, jsonObject.getString("device"))
                                    }
                                    ReceiveData.handleIntent(context, DataSource.NS_EMULATOR, glucoExtras)
                                    SourceStateData.setState(DataSource.NS_EMULATOR, SourceState.NONE)
                                } else {
                                    Log.w(LOG_ID, "Missing data: $jsonObject")
                                    SourceStateData.setError(DataSource.NS_EMULATOR, "Missing data: $jsonObject")
                                }
                            } else {
                                Log.w(LOG_ID, "Unsupported type received: $jsonObject")
                            }
                        }
                    } else {
                        Log.w(LOG_ID, "No data: " + Utils.dumpBundle(intent.extras))
                        SourceStateData.setError(DataSource.NS_EMULATOR, "Missing data!")
                    }
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Receive exception: " + exc.message.toString() )
            SourceStateData.setError(DataSource.NS_EMULATOR, exc.message.toString() )
        }
    }
}