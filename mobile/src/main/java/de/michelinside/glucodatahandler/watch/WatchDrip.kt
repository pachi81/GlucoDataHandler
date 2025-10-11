package de.michelinside.glucodatahandler.watch

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import de.michelinside.glucodatahandler.common.utils.Log
import com.eveningoutpost.dexdrip.services.broadcastservice.models.GraphLine
import com.eveningoutpost.dexdrip.services.broadcastservice.models.Settings
import de.michelinside.glucodatahandler.common.BuildConfig
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.database.dbAccess
import de.michelinside.glucodatahandler.common.notification.AlarmNotificationBase
import de.michelinside.glucodatahandler.common.notification.AlarmType
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.receiver.BroadcastServiceAPI
import de.michelinside.glucodatahandler.common.utils.GlucoDataUtils
import de.michelinside.glucodatahandler.common.utils.JsonUtils
import de.michelinside.glucodatahandler.common.utils.Utils
import de.michelinside.glucodatahandler.notification.AlarmNotification
import java.math.RoundingMode


object WatchDrip: SharedPreferences.OnSharedPreferenceChangeListener, NotifierInterface {
    private val LOG_ID = "GDH.WatchDrip"
    private var init = false
    private var active = false
    private var sendInterval = 1
    private var lastSendValuesTime = 0L
    const val CMD_SET_SETTINGS = "set_settings"
    val receivers = mutableMapOf<String, Settings?>()
    class WatchDripReceiver: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.v(LOG_ID, "onReceive called")
            handleIntent(context, intent)
        }

    }

    private val watchDripReceiver = WatchDripReceiver()

    val connected: Boolean get() = (active && receivers.size > 0)

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

    private fun getSettings(extras: Bundle): Settings? {
        try {
            if(extras.containsKey(BroadcastServiceAPI.EXTRA_SETTINGS)) {
                val settings = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    extras.getParcelable(BroadcastServiceAPI.EXTRA_SETTINGS, Settings::class.java)
                } else {
                    extras.getParcelable(BroadcastServiceAPI.EXTRA_SETTINGS)
                }
                if(settings != null) {
                    Log.i(LOG_ID, "Settings received: has graph = ${settings.isDisplayGraph} - start offset = ${settings.graphStart} - end offset = ${settings.graphEnd}")
                    return settings
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "getSettings exception: " + exc.toString())
        }
        return null
    }

    private fun handleNewReceiver(pkg: String, extras: Bundle): Boolean {
        if(pkg != "" && !receivers.contains(pkg)) {
            Log.i(LOG_ID, "Adding new receiver " + pkg)
            receivers[pkg] = getSettings(extras)
            saveReceivers()
            return true
        }
        return false
    }

    private fun handleIntent(context: Context, intent: Intent) {
        try {
            Log.i(LOG_ID, "handleIntent called for " + intent.action + ":\n" + Utils.dumpBundle(intent.extras))
            if (intent.extras == null) {
                return
            }
            val extras = intent.extras!!
            if (!extras.containsKey(BroadcastServiceAPI.EXTRA_FUNCTION) || !extras.containsKey(BroadcastServiceAPI.EXTRA_PACKAGE)) {
                Log.w(LOG_ID, "Missing mandatory extras: " + Utils.dumpBundle(intent.extras))
                return
            }
            val pkg = extras.getString(BroadcastServiceAPI.EXTRA_PACKAGE, "")
            if(pkg == context.packageName)
                return // ignore messages from itself
            val cmd = extras.getString(BroadcastServiceAPI.EXTRA_FUNCTION, "")
            Log.d(LOG_ID, "Command " + cmd + " received for package " + pkg)
            val newReceiver = handleNewReceiver(pkg, extras)
            if(newReceiver) {
                sendBroadcast(context, BroadcastServiceAPI.CMD_UPDATE_BG_FORCE, pkg)
            } else {
                val settings = getSettings(extras)
                if(settings != null) {
                    Log.i(LOG_ID, "Settings changed for pkg $pkg: has graph = ${settings.isDisplayGraph} - start offset = ${settings.graphStart} - end offset = ${settings.graphEnd}")
                    receivers[pkg] = settings
                    saveReceivers()
                }
            }
            when(cmd) {
                BroadcastServiceAPI.CMD_UPDATE_BG_FORCE -> {
                    if(!newReceiver)
                        sendBroadcast(context, BroadcastServiceAPI.CMD_UPDATE_BG_FORCE, pkg)
                }
                BroadcastServiceAPI.CMD_CANCEL_ALARM,
                BroadcastServiceAPI.CMD_SNOOZE_ALARM -> {
                    AlarmNotification.stopCurrentNotification(context)
                }
                CMD_SET_SETTINGS -> {
                    // ignore as settings already set
                }
                else -> {
                    Log.d(LOG_ID, "Unknown command received: " + cmd + " received from " + pkg)
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "handleIntent exception: " + exc.toString() + "\n" + exc.stackTraceToString() )
        }
    }

    private fun createBundle(context: Context, cmd: String, alarmType: AlarmType, receiver: String?): Bundle {
        return when(cmd) {
            BroadcastServiceAPI.CMD_ALARM -> createAlarmBundle(context, alarmType)
            BroadcastServiceAPI.CMD_UPDATE_BG,
            BroadcastServiceAPI.CMD_UPDATE_BG_FORCE -> createBgBundle(cmd, receiver)
            else -> createCmdBundle(cmd)
        }
    }

    private fun createCmdBundle(cmd: String): Bundle {
        val bundle = Bundle()
        bundle.putString(BroadcastServiceAPI.EXTRA_FUNCTION, cmd)
        return bundle
    }

    private fun createBgBundle(cmd: String, receiver: String?): Bundle {
        val bundle = createCmdBundle(cmd)
        bundle.putDouble(BroadcastServiceAPI.BG_VALUE_MGDL, ReceiveData.rawValue.toDouble())
        bundle.putDouble(BroadcastServiceAPI.BG_DELTA_VALUE_MGDL, ReceiveData.deltaValueMgDl.toDouble())
        bundle.putString(BroadcastServiceAPI.BG_DELTA_NAME, GlucoDataUtils.getDexcomLabel(ReceiveData.rate))
        bundle.putLong(BroadcastServiceAPI.BG_TIMESTAMP, ReceiveData.time)
        bundle.putBoolean(BroadcastServiceAPI.BG_IS_STALE, ReceiveData.isObsoleteShort())
        bundle.putBoolean(BroadcastServiceAPI.BG_DO_MGDL, !ReceiveData.isMmol)
        bundle.putBoolean(BroadcastServiceAPI.BG_IS_HIGH, ReceiveData.getAlarmType() == AlarmType.VERY_HIGH)
        bundle.putBoolean(BroadcastServiceAPI.BG_IS_LOW, ReceiveData.getAlarmType() == AlarmType.VERY_LOW)
        bundle.putString(BroadcastServiceAPI.PUMP_JSON, "{}")
        if (!ReceiveData.isIobCobObsolete() && !ReceiveData.iob.isNaN()) {
            bundle.putString(BroadcastServiceAPI.PREDICT_IOB, ReceiveData.iobString)
            bundle.putLong(BroadcastServiceAPI.PREDICT_IOB_TIME, ReceiveData.iobCobTime)
        }
        return bundle
    }

    private fun formatedValue(rawValue: Int): Float {
        if(ReceiveData.isMmol)
            return GlucoDataUtils.mgToMmol(rawValue.toFloat())
        return rawValue.toFloat()
    }

    const val FUZZER: Int = 60000
    private fun addGraph(bundle: Bundle, receiver: String?): Bundle {
        try {
            if(receiver != null && receivers.contains(receiver)) {
                val cmd = bundle.getString(BroadcastServiceAPI.EXTRA_FUNCTION)
                if(cmd != BroadcastServiceAPI.CMD_UPDATE_BG && cmd != BroadcastServiceAPI.CMD_UPDATE_BG_FORCE)
                    return bundle
                // add graph to bg bundle
                val settings = receivers[receiver]
                if (settings == null || settings.isDisplayGraph) {
                    var graphStartOffset = settings?.graphStart?: 0L
                    if (graphStartOffset == 0L) {
                        graphStartOffset = 2*60*60*1000 // 2 hours
                    }
                    val start = System.currentTimeMillis() - graphStartOffset
                    val end = System.currentTimeMillis()

                    Log.d(LOG_ID, "Add graph data from ${Utils.getUiTimeStamp(start)} ($start) with offset = $graphStartOffset")

                    bundle.putInt("fuzzer", FUZZER)
                    bundle.putLong("start", start)
                    bundle.putLong("end", end)
                    bundle.putDouble("highMark", ReceiveData.targetMax.toDouble())  // mg/dl ????
                    bundle.putDouble("lowMark", ReceiveData.targetMin.toDouble())

                    val startFuz = (start / FUZZER).toFloat()
                    val endFuz = (end / FUZZER).toFloat()

                    val lowLine= GraphLine(Color.LTGRAY)
                    lowLine.add(startFuz,ReceiveData.targetMin)
                    lowLine.add(endFuz,ReceiveData.targetMin)
                    bundle.putParcelable("graph.lowLine", lowLine)

                    val highLine= GraphLine(Color.LTGRAY)
                    highLine.add(startFuz,ReceiveData.targetMax)
                    highLine.add(endFuz,ReceiveData.targetMax)
                    bundle.putParcelable("graph.highLine", highLine)

                    val inRangeValues = GraphLine(ReceiveData.getAlarmTypeColor(AlarmType.OK))
                    val lowValues = GraphLine(ReceiveData.getAlarmTypeColor(AlarmType.VERY_LOW))
                    val highValues = GraphLine(ReceiveData.getAlarmTypeColor(AlarmType.HIGH))

                    val values = dbAccess.getGlucoseValues(start)
                    Log.d(LOG_ID, "Found ${values.size} values")
                    values.forEach {
                        if(it.value > 400)
                            highValues.add(it.timestamp.toFloat()/FUZZER, formatedValue(400))
                        if(it.value > ReceiveData.targetMaxRaw)
                            highValues.add(it.timestamp.toFloat()/FUZZER, formatedValue(it.value))
                        else if(it.value < 40)
                            lowValues.add(it.timestamp.toFloat()/FUZZER, formatedValue(40))
                        else if(it.value < ReceiveData.targetMinRaw)
                            lowValues.add(it.timestamp.toFloat()/FUZZER, formatedValue(it.value))
                        else
                            inRangeValues.add(it.timestamp.toFloat()/FUZZER, formatedValue(it.value))
                    }
                    bundle.putParcelable("graph.inRange", inRangeValues)
                    bundle.putParcelable("graph.low", lowValues)
                    bundle.putParcelable("graph.high", highValues)
                    Log.d(LOG_ID, "Add graph data done")
                }
            } else {
                Log.e(LOG_ID, "Receiver $receiver not registered!")
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "addGraph exception: " + exc.toString() + "" + exc.stackTraceToString() )
        }
        return bundle
    }

    private fun createAlarmBundle(context: Context, alarmType: AlarmType): Bundle {
        val bundle = createCmdBundle(BroadcastServiceAPI.CMD_ALARM)
        bundle.putString(BroadcastServiceAPI.EXTRA_TYPE, getAlertType(alarmType))
        bundle.putString(BroadcastServiceAPI.EXTRA_MESSAGE, getAlarmMessage(context, alarmType))
        return bundle
    }

    private fun getAlertType(alarmType: AlarmType): String {
        return when(alarmType) {
            AlarmType.VERY_LOW,
            AlarmType.VERY_HIGH -> BroadcastServiceAPI.TYPE_ALERT
            AlarmType.LOW,
            AlarmType.HIGH,
            AlarmType.OBSOLETE -> BroadcastServiceAPI.TYPE_OTHER_ALERT
            else -> BroadcastServiceAPI.TYPE_NO_ALERT
        }
    }

    private fun getAlarmMessage(context: Context, alarmType: AlarmType): String {
        val resId = AlarmNotificationBase.getAlarmTextRes(alarmType) ?: return "No alarm!"
        val msg = context.resources.getString(resId)
        return when(alarmType) {
            AlarmType.VERY_LOW,
            AlarmType.LOW,
            AlarmType.HIGH,
            AlarmType.VERY_HIGH -> msg + " " + ReceiveData.getGlucoseAsString()
            AlarmType.OBSOLETE -> msg + " " + ReceiveData.getElapsedRelativeTimeAsString(context)
            else -> "No alarm!"
        }
    }
    private fun sendBroadcastToReceiver(context: Context, receiver: String?, bundle: Bundle) {
        Log.d(LOG_ID, "Sending broadcast to " + receiver + ":\n" + Utils.dumpBundle(bundle))
        val intent = Intent(BroadcastServiceAPI.BROADCAST_SENDER_ACTION)
        if(receiver != null)
            intent.setPackage(receiver)
        intent.putExtras(bundle)
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        context.sendBroadcast(intent)
    }

    private fun sendBroadcast(context: Context, cmd: String, receiver: String? = null, alarmType: AlarmType = AlarmType.NONE) {
        try {
            if (receiver != null || receivers.size > 0) {
                val bundle = createBundle(context, cmd, alarmType, receiver)
                if (receiver != null) {
                    Log.i(LOG_ID, "Sending broadcast to $receiver")
                    sendBroadcastToReceiver(context, receiver, addGraph(bundle, receiver))
                } else {
                    Log.i(LOG_ID, "Sending broadcast to ${receivers.size} receivers")
                    receivers.forEach {
                        sendBroadcastToReceiver(context, it.key, addGraph(bundle, it.key))
                    }
                }
                lastSendValuesTime = ReceiveData.time
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
                    GlucoDataService.context!!.registerReceiver(watchDripReceiver, IntentFilter(BroadcastServiceAPI.BROADCAST_RECEIVE_ACTION),
                        Context.RECEIVER_EXPORTED or Context.RECEIVER_VISIBLE_TO_INSTANT_APPS)
                } else {
                    GlucoDataService.context!!.registerReceiver(watchDripReceiver, IntentFilter(BroadcastServiceAPI.BROADCAST_RECEIVE_ACTION))
                }
                InternalNotifier.addNotifier(GlucoDataService.context!!, this, mutableSetOf(
                    NotifySource.BROADCAST,
                    NotifySource.MESSAGECLIENT,
                    NotifySource.IOB_COB_CHANGE,
                    NotifySource.OBSOLETE_VALUE,
                    NotifySource.ALARM_TRIGGER,
                    NotifySource.OBSOLETE_ALARM_TRIGGER,
                    NotifySource.DELTA_ALARM_TRIGGER,
                    NotifySource.NOTIFICATION_STOPPED))
                active = true
                sendBroadcastToReceiver(GlucoDataService.context!!, null, createCmdBundle(BroadcastServiceAPI.CMD_START))
                if (receivers.size > 0) {
                    sendBroadcast(GlucoDataService.context!!, BroadcastServiceAPI.CMD_UPDATE_BG)
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "activate exception: " + exc.toString() + "\n" + exc.stackTraceToString() )
        }
    }

    private fun deactivate(removeReceivers: Boolean) {
        try {
            if (GlucoDataService.context != null && active) {
                Log.v(LOG_ID, "deactivate called removeReceivers=$removeReceivers")
                InternalNotifier.remNotifier(GlucoDataService.context!!, this)
                GlucoDataService.context!!.unregisterReceiver(watchDripReceiver)
                active = false
                if(removeReceivers) {
                    receivers.clear()
                    saveReceivers()
                }
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
                    savedReceivers.forEach {
                        if(sharedExtraPref.contains(it)) {
                            val settings: Settings? = JsonUtils.getParcelableFromJson(sharedExtraPref.getString(it, ""))
                            receivers[it] = settings
                            if(settings != null)
                                Log.i(LOG_ID, "Loaded receiver $it: has graph = ${settings.isDisplayGraph} - start offset = ${settings.graphStart} - end offset = ${settings.graphEnd}")
                        } else
                            receivers[it] = null
                    }
                }
            }
            if(BuildConfig.DEBUG && receivers.isEmpty()) {
                receivers["dummy"] = null
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
                putStringSet(Constants.SHARED_PREF_WATCHDRIP_RECEIVERS, receivers.keys)
                receivers.forEach {
                    if(it.value != null)
                        putString(it.key, JsonUtils.getParcelableAsJson(it.value!!))
                }
                apply()
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Saving receivers exception: " + exc.toString() + "\n" + exc.stackTraceToString() )
        }
    }

    private fun updateSettings(sharedPreferences: SharedPreferences, removeReceiversOnDisable: Boolean = false) {
        try {
            Log.v(LOG_ID, "updateSettings called")
            if (sharedPreferences.getBoolean(Constants.SHARED_PREF_WATCHDRIP, false))
                activate()
            else
                deactivate(removeReceiversOnDisable)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateSettings exception: " + exc.toString() + "\n" + exc.stackTraceToString() )
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        try {
            Log.v(LOG_ID, "onSharedPreferenceChanged called for key " + key)
            when(key) {
                Constants.SHARED_PREF_WATCHDRIP -> {
                    updateSettings(sharedPreferences!!, true)
                }
                Constants.SHARED_PREF_SEND_TO_WATCH_INTERVAL -> {
                    sendInterval = sharedPreferences!!.getInt(Constants.SHARED_PREF_SEND_TO_WATCH_INTERVAL, 1)
                    Log.i(LOG_ID, "Send interval changed to $sendInterval")
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString() + "\n" + exc.stackTraceToString() )
        }
    }

    private fun canSendBroadcast(dataSource: NotifySource): Boolean {
        if(sendInterval > 1 && !ReceiveData.forceAlarm && ReceiveData.getAlarmType() != AlarmType.VERY_LOW && (dataSource == NotifySource.BROADCAST || dataSource == NotifySource.MESSAGECLIENT)) {
            val elapsed = Utils.getElapsedTimeMinute(lastSendValuesTime, RoundingMode.HALF_UP)
            if ( elapsed < sendInterval) {
                Log.i(LOG_ID, "Ignore data because of interval $sendInterval - elapsed: $elapsed")
                return false
            }
        }
        return true
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            Log.v(LOG_ID, "OnNotifyData called for source " + dataSource)
            when(dataSource) {
                NotifySource.NOTIFICATION_STOPPED -> {
                    sendBroadcast(context, BroadcastServiceAPI.CMD_CANCEL_ALARM)
                }
                NotifySource.ALARM_TRIGGER,
                NotifySource.OBSOLETE_ALARM_TRIGGER -> {
                    sendBroadcast(context, BroadcastServiceAPI.CMD_ALARM, alarmType = ReceiveData.getAlarmType())
                }
                NotifySource.DELTA_ALARM_TRIGGER -> {
                    if(extras?.containsKey(Constants.ALARM_TYPE_EXTRA) == true) {
                        val alarmType = AlarmType.fromIndex(extras.getInt(Constants.ALARM_TYPE_EXTRA, AlarmType.NONE.ordinal))
                        if(alarmType != AlarmType.NONE)
                            sendBroadcast(context, BroadcastServiceAPI.CMD_ALARM, alarmType = alarmType)
                    }
                }
                else -> {
                    if(canSendBroadcast(dataSource))
                        sendBroadcast(context, BroadcastServiceAPI.CMD_UPDATE_BG)
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: " + exc.toString() + "\n" + exc.stackTraceToString() )
        }
    }

    fun sendTestAlert(context: Context, alarmType: AlarmType) {
        sendBroadcast(context, BroadcastServiceAPI.CMD_ALARM, alarmType = alarmType)
    }
}