package de.michelinside.glucodatahandler.common.utils

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.util.Log
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.receiver.InternalActionReceiver
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

object PackageUtils {
    private val LOG_ID = "GDH.Utils.Packages"
    private val packages = HashMap<String, String>()

    private var updateInProgress = AtomicBoolean(false)

    fun updatePackages(context: Context) {
        if(!updateInProgress.get()) {
            updateInProgress.set(true)
            packages.clear()
            GlobalScope.launch {
                Log.d(LOG_ID, "Start updating packages")
                val receivers: List<ResolveInfo>
                val intent = Intent(Intent.ACTION_MAIN)
                intent.addCategory(Intent.CATEGORY_LAUNCHER)
                receivers = context.packageManager.queryIntentActivities(
                    intent,
                    PackageManager.GET_META_DATA
                )
                for (resolveInfo in receivers) {
                    val pkgName = resolveInfo.activityInfo.packageName
                    val name =
                        resolveInfo.activityInfo.loadLabel(context.packageManager).toString()
                    if (pkgName != null) {
                        packages[pkgName] = name
                    }
                }
                updateInProgress.set(false)
                Log.i(LOG_ID, "${packages.size} packages found")
            }
        }
    }

    private fun waitForUpdate() {
        if(updateInProgress.get()) {
            runBlocking {
                val result = async {
                    Log.v(LOG_ID, "Wait for updating packages")
                    while (updateInProgress.get()) {
                        delay(10)
                    }
                    Log.v(LOG_ID, "Update packages done")
                    true
                }
                result.await()
            }
        }
    }

    fun getPackages(context: Context): HashMap<String, String> {
        waitForUpdate()
        if (packages.isEmpty()) {
            Log.i(LOG_ID, "Updating receivers")
            updatePackages(context)
            waitForUpdate()
        }
        return packages
    }

    fun getAppIntent(
        context: Context,
        activityClass: Class<*>,
        requestCode: Int
    ): PendingIntent {
        val launchIntent = Intent(context, activityClass)
        return PendingIntent.getActivity(
            context,
            requestCode,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun getTapAction(
        context: Context,
        tapAction: String?
    ): Pair<Intent?, Boolean> {  // Boolean: true = Broadcase - false = Activity
        Log.d(LOG_ID, "Get tap action $tapAction")
        if (tapAction == null && context.packageName != null) {
            return getTapAction(context, context.packageName)
        }
        if (tapAction.isNullOrEmpty())
            return Pair(null, false)
        if (tapAction.startsWith(Constants.ACTION_PREFIX)) {
            val intent = Intent(context, InternalActionReceiver::class.java)
            intent.action = tapAction
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            intent.setPackage(context.packageName)
            return Pair(intent, true)
        }
        return Pair(context.packageManager.getLaunchIntentForPackage(tapAction), false)
    }

    fun getTapActionIntent(
        context: Context,
        tapAction: String?,
        requestCode: Int
    ): PendingIntent? {
        val intent = getTapAction(context, tapAction)
        if (intent.first != null) {
            Log.d(
                LOG_ID,
                "Create tap action intent for $tapAction - code: $requestCode - broadcast: ${intent.second}"
            )
            if (intent.second)
                return PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent.first!!,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
                )
            return PendingIntent.getActivity(
                context,
                requestCode,
                intent.first,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        return null
    }


    private val tapActionFilter = mutableSetOf<String>()
    private fun getTapActionFilter(context: Context): MutableSet<String> {
        if (tapActionFilter.isEmpty()) {
            tapActionFilter.add(context.packageName)
            tapActionFilter.add(Constants.PACKAGE_JUGGLUCO)
            tapActionFilter.add(Constants.PACKAGE_GLUCODATAAUTO)
            tapActionFilter.add("info.nightscout")  // AAPS
            tapActionFilter.add("com.eveningoutpost.dexdrip")
            tapActionFilter.add("jamorham.xdrip.plus")
            tapActionFilter.add("com.freestylelibre")
            tapActionFilter.add("org.nativescript.librelinkup")
            tapActionFilter.add("com.dexcom.")
            tapActionFilter.add("com.senseonics.")  // Eversense CGM
            tapActionFilter.add("esel.esel.")   // ESEL for Eversense
        }
        return tapActionFilter
    }

    fun tapActionFilterContains(context: Context, value: String): Boolean {
        getTapActionFilter(context).forEach {
            if(value.lowercase().startsWith(it.lowercase()))
                return true
        }
        return false
    }

    /*
    fun getAppIntent(context: Context, packageName: String, requestCode: Int): PendingIntent? {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            return PendingIntent.getActivity(
                context,
                requestCode,
                launchIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        return null
    }*/

    fun isPackageAvailable(context: Context, packageName: String): Boolean {
        return getPackages(context).containsKey(packageName)
    }

    private var gdaAvailable = false
    fun isGlucoDataAutoAvailable(context: Context): Boolean {
        if (!gdaAvailable) {
            gdaAvailable = isPackageAvailable(context, Constants.PACKAGE_GLUCODATAAUTO)
        }
        return gdaAvailable
    }
}