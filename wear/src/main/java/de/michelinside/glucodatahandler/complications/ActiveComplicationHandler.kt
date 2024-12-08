package de.michelinside.glucodatahandler

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Log
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.WearPhoneConnection
import de.michelinside.glucodatahandler.common.notifier.*
import de.michelinside.glucodatahandler.common.receiver.ScreenEventReceiver
import de.michelinside.glucodatahandler.common.utils.WakeLockHelper
import java.math.RoundingMode


object ActiveComplicationHandler: NotifierInterface {
    private const val LOG_ID = "GDH.ActiveComplicationHandler"
    private var packageInfo: PackageInfo? = null
    private var complicationClasses = mutableMapOf<Int, ComponentName>()
    private var noComplication = false   // check complications at least one time
    private var alwaysUpdateComplications = true
    private var forceUpdataAll = false
    private var waitForUpdateThread: Thread? = null

    init {
        Log.d(LOG_ID, "init called")
    }

    fun addComplication(id: Int, component: ComponentName) {
        complicationClasses[id] = component
    }

    fun remComplication(id: Int) {
        complicationClasses.remove(id)
        noComplication = complicationClasses.isEmpty()
    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun getPackages(context: Context): PackageInfo {
        if (packageInfo == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageInfo = context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_SERVICES.toLong())
                )
            } else {
                packageInfo = context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SERVICES
                )
            }
        }
        return packageInfo!!
    }

    fun checkAlwaysUpdateComplications(context: Context) {
        val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
        alwaysUpdateComplications = sharedPref.getBoolean(Constants.SHARED_PREF_PHONE_WEAR_SCREEN_OFF_UPDATE, true)
        Log.d(LOG_ID, "Settings changed - always update complications: $alwaysUpdateComplications - display off: ${ScreenEventReceiver.isDisplayOff()}")
        ReceiveData.showObsoleteOnScreenOff = !alwaysUpdateComplications
    }

    private fun startWaitForUpdateThread(context: Context) {
        try {
            Log.v(LOG_ID, "Start wait for update thread")
            stopWaitForUpdateThread()
            waitForUpdateThread = Thread {
                try {
                    Log.d(LOG_ID, "Start wait for update thread")
                    Thread.sleep(1000)
                    waitForUpdateThread = null
                    if(forceUpdataAll) {
                        Log.w(LOG_ID, "No updates received yet!")
                        OnNotifyData(context, NotifySource.MESSAGECLIENT, null)
                    }
                } catch (exc: InterruptedException) {
                    Log.d(LOG_ID, "Check wait for update interrupted")
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Exception wait for update thread: " + exc.toString())
                }
                waitForUpdateThread = null
            }
            waitForUpdateThread!!.start()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Exception in wait for update thread: " + exc.toString())
        }
    }

    private fun stopWaitForUpdateThread() {
        try {
            Log.v(LOG_ID, "Stop wait for update thread for $waitForUpdateThread")
            if (waitForUpdateThread != null && waitForUpdateThread!!.isAlive && waitForUpdateThread!!.id != Thread.currentThread().id )
            {
                Log.d(LOG_ID, "Stop wait for update thread!")
                waitForUpdateThread!!.interrupt()
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Exception in stop wait for update thread: " + exc.toString())
        }
    }

    fun canUpdateComplications(dataSource: NotifySource): Boolean {
        Log.d(LOG_ID, "Check update complications called for $dataSource - always update: $alwaysUpdateComplications - display off: ${ScreenEventReceiver.isDisplayOff()}")
        if(alwaysUpdateComplications)
            return dataSource != NotifySource.DISPLAY_STATE_CHANGED
        // else only update if screen is on or switched off
        if(ScreenEventReceiver.isDisplayOff())
            return dataSource == NotifySource.DISPLAY_STATE_CHANGED   // after display is switched off, update once to set complications to obsolete
        if(dataSource == NotifySource.DISPLAY_STATE_CHANGED) {
            // display switched on
            forceUpdataAll = true   // force update of all complications after display is switched on
            if(alwaysUpdateComplications || ReceiveData.getElapsedTimeMinute(RoundingMode.HALF_UP) == 0L)
                return true  // data is still up to date
            if(WearPhoneConnection.nodesConnected) {
                // if phone is connected, not updating, wait for data from phone
                startWaitForUpdateThread(GlucoDataService.context!!)
                return false
            }
            return true  // update to last values as no phone is connected
        }
        // else update complications
        return true
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        Log.d(LOG_ID, "OnNotifyData called for $dataSource")
        stopWaitForUpdateThread()
        if(!canUpdateComplications(dataSource))
            return  // do not update, if display is off
        Thread {
            try {
                WakeLockHelper(context).use {
                    if(ScreenEventReceiver.isDisplayOff()) {
                        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                    } else {
                        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY)
                    }
                    if (complicationClasses.isNotEmpty()) {
                        Log.d(LOG_ID, "Update " + complicationClasses.size + " complication(s).")
                        // upgrade all at once can cause a disappear of icon and images in ambient mode,
                        // so use some delay!
                        complicationClasses.forEach {
                            if (dataSource != NotifySource.TIME_VALUE && !forceUpdataAll)
                                Thread.sleep(50)  // add delay to prevent disappearing complication icons in ambient mode
                            ComplicationDataSourceUpdateRequester
                                .create(
                                    context = context,
                                    complicationDataSourceComponent = it.value
                                )
                                .requestUpdate(it.key)
                        }
                    } else if (!noComplication) {
                        noComplication = true  // disable to prevent re-updating complications, if there is none...
                        val packageInfo = getPackages(context)
                        if(packageInfo.services != null && packageInfo.services!!.isNotEmpty()) {
                            Log.d(LOG_ID, "Got " + packageInfo.services!!.size + " services.")
                            packageInfo.services!!.forEach {
                                val isComplication =
                                    if (dataSource == NotifySource.TIME_VALUE && !forceUpdataAll) {
                                        // only update time complications
                                        TimeComplicationBase::class.java.isAssignableFrom(
                                            Class.forName(
                                                it.name
                                            )
                                        )
                                    } else {
                                        BgValueComplicationService::class.java.isAssignableFrom(
                                            Class.forName(
                                                it.name
                                            )
                                        )
                                    }

                                if (isComplication) {
                                    if (dataSource != NotifySource.TIME_VALUE && !forceUpdataAll)
                                        Thread.sleep(10)
                                    ComplicationDataSourceUpdateRequester
                                        .create(
                                            context = context,
                                            complicationDataSourceComponent = ComponentName(context, it.name)
                                        )
                                        .requestUpdateAll()
                                }
                            }
                        }
                    }
                    forceUpdataAll = false
                }
            } catch (exc: Exception) {
                Log.e(LOG_ID, "Update complication exception: " + exc.toString())
            }
        }.start()
    }
}