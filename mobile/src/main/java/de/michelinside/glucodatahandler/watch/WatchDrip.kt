package de.michelinside.glucodatahandler.watch

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import de.michelinside.glucodatahandler.common.BuildConfig
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.GlucoDataUtils
import de.michelinside.glucodatahandler.common.utils.Utils

object WatchDrip: SharedPreferences.OnSharedPreferenceChangeListener, NotifierInterface {
    private val LOG_ID = "GDH.WatchDrip"
    private var init = false
    private var active = false
    const val BROADCAST_SENDER_ACTION =  "com.eveningoutpost.dexdrip.watch.wearintegration.BROADCAST_SERVICE_SENDER"
    const val BROADCAST_RECEIVE_ACTION = "com.eveningoutpost.dexdrip.watch.wearintegration.BROADCAST_SERVICE_RECEIVER"
    const val EXTRA_FUNCTION = "FUNCTION"
    const val EXTRA_PACKAGE = "PACKAGE"
    const val EXTRA_TYPE = "type"
    const val EXTRA_MESSAGE = "message"
    const val CMD_UPDATE_BG_FORCE = "update_bg_force"
    const val CMD_UPDATE_BG = "update_bg"
    const val CMD_ALARM = "alarm"
    const val TYPE_ALERT = "BG_ALERT_TYPE"
    const val TYPE_OTHER_ALERT = "BG_OTHER_ALERT_TYPE"
    const val TYPE_NO_ALERT = "BG_NO_ALERT_TYPE"
    val receivers = mutableSetOf<String>()
    class WatchDripReceiver: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.v(LOG_ID, "onReceive called")
            handleIntent(context, intent)
        }

    }

    private val watchDripReceiver = WatchDripReceiver()

    fun init(context: Context) {
        try {
            if (!init) {
                Log.v(LOG_ID, "init called")
                val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
                sharedPref.registerOnSharedPreferenceChangeListener(this)
                updateSettings(sharedPref)
                init = true
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "init exception: " + exc.toString() + "\n" + exc.stackTraceToString() )
        }
    }

    fun close(context: Context) {
        try {
            if (init) {
                Log.v(LOG_ID, "close called")
                val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
                sharedPref.unregisterOnSharedPreferenceChangeListener(this)
                init = false
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "close exception: " + exc.toString() + "\n" + exc.stackTraceToString() )
        }
    }

    private fun handleNewReceiver(pkg: String) {
        if(!receivers.contains(pkg)) {
            Log.i(LOG_ID, "Adding new receiver " + pkg)
            receivers.add(pkg)
            saveReceivers()
        }
    }

    private fun handleIntent(context: Context, intent: Intent) {
        try {
            Log.i(LOG_ID, "handleIntent called for " + intent.action + ":\n" + Utils.dumpBundle(intent.extras))
            if (intent.extras == null) {
                return
            }
            val extras = intent.extras!!
            if (!extras.containsKey(EXTRA_FUNCTION) || !extras.containsKey(EXTRA_PACKAGE)) {
                Log.w(LOG_ID, "Missing mandatory extras: " + Utils.dumpBundle(intent.extras))
                return
            }
            val cmd = extras.getString(EXTRA_FUNCTION, "")
            val pkg = extras.getString(EXTRA_PACKAGE, "")
            Log.d(LOG_ID, "Command " + cmd + " received for package " + pkg)
            if (CMD_UPDATE_BG_FORCE.equals(cmd) && pkg != "") {
                handleNewReceiver(pkg)
                sendBroadcast(context, CMD_UPDATE_BG_FORCE, pkg)
            } else {
                Log.d(LOG_ID, "Unknown command received: " + cmd + " received from " + pkg)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "handleIntent exception: " + exc.toString() + "\n" + exc.stackTraceToString() )
        }
    }

    private fun createBundle(cmd: String): Bundle {
        return when(cmd) {
            CMD_ALARM -> createAlarmBundle()
            else -> createBgBundle(cmd)
        }
    }

    private fun createBgBundle(cmd: String): Bundle {
        val bundle = Bundle()
        bundle.putString(EXTRA_FUNCTION, cmd)
        bundle.putDouble("bg.valueMgdl", ReceiveData.rawValue.toDouble())
        bundle.putDouble("bg.deltaValueMgdl", ReceiveData.deltaValueMgDl.toDouble())
        bundle.putString("bg.deltaName", GlucoDataUtils.getDexcomLabel(ReceiveData.rate))
        bundle.putLong("bg.timeStamp", ReceiveData.time)
        bundle.putBoolean("bg.isStale", ReceiveData.isObsolete(Constants.VALUE_OBSOLETE_SHORT_SEC))
        bundle.putBoolean("doMgdl", !ReceiveData.isMmol)
        bundle.putBoolean("bg.isHigh", ReceiveData.getAlarmType() == ReceiveData.AlarmType.VERY_HIGH)
        bundle.putBoolean("bg.isLow", ReceiveData.getAlarmType() == ReceiveData.AlarmType.VERY_LOW)
        bundle.putString("pumpJSON", "{}")
        if (!ReceiveData.isIobCobObsolete(Constants.VALUE_OBSOLETE_SHORT_SEC) && !ReceiveData.iob.isNaN()) {
            bundle.putString("predict.IOB", ReceiveData.iobString)
            bundle.putLong("predict.IOB.timeStamp", ReceiveData.iobCobTime)
        }
        return bundle
    }

    private fun createAlarmBundle(): Bundle {
        val bundle = Bundle()
        bundle.putString(EXTRA_FUNCTION, CMD_ALARM)
        bundle.putString(EXTRA_TYPE, getAlertType())
        bundle.putString(EXTRA_MESSAGE, getAlarmMessage())
        return bundle
    }

    private fun getAlertType(): String {
        return when(ReceiveData.getAlarmType()) {
            ReceiveData.AlarmType.VERY_LOW,
            ReceiveData.AlarmType.VERY_HIGH -> TYPE_ALERT
            ReceiveData.AlarmType.LOW,
            ReceiveData.AlarmType.HIGH -> TYPE_OTHER_ALERT
            else -> TYPE_NO_ALERT
        }
    }

    private fun getAlarmMessage(): String {
        return when(ReceiveData.getAlarmType()) {
            ReceiveData.AlarmType.VERY_LOW -> "VERY LOW " + ReceiveData.getClucoseAsString()
            ReceiveData.AlarmType.LOW -> "LOW " + ReceiveData.getClucoseAsString()
            ReceiveData.AlarmType.HIGH -> "HIGH " + ReceiveData.getClucoseAsString()
            ReceiveData.AlarmType.VERY_HIGH -> "VERY HIGH " + ReceiveData.getClucoseAsString()
            else -> "No alarm!"
        }
    }
    private fun sendBroadcastToReceiver(context: Context, receiver: String, bundle: Bundle) {
        Log.d(LOG_ID, "Sending broadcast to " + receiver + ":\n" + Utils.dumpBundle(bundle))
        val intent = Intent(BROADCAST_SENDER_ACTION)
        intent.putExtras(bundle)
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        intent.setPackage(receiver)
        context.sendBroadcast(intent)
    }

    private fun sendBroadcast(context: Context, cmd: String, receiver: String? = null) {
        try {
            if (receiver != null || receivers.size > 0) {
                val bundle = createBundle(cmd)
                if (receiver != null) {
                    sendBroadcastToReceiver(context, receiver, bundle)
                } else {
                    receivers.forEach {
                        sendBroadcastToReceiver(context, it, bundle)
                    }
                }
            } else {
                Log.i(LOG_ID, "No receiver found for sending broadcast")
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "sendBroadcast exception: " + exc.toString() + "\n" + exc.stackTraceToString() )
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun activate() {
        try {
            if (GlucoDataService.context != null) {
                Log.v(LOG_ID, "activate called")
                loadReceivers()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    GlucoDataService.context!!.registerReceiver(watchDripReceiver, IntentFilter(BROADCAST_RECEIVE_ACTION),
                        Context.RECEIVER_EXPORTED or Context.RECEIVER_VISIBLE_TO_INSTANT_APPS)
                } else {
                    GlucoDataService.context!!.registerReceiver(watchDripReceiver, IntentFilter(BROADCAST_RECEIVE_ACTION))
                }
                InternalNotifier.addNotifier(GlucoDataService.context!!, this, mutableSetOf(
                    NotifySource.BROADCAST,
                    NotifySource.MESSAGECLIENT,
                    NotifySource.IOB_COB_CHANGE,
                    NotifySource.OBSOLETE_VALUE))
                active = true
                if (receivers.size > 0) {
                    sendBroadcast(GlucoDataService.context!!, CMD_UPDATE_BG)
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "activate exception: " + exc.toString() + "\n" + exc.stackTraceToString() )
        }
    }

    private fun deactivate() {
        try {
            if (GlucoDataService.context != null && active) {
                Log.v(LOG_ID, "deactivate called")
                InternalNotifier.remNotifier(GlucoDataService.context!!, this)
                GlucoDataService.context!!.unregisterReceiver(watchDripReceiver)
                active = false
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "deactivate exception: " + exc.toString() + "\n" + exc.stackTraceToString() )
        }
    }

    private fun loadReceivers() {
        try {
            val sharedExtraPref = GlucoDataService.context!!.getSharedPreferences(Constants.SHARED_PREF_EXTRAS_TAG, Context.MODE_PRIVATE)
            if (sharedExtraPref.contains(Constants.SHARED_PREF_WATCHDRIP_RECEIVERS)) {
                Log.i(LOG_ID, "Reading saved values...")
                val savedReceivers = sharedExtraPref.getStringSet(Constants.SHARED_PREF_WATCHDRIP_RECEIVERS, HashSet<String>())
                if (!savedReceivers.isNullOrEmpty()) {
                    Log.i(LOG_ID, "Loading receivers: " + savedReceivers)
                    receivers.addAll(savedReceivers)
                }
            }
            if(BuildConfig.DEBUG && receivers.isEmpty()) {
                receivers.add("dummy")
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Loading receivers exception: " + exc.toString() + "\n" + exc.stackTraceToString() )
        }
    }

    private fun saveReceivers() {
        try {
            Log.d(LOG_ID, "Saving receivers")
            // use own tag to prevent trigger onChange event at every time!
            val sharedExtraPref = GlucoDataService.context!!.getSharedPreferences(Constants.SHARED_PREF_EXTRAS_TAG, Context.MODE_PRIVATE)
            with(sharedExtraPref.edit()) {
                putStringSet(Constants.SHARED_PREF_WATCHDRIP_RECEIVERS, receivers)
                apply()
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Saving receivers exception: " + exc.toString() + "\n" + exc.stackTraceToString() )
        }
    }

    private fun updateSettings(sharedPreferences: SharedPreferences) {
        try {
            Log.v(LOG_ID, "updateSettings called")
            if (sharedPreferences.getBoolean(Constants.SHARED_PREF_WATCHDRIP, false))
                activate()
            else
                deactivate()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateSettings exception: " + exc.toString() + "\n" + exc.stackTraceToString() )
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        try {
            Log.v(LOG_ID, "onSharedPreferenceChanged called for key " + key)
            when(key) {
                Constants.SHARED_PREF_WATCHDRIP -> {
                    updateSettings(sharedPreferences!!)
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString() + "\n" + exc.stackTraceToString() )
        }
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            Log.v(LOG_ID, "OnNotifyData called for source " + dataSource)
            sendBroadcast(context, CMD_UPDATE_BG)
            if (ReceiveData.forceAlarm && dataSource != NotifySource.IOB_COB_CHANGE && ReceiveData.alarm > 0)
                sendBroadcast(context, CMD_ALARM)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: " + exc.toString() + "\n" + exc.stackTraceToString() )
        }
    }
}