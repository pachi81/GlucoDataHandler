package de.michelinside.glucodatahandler.common

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.*
import android.util.Log
import com.google.android.gms.wearable.*
import de.michelinside.glucodatahandler.common.notifier.*
import de.michelinside.glucodatahandler.common.receiver.*

enum class AppSource {
    NOT_SET,
    PHONE_APP,
    WEAR_APP;
}

open class GlucoDataService(source: AppSource) : WearableListenerService(), NotifierInterface {
    private val LOG_ID = "GlucoDataHandler.GlucoDataService"
    private lateinit var receiver: GlucoseDataReceiver
    private lateinit var batteryReceiver: BatteryReceiver
    private lateinit var xDripReceiver: XDripBroadcastReceiver
    private val connection = WearPhoneConnection()
    private var lastAlarmTime = 0L
    private var lastAlarmType = ReceiveData.AlarmType.OK

    companion object {
        var appSource = AppSource.NOT_SET
        private var isRunning = false
        val running get() = isRunning
        private var service: GlucoDataService? = null
        val context: Context? get() {
            if(service != null)
                return service!!.applicationContext
            return null
        }
        val sharedPref: SharedPreferences? get() {
            if (context != null) {
                return context!!.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            }
            return null
        }
    }

    init {
        appSource = source
    }

