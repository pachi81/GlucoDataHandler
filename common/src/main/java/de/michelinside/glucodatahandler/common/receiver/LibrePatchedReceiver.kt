package de.michelinside.glucodatahandler.common.receiver

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.SourceState
import de.michelinside.glucodatahandler.common.SourceStateData
import de.michelinside.glucodatahandler.common.notifier.DataSource
import de.michelinside.glucodatahandler.common.utils.Utils


open class LibrePatchedReceiver: NamedBroadcastReceiver() {
    private val LOG_ID = "GDH.LibrePatchedReceiver"

    override fun getName(): String {
        return LOG_ID
    }

    override fun onReceiveData(context: Context, intent: Intent) {
        try {
            val extras = intent.extras
            Log.i(LOG_ID, "onReceive called for " + intent.action + ": " + Utils.dumpBundle(extras))
            if (extras != null) {
                if (extras.containsKey(Constants.EXTRA_SOURCE_PACKAGE)) {
                    val packageSource = extras.getString(Constants.EXTRA_SOURCE_PACKAGE, "")
                    Log.d(LOG_ID, "Intent received from " + packageSource)
                    if (packageSource == context.packageName) {
                        Log.d(LOG_ID, "Ignore received intent from itself!")
                        return
                    }
                }
                if(intent.action == Constants.XDRIP_ACTION_GLUCOSE_READING) {
                    if(extras.containsKey("glucose") && extras.containsKey("timestamp")) {
                        val glucoExtras = Bundle()
                        glucoExtras.putLong(ReceiveData.TIME, extras.getLong("timestamp"))
                        glucoExtras.putInt(ReceiveData.MGDL,extras.getDouble("glucose").toInt())
                        if(extras.containsKey("bleManager")) {
                            val bleManager = extras.getBundle("bleManager")
                            if(bleManager != null && bleManager.containsKey("sensorSerial")) {
                                glucoExtras.putString(ReceiveData.SERIAL, bleManager.getString("sensorSerial"))
                            }
                        }
                        if(extras.containsKey("sas")) {
                            val sas = extras.getBundle("sas")
                            if(sas != null && sas.containsKey("currentSensor")) {
                                val currentSensor = sas.getBundle("currentSensor")
                                if(currentSensor != null && currentSensor.containsKey("sensorStartTime")) {
                                    glucoExtras.putLong(ReceiveData.SENSOR_START_TIME, currentSensor.getLong("sensorStartTime"))
                                }
                            }
                        }
                        ReceiveData.handleIntent(context, DataSource.LIBRE_PATCHED, glucoExtras)
                        SourceStateData.setState(DataSource.LIBRE_PATCHED, SourceState.NONE)
                    } else {
                        Log.w(LOG_ID, "No data: " + Utils.dumpBundle(intent.extras))
                        SourceStateData.setError(DataSource.LIBRE_PATCHED, "Missing data!")
                    }
                } else if(intent.action == Constants.XDRIP_ACTION_SENSOR_ACTIVATE) {
                    if(extras.containsKey("bleManager") && extras.containsKey("sensor")) {
                        val bleManager = extras.getBundle("bleManager")
                        if(bleManager != null && bleManager.containsKey("sensorSerial")) {
                            val serial = bleManager.getString("sensorSerial")
                            val sensor = extras.getBundle("sensor")
                            if(sensor != null && sensor.containsKey("sensorStartTime")) {
                                val startTime = sensor.getLong("sensorStartTime")
                                ReceiveData.setSensorStartTime(serial, startTime)
                            }
                        }
                    }
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Receive exception: " + exc.message.toString() )
            SourceStateData.setError(DataSource.LIBRE_PATCHED, exc.message.toString() )
        }
    }
}