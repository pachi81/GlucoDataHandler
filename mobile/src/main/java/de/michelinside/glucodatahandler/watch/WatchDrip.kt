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
import de.michelinside.glucodatahandler.common.notification.AlarmType
import de.michelinside.glucodatahandler.common.BuildConfig
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.notification.AlarmNotificationBase
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.receiver.BroadcastServiceAPI
import de.michelinside.glucodatahandler.common.utils.GlucoDataUtils
import de.michelinside.glucodatahandler.common.utils.Utils
import de.michelinside.glucodatahandler.notification.AlarmNotification

object WatchDrip: SharedPreferences.OnSharedPreferenceChangeListener, NotifierInterface {
    private val LOG_ID = "GDH.WatchDrip"
    private var init = false
    private var active = false
    val receivers = mutableSetOf<String>()
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

    private fun handleNewReceiver(pkg: String): Boolean {
        if(pkg != "" && !receivers.contains(pkg)) {
            Log.i(LOG_ID, "Adding new receiver " + pkg)
            receivers.add(pkg)
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
            val newReceiver = handleNewReceiver(pkg)
            if(newReceiver) {
                sendBroadcast(context, BroadcastServiceAPI.CMD_UPDATE_BG_FORCE, pkg)
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
                else -> {
                    Log.d(LOG_ID, "Unknown command received: " + cmd + " received from " + pkg)
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "handleIntent exception: " + exc.toString() + "\n" + exc.stackTraceToString() )
        }
    }

    private fun createBundle(context: Context, cmd: String, alarmType: AlarmType): Bundle {
        return when(cmd) {
            BroadcastServiceAPI.CMD_ALARM -> createAlarmBundle(context, alarmType)
            BroadcastServiceAPI.CMD_UPDATE_BG,
            BroadcastServiceAPI.CMD_UPDATE_BG_FORCE -> createBgBundle(cmd)
            else -> createCmdBundle(cmd)
        }
    }

    private fun createCmdBundle(cmd: String): Bundle {
        val bundle = Bundle()
        bundle.putString(BroadcastServiceAPI.EXTRA_FUNCTION, cmd)
        return bundle
    }

    private fun createBgBundle(cmd: String): Bundle {
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
                val bundle = createBundle(context, cmd, alarmType)
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
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString() + "\n" + exc.stackTraceToString() )
        }
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