package de.michelinside.glucodatahandler.common

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.graphics.Color
import androidx.core.content.edit
import de.michelinside.glucodatahandler.common.GlucoDataService.Companion.appSource
import de.michelinside.glucodatahandler.common.notification.AlarmType
import de.michelinside.glucodatahandler.common.notifier.DataSource
import de.michelinside.glucodatahandler.common.receiver.NotificationReceiver
import de.michelinside.glucodatahandler.common.tasks.LibreLinkSourceTask
import de.michelinside.glucodatahandler.common.utils.Log
import java.util.Locale

object SettingsMigrator {

    private val LOG_ID = "GDH.SettingsMigrator"

    fun migrateSettings(context: Context) {
        val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, MODE_PRIVATE)
        Log.v(LOG_ID, "migrateSettings called")

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
            if(isUpgrade && oldVersion == 156) {
                // for existing users use the old setting
                if(!sharedPref.contains(Constants.SHARED_PREF_STANDARD_STATISTICS)) {
                    sharedPref.edit {
                        putBoolean(Constants.SHARED_PREF_STANDARD_STATISTICS, false)
                    }
                }
            }
        }

        migrateExtraSettings(context, sharedPref)
    }  // migrateSettings

    private fun migrateExtraSettings(context: Context, sharedPref: android.content.SharedPreferences) {
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
            GlucoDataService.sharedExtraPref!!.edit {
                putString(Constants.SHARED_PREF_MEDTRUM_COOKIE, sharedPref.getString(Constants.SHARED_PREF_MEDTRUM_COOKIE, ""))
            }
            sharedPref.edit {
                remove(Constants.SHARED_PREF_MEDTRUM_COOKIE)
            }
        }

    }


}