package de.michelinside.glucodatahandler

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import de.michelinside.glucodatahandler.common.notifier.*


object ActiveComplicationHandler: NotifierInterface {
    private const val LOG_ID = "GlucoDataHandler.ActiveComplicationHandler"
    private var packageInfo: PackageInfo? = null
    private var complicationClasses = mutableMapOf<Int, ComponentName>()
    private var noComplication = false   // check complications at least one time

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
                @Suppress("DEPRECATION")
                packageInfo = context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SERVICES
                )
            }
        }
        return packageInfo!!
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        Thread {
            try {
                if (complicationClasses.isNotEmpty()) {
                    Log.d(LOG_ID, "Update " + complicationClasses.size + " complication(s).")
                    // upgrade all at once can cause a disappear of icon and images in ambient mode,
                    // so use some delay!
                    complicationClasses.forEach {
                        if (dataSource != NotifySource.TIME_VALUE)
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
                    Log.d(LOG_ID, "Got " + packageInfo.services.size + " services.")
                    packageInfo.services.forEach {
                        val isComplication =
                            if (dataSource == NotifySource.TIME_VALUE) {
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
                            if (dataSource != NotifySource.TIME_VALUE)
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
            } catch (exc: Exception) {
                Log.e(LOG_ID, "Update complication exception: " + exc.toString())
            }
        }.start()
    }
}