    override fun onCreate() {
        try {
            super.onCreate()
            Log.i(LOG_ID, "onCreate called")
            service = this
            isRunning = true

            ReceiveData.initData(this)

            connection.open(this)

            val filter = mutableSetOf(
                NotifyDataSource.BROADCAST,
                NotifyDataSource.MESSAGECLIENT,
                NotifyDataSource.CAPILITY_INFO,
                NotifyDataSource.BATTERY_LEVEL,
                NotifyDataSource.OBSOLETE_VALUE)   // to trigger re-start for the case of stopped by the system
            if (appSource == AppSource.PHONE_APP)
                filter.add(NotifyDataSource.SETTINGS)   // only send setting changes from phone to wear!
            InternalNotifier.addNotifier(this, filter)

            Log.d(LOG_ID, "Register Receiver")
            receiver = GlucoseDataReceiver()
            registerReceiver(receiver, IntentFilter("glucodata.Minute"))
            batteryReceiver = BatteryReceiver()
            registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            xDripReceiver = XDripBroadcastReceiver()
            registerReceiver(xDripReceiver,IntentFilter("com.eveningoutpost.dexdrip.BgEstimate"))

            if (BuildConfig.DEBUG && sharedPref!!.getBoolean(Constants.SHARED_PREF_NOTIFICATION, false)) {
                Thread {
                    try {
                        while (true) {
                            // create Thread which send dummy intents
                            this.sendBroadcast(Utils.getDummyGlucodataIntent(true))
                            Thread.sleep(40000)
                        }
                    } catch (exc: Exception) {
                        Log.e(LOG_ID, "Send dummy glucodata exception: " + exc.toString())
                    }
                }.start()
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreate exception: " + exc.toString())
        }
    }

    override fun onDestroy() {
        try {
            Log.w(LOG_ID, "onDestroy called")
            unregisterReceiver(receiver)
            unregisterReceiver(batteryReceiver)
            unregisterReceiver(xDripReceiver)
            connection.close()
            super.onDestroy()
            service = null
            isRunning = false
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onDestroy exception: " + exc.toString())
        }
    }

    fun getVibrationPattern(alarmType: ReceiveData.AlarmType): LongArray? {
        return when(alarmType) {
            ReceiveData.AlarmType.LOW_ALARM -> longArrayOf(0, 1000, 500, 1000, 500, 1000, 500, 1000, 500, 1000, 500, 1000)
            ReceiveData.AlarmType.LOW -> longArrayOf(0, 700, 500, 700, 5000, 700, 500, 700)
            ReceiveData.AlarmType.HIGH -> longArrayOf(0, 500, 500, 500, 500, 500, 500, 500)
            ReceiveData.AlarmType.HIGH_ALARM -> longArrayOf(0, 800, 500, 800, 800, 600, 800, 800, 500, 800, 800, 600, 800)
            else -> null
        }
    }

    fun vibrate(alarmType: ReceiveData.AlarmType): Boolean {
        val vibratePattern = getVibrationPattern(alarmType) ?: return false
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        Log.i(LOG_ID, "vibration for " + alarmType.toString())
        vibrator.vibrate(VibrationEffect.createWaveform(vibratePattern, -1))
        return true
    }

    override fun OnNotifyData(context: Context, dataSource: NotifyDataSource, extras: Bundle?) {
        try {
            Log.d(LOG_ID, "OnNotifyData for source " + dataSource.toString() + " and extras " + extras.toString())
            if (dataSource != NotifyDataSource.MESSAGECLIENT && dataSource != NotifyDataSource.NODE_BATTERY_LEVEL && (dataSource != NotifyDataSource.SETTINGS || extras != null)) {
                Thread {
                    try {
                        connection.sendMessage(dataSource, extras, null)
                    } catch (exc: Exception) {
                        Log.e(LOG_ID, "SendMessage exception: " + exc.toString())
                    }
                }.start()
            }
            if (dataSource == NotifyDataSource.MESSAGECLIENT || dataSource == NotifyDataSource.BROADCAST) {
                if (sharedPref!!.getBoolean(Constants.SHARED_PREF_NOTIFICATION, false)) {
                    val curAlarmType = ReceiveData.getAlarmType()
                    val forceAlarm = (ReceiveData.alarm and 8) != 0 // alarm triggered by Juggluco
                    Log.d(LOG_ID, "Check vibration: force=" + forceAlarm.toString() +
                            " - curAlarmType=" + curAlarmType.toString() +
                            " - lastAlarmType=" + lastAlarmType.toString() +
                            " - lastAlarmTime=" + lastAlarmTime.toString() +
                            " - time=" + ReceiveData.time.toString() +
                            " - delta=" + ReceiveData.delta.toString() +
                            " - rate=" + ReceiveData.rate.toString() +
                            " - diff=" + (ReceiveData.time - lastAlarmTime).toString()
                    )
                    if (curAlarmType == ReceiveData.AlarmType.LOW_ALARM || curAlarmType == ReceiveData.AlarmType.LOW)
                    {
                        // Low alarm only, if the values are still falling!
                        val durLow: Long
                        if (BuildConfig.DEBUG)
                            durLow = 1000
                        else
                            durLow = sharedPref!!.getLong(Constants.SHARED_PREF_NOTIFY_DURATION_LOW, 15) * 60 * 1000
                        if( forceAlarm || (curAlarmType < lastAlarmType && (ReceiveData.delta < 0F || ReceiveData.rate < 0F) && (ReceiveData.time - lastAlarmTime >= durLow)) )
                        {
                            if( vibrate(curAlarmType) ) {
                                lastAlarmTime = ReceiveData.time
                                lastAlarmType = curAlarmType
                            }
                        }
                    }
                    else if (curAlarmType == ReceiveData.AlarmType.HIGH_ALARM || curAlarmType == ReceiveData.AlarmType.HIGH)
                    {
                        // High alarm only, if the values are still rising!
                        val durHigh: Long
                        if (BuildConfig.DEBUG)
                            durHigh = 1000
                        else
                            durHigh = sharedPref!!.getLong(Constants.SHARED_PREF_NOTIFY_DURATION_HIGH, 20) * 60 * 1000
                        if( forceAlarm || (curAlarmType > lastAlarmType && (ReceiveData.delta > 0F || ReceiveData.rate > 0F) && (ReceiveData.time - lastAlarmTime >= durHigh)) )
                        {
                            if( vibrate(curAlarmType) ) {
                                lastAlarmTime = ReceiveData.time
                                lastAlarmType = curAlarmType
                            }
                        }
                    }
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: " + exc.toString())
        }
    }
}