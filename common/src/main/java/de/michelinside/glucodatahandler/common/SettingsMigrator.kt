package de.michelinside.glucodatahandler.common

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import androidx.core.content.edit
import de.michelinside.glucodatahandler.common.GlucoDataService.Companion.appSource
import de.michelinside.glucodatahandler.common.chart.ChartCreator
import de.michelinside.glucodatahandler.common.notification.AlarmType
import de.michelinside.glucodatahandler.common.notifier.DataSource
import de.michelinside.glucodatahandler.common.receiver.GlucoseDataReceiver
import de.michelinside.glucodatahandler.common.receiver.NotificationReceiver
import de.michelinside.glucodatahandler.common.tasks.LibreLinkSourceTask
import de.michelinside.glucodatahandler.common.utils.Log
import de.michelinside.glucodatahandler.common.utils.Utils.isScreenReaderOn
import java.util.Locale

object SettingsMigrator {

    private const val LOG_ID = "GDH.SettingsMigrator"
    private var migrated = false

    fun migrateSettings(context: Context) {
        Log.i(LOG_ID, "migrateSettings called")
        if(migrated)
            return

        val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, MODE_PRIVATE)

        val oldVersion = sharedPref.getInt(Constants.SHARED_PREF_GDH_VERSION, 0)
        val isUpgrade = oldVersion < BuildConfig.BASE_VERSION
        if(oldVersion != BuildConfig.BASE_VERSION) {
            Log.i(LOG_ID, "Migrate settings from version $oldVersion to ${BuildConfig.BASE_VERSION}")
            sharedPref.edit {
                putInt(Constants.SHARED_PREF_GDH_VERSION, BuildConfig.BASE_VERSION)
            }
        }

        if(!sharedPref.contains(Constants.SHARED_PREF_OBSOLETE_TIME)) {
            val sharedGlucosePref = context.getSharedPreferences(Constants.GLUCODATA_BROADCAST_ACTION, MODE_PRIVATE)
            var obsoleteTime = 6
            if(sharedGlucosePref.contains(Constants.EXTRA_SOURCE_INDEX)) {
                val srcOrdinal = sharedGlucosePref.getInt(Constants.EXTRA_SOURCE_INDEX, DataSource.NONE.ordinal)
                if (srcOrdinal == DataSource.JUGGLUCO.ordinal || srcOrdinal == DataSource.LIBRELINK.ordinal) {
                    obsoleteTime = 5
                }
            }
            Log.i(LOG_ID, "Migrate default obsolete time $obsoleteTime minutes")
            sharedPref.edit {
                putInt(Constants.SHARED_PREF_OBSOLETE_TIME, obsoleteTime)
            }
        }

        // show other unit should be default on for mmol/l as there was the raw value before
        // so this is only related, if use mmol/l is already set, else set to false
        if(!sharedPref.contains(Constants.SHARED_PREF_SHOW_OTHER_UNIT)) {
            val useMmol = if(sharedPref.contains(Constants.SHARED_PREF_USE_MMOL))
                sharedPref.getBoolean(Constants.SHARED_PREF_USE_MMOL, false)
            else false
            sharedPref.edit {
                putBoolean(Constants.SHARED_PREF_SHOW_OTHER_UNIT, useMmol)
            }
        }

        if(oldVersion <= 176 && sharedPref.contains(Constants.SHARED_PREF_COLOR_OUT_OF_RANGE)) {
            val curColor = sharedPref.getInt(Constants.SHARED_PREF_COLOR_OUT_OF_RANGE, ReceiveData.getAlarmTypeColor(AlarmType.LOW))
            if(curColor == Color.YELLOW) {
                Log.i(LOG_ID, "Migrate color out of range from YELLOW to 0xFFDC00")
                sharedPref.edit {
                    putInt(Constants.SHARED_PREF_COLOR_OUT_OF_RANGE, 0xFFFFDC00.toInt())
                }
            }
        }

        if(isUpgrade || !sharedPref.contains(Constants.SHARED_PREF_LIBRE_VERSION)) {
            sharedPref.edit {
                putString(Constants.SHARED_PREF_LIBRE_VERSION, LibreLinkSourceTask.version)
            }
        }

