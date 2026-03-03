package de.michelinside.glucodatahandler.healthconnect

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import de.michelinside.glucodatahandler.common.utils.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.BloodGlucose
import de.michelinside.glucodatahandler.common.R
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.database.GlucoseValue
import de.michelinside.glucodatahandler.common.database.dbAccess
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZonedDateTime

enum class HealthConnectState(val resId: Int) {
    UNKNOWN(0),
    DISABLED(0),
    NOT_AVAILABLE(R.string.health_connect_not_available),
    NO_PERMISSION(R.string.health_connect_no_permission),
    CONNECTED(R.string.state_connected),
    ERROR(R.string.health_connect_error)
}

object HealthConnectManager: NotifierInterface {

    private const val LOG_ID = "GDH.HealthConnectManager"
    private var healthConnectClient: HealthConnectClient? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var sharedExtraPref: SharedPreferences
    var state = HealthConnectState.UNKNOWN
        private set(value) {
            if (field != value) {
                Log.i(LOG_ID, "Health Connect state changed from $field to $value")
                field = value
                InternalNotifier.notify(GlucoDataService.context!!, NotifySource.UPDATE_MAIN, null)
            }
        }
    private var lastValueTime: Long = 0
    var enabled = false
        private set

    val WRITE_GLUCOSE_PERMISSIONS =
        setOf(HealthPermission.getWritePermission(BloodGlucoseRecord::class))


    fun init(context: Context) {
        sharedExtraPref = context.getSharedPreferences(Constants.SHARED_PREF_EXTRAS_TAG, Context.MODE_PRIVATE)
    }

    fun close(context: Context) {
        disable()
    }

    fun enable() {
        enabled = true
        InternalNotifier.addNotifier( GlucoDataService.context!!, this,
            mutableSetOf(
                NotifySource.DB_DATA_CHANGED
            )
        )
        lastValueTime = sharedExtraPref.getLong(Constants.SHARED_PREF_HEALTH_CONNECT_LAST_VALUE_TIME, 0L)
        writeLastValues(GlucoDataService.context!!)
        Log.i(LOG_ID, "Health Connect enabled")
    }

    fun disable() {
        enabled = false
        state = HealthConnectState.DISABLED
        InternalNotifier.remNotifier(
            GlucoDataService.context!!, this
        )
        Log.i(LOG_ID, "Health Connect disabled")
    }

    fun getPermissionRequestContract(): ActivityResultContract<Set<String>, Set<String>> {
        return PermissionController.createRequestPermissionResultContract()
    }

    private fun getHealthConnectClient(context: Context): HealthConnectClient? {
        if (healthConnectClient == null) {
            try {
                healthConnectClient = HealthConnectClient.getOrCreate(context)
            } catch (e: Exception) {
                Log.e(LOG_ID, "Error getting HealthConnectClient: ${e.message}")
            }
        }
        return healthConnectClient
    }

    /**
     * Checks if Health Connect is available on the device by attempting to create a client.
     * @param context The application context.
     * @return True if Health Connect is available, false otherwise.
     */
    fun isHealthConnectAvailable(context: Context): Boolean {
        return try {
            // Attempt to get or create the client.
            // If this doesn't throw an exception, Health Connect is considered available.
            return getHealthConnectClient(context) != null
        } catch (e: Exception) {
            Log.w(LOG_ID, "Health Connect not available or client creation failed: ${e.message}")
            false // Exception indicates Health Connect is not available or not set up
        }
    }

