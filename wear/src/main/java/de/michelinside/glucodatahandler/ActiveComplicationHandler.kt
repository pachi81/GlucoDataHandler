import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import de.michelinside.glucodatahandler.BgValueComplicationService
import de.michelinside.glucodatahandler.common.ReceiveDataInterface
import de.michelinside.glucodatahandler.common.ReceiveDataSource


object ActiveComplicationHandler: ReceiveDataInterface {
    private const val LOG_ID = "GlucoDataHandler.ActiveComplicationHandler"
    private var packageInfo: PackageInfo? = null
    private var complicationClasses = mutableMapOf<Int, ComponentName>()
    init {
        Log.d(LOG_ID, "init called")
    }

    fun addComplication(id: Int, component: ComponentName) {
        complicationClasses[id] = component
    }

    fun remComplication(id: Int) {
        complicationClasses.remove(id)
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

    override fun OnReceiveData(context: Context, dataSource: ReceiveDataSource, extras: Bundle?) {
        Thread {
            try {
                if (complicationClasses.isEmpty()) {
                    val packageInfo = getPackages(context)
                    Log.d(LOG_ID, "Got " + packageInfo.services.size + " services.")
                    packageInfo.services.forEach {
                        val isComplication =
                            BgValueComplicationService::class.java.isAssignableFrom(Class.forName(it.name))
                        if (isComplication) {
                            Thread.sleep(10)
                            ComplicationDataSourceUpdateRequester
                                .create(
                                    context = context,
                                    complicationDataSourceComponent = ComponentName(context, it.name)
                                )
                                .requestUpdateAll()
                        }
                    }
                } else {
                    Log.d(LOG_ID, "Update " + complicationClasses.size + " complications.")
                    // upgrade all at once can cause a disappear of icon and images in ambient mode,
                    // so use some delay!
                    complicationClasses.forEach {
                        Thread.sleep(50)  // add delay to prevent disappearing complication icons in ambient mode
                        ComplicationDataSourceUpdateRequester
                            .create(
                                context = context,
                                complicationDataSourceComponent = it.value
                            )
                            .requestUpdate(it.key)
                    }
                }
            } catch (exc: Exception) {
                Log.e(LOG_ID, "Update complication exception: " + exc.toString())
            }
        }.start()
    }
}