        if(!sharedPref.contains(Constants.SHARED_PREF_DEXCOM_SHARE_SERVER)) {
            if(!sharedPref.contains(Constants.SHARED_PREF_DEXCOM_SHARE_USE_US_URL)) {
                // check local for US and set to true if set
                val currentLocale = Locale.getDefault()
                val countryCode = currentLocale.country
                Log.i(LOG_ID, "Using country code $countryCode")
                sharedPref.edit {
                    when (countryCode.lowercase()) {
                        "us" -> {
                            putString(Constants.SHARED_PREF_DEXCOM_SHARE_SERVER, "us")
                        }

                        "jp" -> {
                            putString(Constants.SHARED_PREF_DEXCOM_SHARE_SERVER, "jp")
                        }

                        else -> {
                            putString(Constants.SHARED_PREF_DEXCOM_SHARE_SERVER, "eu")
                        }
                    }
                }
            } else {
                sharedPref.edit {
                    if (sharedPref.getBoolean(
                            Constants.SHARED_PREF_DEXCOM_SHARE_USE_US_URL,
                            false
                        )
                    )
                        putString(Constants.SHARED_PREF_DEXCOM_SHARE_SERVER, "us")
                    else
                        putString(Constants.SHARED_PREF_DEXCOM_SHARE_SERVER, "eu")
                    remove(Constants.SHARED_PREF_DEXCOM_SHARE_USE_US_URL)
                }
            }
            Log.i(LOG_ID, "Using dexcom server ${sharedPref.getString(Constants.SHARED_PREF_DEXCOM_SHARE_SERVER, "eu")}")
        }

        if(sharedPref.contains(Constants.SHARED_PREF_ALARM_SNOOZE_ON_NOTIFICATION) || !sharedPref.contains(Constants.SHARED_PREF_ALARM_SNOOZE_NOTIFICATION_BUTTONS) ) {
            sharedPref.edit {
                remove(Constants.SHARED_PREF_ALARM_SNOOZE_ON_NOTIFICATION)
                putStringSet(
                    Constants.SHARED_PREF_ALARM_SNOOZE_NOTIFICATION_BUTTONS,
                    mutableSetOf("60", "90", "120")
                )
            }
        }

        if(sharedPref.contains(Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_5_MINUTE_INTERVAl)) {
            val use5Min = sharedPref.getBoolean(Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_5_MINUTE_INTERVAl, false)
            Log.i(LOG_ID, "Remove old notification interval (use 5 minutes: $use5Min)")
            sharedPref.edit {
                putInt(Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_INTERVAl, if(use5Min) 5 else 0)
                remove(Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_5_MINUTE_INTERVAl)
            }
        }

        if(sharedPref.contains(Constants.SHARED_PREF_SOURCE_INTERVAL_DEPRECATED)) {
            val interval = (sharedPref.getString(Constants.SHARED_PREF_SOURCE_INTERVAL_DEPRECATED, "1")?: "1").toInt()
            Log.i(LOG_ID, "Remove old interval array value: $interval")
            sharedPref.edit {
                putInt(Constants.SHARED_PREF_SOURCE_INTERVAL, interval)
                remove(Constants.SHARED_PREF_SOURCE_INTERVAL_DEPRECATED)
            }
        }

        if(isUpgrade && sharedPref.contains(Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_APP_REGEX)) {
            val oldRegex = sharedPref.getString(Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_APP_REGEX, "")
            if(oldRegex == null || NotificationReceiver.oldGlucoseRegexes.contains(oldRegex)) {
                Log.i(LOG_ID, "Remove old notification regex $oldRegex")
                sharedPref.edit {
                    putString(Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_APP_REGEX, "")
                }
            }
        }

        if(isUpgrade && sharedPref.contains(Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_IOB_APP_REGEX)) {
            val oldRegex = sharedPref.getString(Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_IOB_APP_REGEX, "")
            if(oldRegex == null || NotificationReceiver.oldIobRegexes.contains(oldRegex)) {
                Log.i(LOG_ID, "Remove old IOB notification regex from $oldRegex")
                sharedPref.edit {
                    putString(
                        Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_IOB_APP_REGEX,
                        ""
                    )
                }
            }
        }

        if(isUpgrade && sharedPref.contains(Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_COB_APP_REGEX)) {
            val oldRegex = sharedPref.getString(Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_COB_APP_REGEX, "")
            if(oldRegex == null || NotificationReceiver.oldCobRegexes.contains(oldRegex)) {
                Log.i(LOG_ID, "Remove old COB notification regex from $oldRegex")
                sharedPref.edit {
                    putString(
                        Constants.SHARED_PREF_SOURCE_NOTIFICATION_READER_COB_APP_REGEX,
                        ""
                    )
                }
            }
        }