    /**
     * Checks if the application has been granted all the required permissions for Health Connect.
     * @param context The application context.
     * @return True if all permissions are granted, false otherwise.
     */
    private suspend fun hasAllPermissions(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P && !isHealthConnectAvailable(context)) {
            return false
        }
        return try {
            val controller = getHealthConnectClient(context)?.permissionController ?: return false
            val allGrantedPermissions = controller.getGrantedPermissions() // Corrected: No arguments
            allGrantedPermissions.containsAll(WRITE_GLUCOSE_PERMISSIONS)
        } catch (e: Exception) {
            Log.e(LOG_ID, "Error checking permissions: ${e.message}")
            false
        }
    }

    /**
     * Launches the UI flow to request necessary permissions from the user.
     * @param launcher The ActivityResultLauncher configured to handle permission requests.
     */
    private fun requestPermissions(launcher: ActivityResultLauncher<Set<String>>) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                launcher.launch(WRITE_GLUCOSE_PERMISSIONS)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Error requesting permissions: ${exc.message}")
        }
    }

    /**
     * Checks if Health Connect is available and if permissions are granted.
     * If not, it initiates steps to fulfill these requirements (e.g., opening Play Store or launching permission request).
     * @param context The application context.
     * @param permissionLauncher Launcher to request Health Connect permissions.
     * @return True if Health Connect is available and all permissions are granted, false otherwise.
     */
    suspend fun checkAndEnsureRequirements(context: Context, permissionLauncher: ActivityResultLauncher<Set<String>>): Boolean {
        Log.d(LOG_ID, "checkAndEnsureRequirements called")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P && !isHealthConnectAvailable(context)) {
            Log.w(LOG_ID, "Health Connect not supported on API < 28 for checkAndEnsureRequirements.")
            return false
        }
        if (!isHealthConnectAvailable(context)) {
            Log.i(LOG_ID, "Health Connect not available. Opening settings...")
            openHealthConnectPlaystore(context)
            return false
        }
        if (!hasAllPermissions(context)) {
            Log.i(LOG_ID, "Health Connect permissions not granted. Requesting permissions...")
            requestPermissions(permissionLauncher)
            return false
        }
        Log.i(LOG_ID, "Health Connect is available and all permissions are granted.")
        return true
    }

    // check only without doing any action
    suspend fun checkRequirements(context: Context): Boolean {
        Log.d(LOG_ID, "checkAndEnsureRequirements called")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P && !isHealthConnectAvailable(context)) {
            Log.w(LOG_ID, "Health Connect not supported on API < 28 for checkAndEnsureRequirements.")
            return false
        }
        if (!isHealthConnectAvailable(context)) {
            Log.i(LOG_ID, "Health Connect not available.")
            return false
        }
        if (!hasAllPermissions(context)) {
            Log.i(LOG_ID, "Health Connect permissions not granted.")
            return false
        }
        Log.i(LOG_ID, "Health Connect is available and all permissions are granted.")
        return true
    }

    fun writeLastValues(context: Context): Boolean {
        try {
            val minTime = maxOf(lastValueTime + 1, System.currentTimeMillis() - Constants.DB_MAX_DATA_WEAR_TIME_MS)
            Log.d(LOG_ID, "Write last values from ${Utils.getUiTimeStamp(minTime)}")
            return writeGlucoseData(context, dbAccess.getGlucoseValues(minTime))
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Error writing last values: ${exc.message}")
        }
        return false
    }

    private fun writeGlucoseData(context: Context, glucoseValues: List<GlucoseValue>): Boolean {
        if(!enabled)
            return false
        val client = getHealthConnectClient(context)
        if (client == null) {
            state = HealthConnectState.NOT_AVAILABLE
            Log.e(LOG_ID, "HealthConnectClient is not available (client is null).") // Changed log message
            return false
        }

        scope.launch {
            try {
                if(!hasAllPermissions(context)) {
                    state = HealthConnectState.NO_PERMISSION
                    return@launch
                }
                if(glucoseValues.isNotEmpty()) {
                    // Using Metadata.autoRecorded() based on the web example for StepsRecord
                    // Device.TYPE_PHONE (1) is used as it's running on a phone.
                    val deviceInfo = Device(
                        manufacturer = "GlucoDataHandler",
                        model = "App",
                        type = Device.TYPE_PHONE
                    )
                    val currentMeta = Metadata.Companion.autoRecorded(device = deviceInfo)
                    val recordsToInsert = mutableListOf<BloodGlucoseRecord>()

                    glucoseValues.forEach {
                        recordsToInsert.add(BloodGlucoseRecord(
                            time = Instant.ofEpochMilli(it.timestamp),
                            zoneOffset = ZonedDateTime.now().offset,
                            level = BloodGlucose.milligramsPerDeciliter(it.value.toDouble()),
                            metadata = currentMeta
                        ))
                    }

                    client.insertRecords(recordsToInsert)
                    Log.i(LOG_ID, "Successfully wrote ${recordsToInsert.size} glucose data to Health Connect from ${Utils.getUiTimeStamp(glucoseValues.first().timestamp)} to ${Utils.getUiTimeStamp(glucoseValues.last().timestamp)}")
                    updateLastValueTime(glucoseValues.last().timestamp)
                } else {
                    Log.d(LOG_ID, "No glucose data to write to Health Connect")
                }
                state = HealthConnectState.CONNECTED
            } catch (e: Exception) {
                Log.e(LOG_ID, "Error writing glucose data to Health Connect: ${e.message}")
                state = HealthConnectState.ERROR
            }
        }
        return true
    }

    private fun updateLastValueTime(time: Long) {
        if(time > lastValueTime) {
            Log.i(LOG_ID, "Updating last value time to ${Utils.getUiTimeStamp(time)}")
            lastValueTime = time
            with(sharedExtraPref.edit()) {
                putLong(Constants.SHARED_PREF_HEALTH_CONNECT_LAST_VALUE_TIME, lastValueTime)
                apply()
            }
        }
    }

    /**
     * Opens the Health Connect app settings in the Google Play Store.
     * This is useful for guiding the user to grant permissions or install Health Connect.
     * @param context The application context.
     */
    private fun openHealthConnectPlaystore(context: Context) {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=com.google.android.apps.healthdata")
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            Log.e(LOG_ID, "Could not resolve intent to open Health Connect settings.")
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")
            )
            webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(webIntent)
        }
    }

    fun openHealthConnectSettings(context: Context) {
        try {
            val sdkStatus = HealthConnectClient.getSdkStatus(context)
            if (sdkStatus == HealthConnectClient.SDK_AVAILABLE) {
                val intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                openHealthConnectPlaystore(context)
            }
        } catch (e: Exception) {
            Log.e(LOG_ID, "Error opening Health Connect settings: ${e.message}")
        }
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            Log.d(LOG_ID, "OnNotifyData - dataSource: $dataSource - enable: $enabled")
            if(enabled) {
                if(dataSource == NotifySource.DB_DATA_CHANGED && extras != null) {
                    val startTime = extras.getLong(Constants.EXTRA_START_TIME)
                    val endTime = extras.getLong(Constants.EXTRA_END_TIME)
                    if(startTime > 0 && endTime > 0 && startTime <= endTime) {
                        writeGlucoseData(context, dbAccess.getGlucoseValuesInRange(startTime, endTime+1))
                    } else {
                        Log.w(LOG_ID, "Invalid time range: $startTime - $endTime")
                    }
                } else {
                    Log.w(LOG_ID, "Unknown dataSource: $dataSource")
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception for $dataSource: " + exc.message.toString())
        }
    }

}
