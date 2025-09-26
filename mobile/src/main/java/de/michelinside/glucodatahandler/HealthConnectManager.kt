package de.michelinside.glucodatahandler

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.metadata.Device // IMPORT ensured
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.BloodGlucose
import java.time.Instant
import java.time.ZonedDateTime
import java.util.UUID

class HealthConnectManager {

    companion object {
        private const val LOG_ID = "GDH.HealthConnectManager"
        private var healthConnectClient: HealthConnectClient? = null
        val WRITE_GLUCOSE_PERMISSIONS =
            setOf(HealthPermission.getWritePermission(BloodGlucoseRecord::class))

        private var instance: HealthConnectManager? = null
        fun getInstance(context: Context): HealthConnectManager =
            instance ?: synchronized(this) {
                instance ?: HealthConnectManager().also { instance = it }
            }
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
        if (Build.VERSION.SDK_INT < 28) {
            return false
        }
        return try {
            // Attempt to get or create the client.
            // If this doesn't throw an exception, Health Connect is considered available.
            HealthConnectClient.getOrCreate(context)
            true // Health Connect client obtained successfully
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
    suspend fun hasAllPermissions(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 28) {
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
    fun requestPermissions(launcher: ActivityResultLauncher<Set<String>>) {
        if (Build.VERSION.SDK_INT >= 28) {
            launcher.launch(WRITE_GLUCOSE_PERMISSIONS)
        }
    }

    /**
     * Writes a BloodGlucoseRecord to Health Connect.
     * @param context The application context.
     * @param glucoseValue The glucose concentration value (assuming mg/dL).
     * @param time The time the measurement was taken.
     * @param uuid A unique identifier for the record.
     */
    suspend fun writeGlucoseData(context: Context, glucoseValue: Double, time: Instant, uuid: UUID) {
        val client = getHealthConnectClient(context)
        if (client == null) {
            Log.e(LOG_ID, "HealthConnectClient is not available (client is null).") // Changed log message
            return
        }
        
        // It might be redundant to call isHealthConnectAvailable again if getHealthConnectClient already tried and failed
        // However, keeping it here for explicit check if desired.
        if (!isHealthConnectAvailable(context)) { 
             Log.w(LOG_ID, "Health Connect not available (checked by isHealthConnectAvailable).")
             return
        }

        if (!hasAllPermissions(context)) {
            Log.w(LOG_ID, "Required Health Connect permissions are not granted.")
            return
        }

        try {
            // Using Metadata.autoRecorded() based on the web example for StepsRecord
            // Device.TYPE_PHONE (1) is used as it's running on a phone.
            val deviceInfo = Device(manufacturer = "GlucoDataHandler", model = "App", type = Device.TYPE_PHONE)
            val currentMeta = Metadata.autoRecorded(device = deviceInfo)
            // Note: clientRecordId (uuid) might not be directly settable with autoRecorded metadata this way.
            // If uuid is crucial for deduplication via clientRecordId, further investigation for this API version is needed.

            val bloodGlucoseRecord = BloodGlucoseRecord(
                time = time,
                zoneOffset = ZonedDateTime.now().offset,
                level = BloodGlucose.milligramsPerDeciliter(glucoseValue),
                metadata = currentMeta
            )

            val recordsToInsert = listOf(bloodGlucoseRecord)
            client.insertRecords(recordsToInsert)
            Log.i(LOG_ID, "Successfully wrote glucose data to Health Connect: $glucoseValue at $time with UUID: $uuid (used autoRecorded metadata)")

        } catch (e: Exception) {
            Log.e(LOG_ID, "Error writing glucose data to Health Connect: ${e.message}")
        }
    }

    /**
     * Opens the Health Connect app settings in the Google Play Store.
     * This is useful for guiding the user to grant permissions or install Health Connect.
     * @param context The application context.
     */
    fun openHealthConnectSettings(context: Context) {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=com.google.android.apps.healthdata")
        )
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            Log.e(LOG_ID, "Could not resolve intent to open Health Connect settings.")
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")
            )
            context.startActivity(webIntent)
        }
    }
}