        if(Constants.IS_SECOND && !sharedPref.contains(Constants.PATIENT_NAME)) {
            sharedPref.edit {
                putString(Constants.PATIENT_NAME, "SECOND")
            }
        }

        if(appSource == AppSource.PHONE_APP) {
            migratePhoneSettings(context, sharedPref, oldVersion, isUpgrade)
        } else if(appSource == AppSource.WEAR_APP) {
            migrateWearSettings(context, sharedPref, oldVersion, isUpgrade)
        }

        migrateExtraSettings(context, sharedPref)

        migrated = true
    }  // migrateSettings

    private fun migratePhoneSettings(
        context: Context,
        sharedPref: SharedPreferences,
        oldVersion: Int,
        isUpgrade: Boolean
    ) {
        if(isUpgrade && oldVersion == 156) {
            // for existing users use the old setting
            if(!sharedPref.contains(Constants.SHARED_PREF_STANDARD_STATISTICS)) {
                sharedPref.edit {
                    putBoolean(Constants.SHARED_PREF_STANDARD_STATISTICS, false)
                }
            }
        }

        if(!sharedPref.contains(Constants.SHARED_PREF_GLUCODATA_RECEIVERS)) {
            val receivers = HashSet<String>()
            val sendToAod = sharedPref.getBoolean(Constants.SHARED_PREF_SEND_TO_GLUCODATA_AOD, false)
            if (sendToAod)
                receivers.add("de.metalgearsonic.glucodata.aod")
            Log.i(LOG_ID, "Upgrade receivers to " + receivers.toString())
            sharedPref.edit {
                putStringSet(Constants.SHARED_PREF_GLUCODATA_RECEIVERS, receivers)
            }
        }

        if(!sharedPref.contains(Constants.SHARED_PREF_XDRIP_RECEIVERS)) {
            val receivers = HashSet<String>()
            receivers.add("com.eveningoutpost.dexdrip")
            Log.i(LOG_ID, "Upgrade receivers to " + receivers.toString())
            sharedPref.edit {
                putStringSet(Constants.SHARED_PREF_XDRIP_RECEIVERS, receivers)
            }
        }

        // create default tap actions
        // notifications
        if(!sharedPref.contains(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_TAP_ACTION)) {
            val curApp = context.packageName
            Log.i(LOG_ID, "Setting default tap action for notification to $curApp")
            sharedPref.edit {
                putString(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_TAP_ACTION, curApp)
            }
        }
        if(!sharedPref.contains(Constants.SHARED_PREF_SECOND_PERMANENT_NOTIFICATION_TAP_ACTION)) {
            val curApp = context.packageName
            Log.i(LOG_ID, "Setting default tap action for second notification to $curApp")
            sharedPref.edit {
                putString(Constants.SHARED_PREF_SECOND_PERMANENT_NOTIFICATION_TAP_ACTION, curApp)
            }
        }
        // widgets
        if(!sharedPref.contains(Constants.SHARED_PREF_FLOATING_WIDGET_TAP_ACTION)) {
            val curApp = context.packageName
            Log.i(LOG_ID, "Setting default tap action for floating widget to $curApp")
            sharedPref.edit {
                putString(Constants.SHARED_PREF_FLOATING_WIDGET_TAP_ACTION, curApp)
            }
        }
        if(!sharedPref.contains(Constants.SHARED_PREF_WIDGET_TAP_ACTION)) {
            val curApp = context.packageName
            Log.i(LOG_ID, "Setting default tap action for widget to $curApp")
            sharedPref.edit {
                putString(Constants.SHARED_PREF_WIDGET_TAP_ACTION, curApp)
            }
        }
        if(!sharedPref.contains(Constants.SHARED_PREF_FLOATING_WIDGET_SIZE_MIGRATION) || !sharedPref.contains(Constants.SHARED_PREF_FLOATING_WIDGET_SIZE_MIGRATION_2)) {
            if(sharedPref.contains(Constants.SHARED_PREF_FLOATING_WIDGET_SIZE)) {
                val oldValue = sharedPref.getInt(Constants.SHARED_PREF_FLOATING_WIDGET_SIZE, 0)
                if(oldValue in 1..10) {
                    Log.i(LOG_ID, "Migrating size from $oldValue")
                    sharedPref.edit {
                        putInt(Constants.SHARED_PREF_FLOATING_WIDGET_SIZE, oldValue + 1)
                    }
                }
            }
            sharedPref.edit {
                putBoolean(Constants.SHARED_PREF_FLOATING_WIDGET_SIZE_MIGRATION, true)
                putBoolean(Constants.SHARED_PREF_FLOATING_WIDGET_SIZE_MIGRATION_2, true)
            }
        }


        // graph settings related to screen reader
        if(!sharedPref.contains(Constants.SHARED_PREF_GRAPH_DURATION_PHONE_MAIN)) {
            val isScreenReader = context.isScreenReaderOn()
            Log.i(LOG_ID, "Setting default duration for graph - screenReader: $isScreenReader")
            sharedPref.edit {
                putInt(
                    Constants.SHARED_PREF_GRAPH_DURATION_PHONE_MAIN,
                    if (isScreenReader) 0 else ChartCreator.defaultDurationHours
                )
            }
        }
        if(sharedPref.contains(Constants.DEPRECATED_SHARED_PREF_GRAPH_DURATION_PHONE_NOTIFICATION) || !sharedPref.contains(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_SHOW_GRAPH)) {
            val isScreenReader = context.isScreenReaderOn()
            Log.i(LOG_ID, "Setting default duration for notification graph - screenReader: $isScreenReader")
            sharedPref.edit {
                putBoolean(
                    Constants.SHARED_PREF_PERMANENT_NOTIFICATION_SHOW_GRAPH,
                    !isScreenReader && sharedPref.getInt(
                        Constants.DEPRECATED_SHARED_PREF_GRAPH_DURATION_PHONE_NOTIFICATION,
                        0
                    ) > 0
                )
                remove(Constants.DEPRECATED_SHARED_PREF_GRAPH_DURATION_PHONE_NOTIFICATION)
            }
        }
        if(!sharedPref.contains(Constants.SHARED_PREF_FULLSCREEN_LANDSCAPE)) {
            val isScreenReader = context.isScreenReaderOn()
            Log.i(LOG_ID, "Setting default fullscreen mode for screenReader: $isScreenReader")
            sharedPref.edit {
                putBoolean(Constants.SHARED_PREF_FULLSCREEN_LANDSCAPE, !isScreenReader)
            }
        }

        if(sharedPref.contains(Constants.DEPRECATED_SHARED_PREF_GRAPH_DURATION_PHONE_WIDGET)) {
            val oldDuration = sharedPref.getInt(Constants.DEPRECATED_SHARED_PREF_GRAPH_DURATION_PHONE_WIDGET, 0)
            Log.i(LOG_ID, "Migratate old widget duration of $oldDuration hours to bitmap duration")
            sharedPref.edit {
                if (oldDuration > 0)
                    putInt(Constants.SHARED_PREF_GRAPH_BITMAP_DURATION, oldDuration)
                remove(Constants.DEPRECATED_SHARED_PREF_GRAPH_DURATION_PHONE_WIDGET)
            }
        }
        if(sharedPref.contains(Constants.DEPRECATED_SHARED_PREF_GRAPH_SHOW_AXIS_PHONE_WIDGET)) {
            val oldShowAxis = sharedPref.getBoolean(Constants.DEPRECATED_SHARED_PREF_GRAPH_SHOW_AXIS_PHONE_WIDGET, false)
            Log.i(LOG_ID, "Migratate old widget show axis of $oldShowAxis to bitmap show axis")
            sharedPref.edit {
                putBoolean(Constants.SHARED_PREF_GRAPH_BITMAP_SHOW_AXIS, oldShowAxis)
                remove(Constants.DEPRECATED_SHARED_PREF_GRAPH_SHOW_AXIS_PHONE_WIDGET)
            }
        }

        // Juggluco webserver settings
        if(!sharedPref.contains(Constants.SHARED_PREF_SOURCE_JUGGLUCO_WEBSERVER_ENABLED) || !sharedPref.contains(Constants.SHARED_PREF_SOURCE_JUGGLUCO_WEBSERVER_IOB_SUPPORT)) {
            // check current source for Juggluco and if Nightscout is enabled for local requests supporting IOB
            var webServer = false
            var apiSecret = ""
            if(sharedPref.getBoolean(Constants.SHARED_PREF_SOURCE_JUGGLUCO_ENABLED, true)
                && sharedPref.getBoolean(Constants.SHARED_PREF_NIGHTSCOUT_ENABLED, false)
                && sharedPref.getBoolean(Constants.SHARED_PREF_NIGHTSCOUT_IOB_COB, false)
                && sharedPref.getString(Constants.SHARED_PREF_NIGHTSCOUT_TOKEN, "").isNullOrEmpty()
                && sharedPref.getString(Constants.SHARED_PREF_NIGHTSCOUT_URL, "")!!.trim().trimEnd('/') == GlucoseDataReceiver.JUGGLUCO_WEBSERVER
            ) {
                val sharedGlucosePref = context.getSharedPreferences(Constants.GLUCODATA_BROADCAST_ACTION, MODE_PRIVATE)
                if(DataSource.fromIndex(sharedGlucosePref.getInt(Constants.EXTRA_SOURCE_INDEX, DataSource.NONE.ordinal)) == DataSource.JUGGLUCO) {
                    webServer = true
                    apiSecret = sharedPref.getString(Constants.SHARED_PREF_NIGHTSCOUT_SECRET, "")?:""
                }
            }
            Log.i(LOG_ID, "Using Juggluco webserver: $webServer")
            sharedPref.edit {
                putBoolean( Constants.SHARED_PREF_SOURCE_JUGGLUCO_WEBSERVER_ENABLED, webServer)
                putBoolean( Constants.SHARED_PREF_SOURCE_JUGGLUCO_WEBSERVER_IOB_SUPPORT, webServer)
                if (webServer) {
                    putString( Constants.SHARED_PREF_SOURCE_JUGGLUCO_WEBSERVER_API_SECRET, apiSecret)
                    putBoolean(Constants.SHARED_PREF_NIGHTSCOUT_ENABLED, false)
                }
            }
        }

        if(oldVersion < 166 && isUpgrade) {
            // notification icon
            if(!sharedPref.contains(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_ICON)
                || sharedPref.getString(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_ICON, "glucose") == "app") {
                sharedPref.edit {
                    putString(Constants.SHARED_PREF_PERMANENT_NOTIFICATION_ICON, "glucose")
                }
            }
            if(!sharedPref.contains(Constants.SHARED_PREF_SECOND_PERMANENT_NOTIFICATION_ICON)
                || sharedPref.getString(Constants.SHARED_PREF_SECOND_PERMANENT_NOTIFICATION_ICON, "trend") == "app") {
                sharedPref.edit {
                    putString(Constants.SHARED_PREF_SECOND_PERMANENT_NOTIFICATION_ICON, "trend")
                }
            }
            if(!sharedPref.contains(Constants.SHARED_PREF_THIRD_PERMANENT_NOTIFICATION_ICON)
                || sharedPref.getString(Constants.SHARED_PREF_THIRD_PERMANENT_NOTIFICATION_ICON, "delta") == "app") {
                sharedPref.edit {
                    putString(Constants.SHARED_PREF_THIRD_PERMANENT_NOTIFICATION_ICON, "delta")
                }
            }
        }

        // special Android 16 handling
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            if(!sharedPref.contains(Constants.SHARED_PREF_API_36_DISABLE_NOTIFICATION)) {
                Log.w(LOG_ID, "Disable second and third notification for Android 16")
                sharedPref.edit {
                    putBoolean(Constants.SHARED_PREF_API_36_DISABLE_NOTIFICATION, true)
                    putBoolean(Constants.SHARED_PREF_SECOND_PERMANENT_NOTIFICATION, false)
                    putBoolean(Constants.SHARED_PREF_THIRD_PERMANENT_NOTIFICATION, false)
                }
            }
        }

        // Health Connect interval support
        if(oldVersion < 170 && !sharedPref.contains(Constants.SHARED_PREF_SEND_TO_HEALTH_CONNECT_INTERVAL)) {
            if(sharedPref.contains(Constants.SHARED_PREF_SEND_TO_HEALTH_CONNECT) && sharedPref.getBoolean(Constants.SHARED_PREF_SEND_TO_HEALTH_CONNECT, false)) {
                Log.i(LOG_ID, "Setting default Health Connect interval for active instaces to 1 minute")
                sharedPref.edit {
                    putInt(Constants.SHARED_PREF_SEND_TO_HEALTH_CONNECT_INTERVAL, 1)
                }
            } else {
                // else set interval for force using 5 minutes in the future...
                Log.i(LOG_ID, "Setting default Health Connect interval to 5 minutes")
                sharedPref.edit {
                    putInt(Constants.SHARED_PREF_SEND_TO_HEALTH_CONNECT_INTERVAL, 5)
                }
            }
        }
    }


    private fun migrateWearSettings(
        context: Context,
        sharedPref: SharedPreferences,
        oldVersion: Int,
        isUpgrade: Boolean
    ) {
        // notification to vibrate_only
        if(!sharedPref.contains(Constants.SHARED_PREF_NOTIFICATION_VIBRATE) && sharedPref.contains("notification")) {
            sharedPref.edit {
                putBoolean(
                    Constants.SHARED_PREF_NOTIFICATION_VIBRATE,
                    sharedPref.getBoolean("notification", false)
                )
            }
        }
        // complications
        if(!sharedPref.contains(Constants.SHARED_PREF_COMPLICATION_TAP_ACTION)) {
            val curApp = context.packageName
            Log.i(LOG_ID, "Setting default tap action for complications to $curApp")
            sharedPref.edit {
                putString(Constants.SHARED_PREF_COMPLICATION_TAP_ACTION, curApp)
            }
        }

        // graph settings
        if(sharedPref.contains(Constants.DEPRECATED_SHARED_PREF_GRAPH_DURATION_WEAR_COMPLICATION) || !sharedPref.contains(Constants.SHARED_PREF_GRAPH_BITMAP_DURATION)) {
            val isScreenReader = context.isScreenReaderOn()
            val oldDuration = if(isScreenReader) 0 else sharedPref.getInt(Constants.DEPRECATED_SHARED_PREF_GRAPH_DURATION_WEAR_COMPLICATION, 2)
            Log.i(LOG_ID, "Setting default duration for graph - screenReader: $isScreenReader - oldDuration: $oldDuration")
            sharedPref.edit {
                putInt(Constants.SHARED_PREF_GRAPH_BITMAP_DURATION, oldDuration)
                remove(Constants.DEPRECATED_SHARED_PREF_GRAPH_DURATION_WEAR_COMPLICATION)
            }
        }
    }

    private fun migrateExtraSettings(context: Context, sharedPref: SharedPreferences) {
        val sharedExtraPref = context.getSharedPreferences(Constants.SHARED_PREF_EXTRAS_TAG, MODE_PRIVATE)

        // LibreLinkUp
        if(sharedPref.contains(Constants.SHARED_PREF_LIBRE_TOKEN)) {
            sharedExtraPref.edit {
                putString(Constants.SHARED_PREF_LIBRE_TOKEN, sharedPref.getString(Constants.SHARED_PREF_LIBRE_TOKEN, ""))
                putString(Constants.SHARED_PREF_LIBRE_USER_ID, sharedPref.getString(Constants.SHARED_PREF_LIBRE_USER_ID, ""))
                putLong(Constants.SHARED_PREF_LIBRE_TOKEN_EXPIRE, sharedPref.getLong(Constants.SHARED_PREF_LIBRE_TOKEN_EXPIRE, 0))
                putString(Constants.SHARED_PREF_LIBRE_REGION, sharedPref.getString(Constants.SHARED_PREF_LIBRE_REGION, ""))
            }
        }
        sharedPref.edit {
            remove(Constants.SHARED_PREF_LIBRE_TOKEN)
            remove(Constants.SHARED_PREF_LIBRE_USER_ID)
            remove(Constants.SHARED_PREF_LIBRE_TOKEN_EXPIRE)
            remove(Constants.SHARED_PREF_LIBRE_REGION)
        }

        // Medtrum
        if(sharedPref.contains(Constants.SHARED_PREF_MEDTRUM_COOKIE)) {
            sharedExtraPref.edit {
                putString(Constants.SHARED_PREF_MEDTRUM_COOKIE, sharedPref.getString(Constants.SHARED_PREF_MEDTRUM_COOKIE, ""))
            }
            sharedPref.edit {
                remove(Constants.SHARED_PREF_MEDTRUM_COOKIE)
            }
        }
    }



}