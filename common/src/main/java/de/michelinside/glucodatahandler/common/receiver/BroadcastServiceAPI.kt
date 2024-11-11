package de.michelinside.glucodatahandler.common.receiver

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.RECEIVER_EXPORTED
import android.content.Context.RECEIVER_VISIBLE_TO_INSTANT_APPS
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.eveningoutpost.dexdrip.services.broadcastservice.models.Settings
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.SourceState
import de.michelinside.glucodatahandler.common.SourceStateData
import de.michelinside.glucodatahandler.common.notifier.DataSource
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.GlucoDataUtils
import de.michelinside.glucodatahandler.common.utils.Utils


open class BroadcastServiceAPI: BroadcastReceiver(), NotifierInterface,
    SharedPreferences.OnSharedPreferenceChangeListener {
    private val LOG_ID = "GDH.BroadcastServiceAPI"
    companion object {
        private var enabled = false
        private var init = false
        private var lastData = ""

        const val BROADCAST_SENDER_ACTION = "com.eveningoutpost.dexdrip.watch.wearintegration.BROADCAST_SERVICE_SENDER"
        const val BROADCAST_RECEIVE_ACTION = "com.eveningoutpost.dexdrip.watch.wearintegration.BROADCAST_SERVICE_RECEIVER"
        const val EXTRA_FUNCTION = "FUNCTION"
        const val EXTRA_PACKAGE = "PACKAGE"
        const val EXTRA_TYPE = "type"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_SETTINGS = "SETTINGS"
        const val CMD_UPDATE_BG_FORCE = "update_bg_force"
        const val CMD_UPDATE_BG = "update_bg"
        const val CMD_ALARM = "alarm"
        const val CMD_START = "start"
        const val CMD_CANCEL_ALARM = "cancel_alarm"
        const val CMD_SNOOZE_ALARM = "snooze_alarm"
        const val TYPE_ALERT = "BG_ALERT_TYPE"
        const val TYPE_OTHER_ALERT = "BG_OTHER_ALERT_TYPE"
        const val TYPE_NO_ALERT = "BG_NO_ALERT_TYPE"
        // bg bundle
        const val BG_VALUE_MGDL = "bg.valueMgdl"
        const val BG_DELTA_VALUE_MGDL = "bg.deltaValueMgdl"
        const val BG_DELTA_NAME = "bg.deltaName"
        const val BG_TIMESTAMP = "bg.timeStamp"
        const val BG_IS_STALE = "bg.isStale"
        const val BG_IS_HIGH = "bg.isHigh"
        const val BG_IS_LOW = "bg.isLow"
        const val BG_DO_MGDL = "doMgdl"
        const val PUMP_JSON = "pumpJSON"
        const val PREDICT_IOB = "predict.IOB"
        const val PREDICT_IOB_TIME = "predict.IOB.timeStamp"
        const val EXTERNAL_STATUSLINE = "external.statusLine"
        const val EXTERNAL_TIMESTAMP = "external.timeStamp"
    }

    fun init() {
        if(!init) {
            Log.d(LOG_ID, "init")
            init = true
            GlucoDataService.sharedPref!!.registerOnSharedPreferenceChangeListener(this)
            onSharedPreferenceChanged(GlucoDataService.sharedPref!!, Constants.SHARED_PREF_XDRIP_BROADCAST_SERVICE_API)
        }
    }

    fun close(context: Context) {
        if(init) {
            Log.d(LOG_ID, "close")
            init = false
            disable(context)
            GlucoDataService.sharedPref!!.unregisterOnSharedPreferenceChangeListener(this)
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun enable(context: Context) {
        if(!enabled) {
            Log.i(LOG_ID, "enable")
            enabled = true
            InternalNotifier.addNotifier(context, this, mutableSetOf(NotifySource.OBSOLETE_VALUE))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(this, IntentFilter("com.eveningoutpost.dexdrip.watch.wearintegration.BROADCAST_SERVICE_SENDER"), RECEIVER_EXPORTED or RECEIVER_VISIBLE_TO_INSTANT_APPS)
            } else {
                context.registerReceiver(this, IntentFilter("com.eveningoutpost.dexdrip.watch.wearintegration.BROADCAST_SERVICE_SENDER"))
            }
            sendForceDataRequest(context)
        }
    }

    private fun disable(context: Context) {
        if(enabled) {
            Log.i(LOG_ID, "disable")
            enabled = false
            InternalNotifier.remNotifier(context, this)
            context.unregisterReceiver(this)
        }
    }

    private fun sendForceDataRequest(context: Context) {
        if(enabled) {
            Log.d(LOG_ID, "sendForceDataRequest called")
            val intent = Intent(BROADCAST_RECEIVE_ACTION)
            intent.putExtra(EXTRA_SETTINGS, Settings(context.packageName))
            sendBroadcast(context, CMD_UPDATE_BG_FORCE, intent)
        }
    }

    @Suppress("SameParameterValue")
    private fun sendBroadcast(context: Context, cmd: String, intent: Intent) {
        intent.putExtra(EXTRA_FUNCTION, cmd)
        intent.putExtra(EXTRA_PACKAGE, context.packageName)
        Log.d(LOG_ID, "sendBroadcast for $cmd")
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        context.sendBroadcast(intent)
    }


    private fun handleBgBundle(context: Context, bundle: Bundle) {
        if(!bundle.containsKey(BG_VALUE_MGDL) ||
            !bundle.containsKey(BG_DELTA_NAME) ||
            !bundle.containsKey(BG_TIMESTAMP)) {
            Log.w(LOG_ID, "Missing data! ${Utils.dumpBundle(bundle)}")
            SourceStateData.setError(DataSource.XDRIP, "Missing values in service API data!")
            return
        }

        val mgdl = Utils.round(bundle.getDouble(BG_VALUE_MGDL, 0.0).toFloat(), 0)

        val glucoExtras = Bundle()
        glucoExtras.putLong(ReceiveData.TIME, bundle.getLong(BG_TIMESTAMP))
        glucoExtras.putInt(ReceiveData.MGDL, mgdl.toInt())
        if(bundle.containsKey(BG_DO_MGDL)) {
            if(bundle.getBoolean(BG_DO_MGDL)) {
                glucoExtras.putFloat(ReceiveData.GLUCOSECUSTOM, mgdl)
            } else {
                glucoExtras.putFloat(ReceiveData.GLUCOSECUSTOM, GlucoDataUtils.mgToMmol(mgdl))
            }
        }
        glucoExtras.putFloat(ReceiveData.RATE, GlucoDataUtils.getRateFromLabel(bundle.getString(BG_DELTA_NAME)))
        glucoExtras.putInt(ReceiveData.ALARM, 0)

        if(bundle.containsKey(EXTERNAL_STATUSLINE)) {
            val statusLine = bundle.getString(EXTERNAL_STATUSLINE)
            if(!statusLine.isNullOrEmpty() && statusLine.contains("IE")) {
                /*
                    Loop deaktiviert
                    2,07IE(2,07|0,00) 19g
                    or
                    Loop deaktiviert
                    2,07IE 19g
                    or
                    Loop deaktiviert
                    2,07IE --g
                 */
                try {
                    val pattern = "(\\d+[.|,]\\d+)IE(.* (\\d+)g)?"
                    val match = Regex(pattern).find(statusLine)
                    if (match != null) {
                        Log.d(LOG_ID, "Statusline $statusLine found values: ${match.groupValues}")
                        if(match.groupValues.size > 1) {
                            glucoExtras.putFloat(ReceiveData.IOB, Utils.parseFloatString(match.groupValues[1]))
                            if(match.groupValues.size == 4) {
                                glucoExtras.putFloat(ReceiveData.COB, Utils.getCobValue(Utils.parseFloatString(match.groupValues[3])))
                            }
                            if(bundle.containsKey(EXTERNAL_TIMESTAMP)) {
                                glucoExtras.putLong(ReceiveData.IOBCOB_TIME, bundle.getLong(EXTERNAL_TIMESTAMP))
                            }
                        }
                    }
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Error parsing statusline ${statusLine}: ${exc.message}")
                }
            }
        }

        if(!glucoExtras.containsKey(ReceiveData.IOB) && bundle.containsKey(PREDICT_IOB)) {
            val iobString = bundle.getString(PREDICT_IOB)
            if (!iobString.isNullOrEmpty()) {
                glucoExtras.putFloat(ReceiveData.IOB, Utils.parseFloatString(iobString))
                if(bundle.containsKey(PREDICT_IOB_TIME))
                    glucoExtras.putLong(ReceiveData.IOBCOB_TIME, bundle.getLong(PREDICT_IOB_TIME))
            }
        }

        if (!ReceiveData.isIobCobObsolete() && !ReceiveData.iob.isNaN()) {
            bundle.putString(PREDICT_IOB, ReceiveData.iobString)
            bundle.putLong(PREDICT_IOB_TIME, ReceiveData.iobCobTime)
        }

        ReceiveData.handleIntent(context, DataSource.XDRIP, glucoExtras)
        SourceStateData.setState(DataSource.XDRIP, SourceState.NONE)
    }


    override fun onReceive(context: Context, intent: Intent) {
        try {
            val data = Utils.dumpBundle(intent.extras)
            if (data == lastData) {
                Log.d(LOG_ID, "Ignoring double receiving data: $data")
                return
            }
            Log.i(LOG_ID, "onReceive called for action ${intent.action} (enaled=$enabled): $data")
            if(enabled) {
                lastData = data
                val function = intent.getStringExtra(EXTRA_FUNCTION)
                when(function) {
                    CMD_UPDATE_BG,
                    CMD_UPDATE_BG_FORCE -> {
                        if(intent.extras != null)
                            handleBgBundle(context, intent.extras!!)
                    }
                    CMD_START -> sendForceDataRequest(context)
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onReceive exception: " + exc.message.toString() )
        }
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            Log.d(LOG_ID, "OnNotifyData called for source $dataSource")
            if(dataSource == NotifySource.OBSOLETE_VALUE && ReceiveData.isObsoleteLong()) {
                sendForceDataRequest(context)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: " + exc.message.toString() )
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        try {
            Log.d(LOG_ID, "onSharedPreferenceChanged called for key $key")
            when(key) {
                Constants.SHARED_PREF_XDRIP_BROADCAST_SERVICE_API -> {
                    if(sharedPreferences!!.getBoolean(Constants.SHARED_PREF_XDRIP_BROADCAST_SERVICE_API, false)) {
                        enable(GlucoDataService.context!!)
                    } else {
                        disable(GlucoDataService.context!!)
                    }
                }
            }

        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.message.toString() )
        }
    }
}