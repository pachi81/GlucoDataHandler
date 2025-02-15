package de.michelinside.glucodatahandler.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.WearPhoneConnection
import de.michelinside.glucodatahandler.common.notification.AlarmType
import de.michelinside.glucodatahandler.common.utils.PackageUtils
import de.michelinside.glucodatahandler.common.utils.Utils
import de.michelinside.glucodatahandler.common.R as CR

class BatteryLevelWidget : AppWidgetProvider() {
        private val LOG_ID = "GDH.widget.BatteryLevelWidget"

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            batteryLevel: Int,
            deviceName: String
        ) {
            try {
                Log.d(LOG_ID, "updateWidget called for " + this.toString() + " widget:$appWidgetId battery:$batteryLevel device:$deviceName" )

                val remoteViews = RemoteViews(context.packageName, R.layout.battery_level_widget)

                val batteryLevelStr = if (batteryLevel == 0) "?%" else "$batteryLevel%"
                remoteViews.setTextViewText(R.id.battery_level, batteryLevelStr)
                remoteViews.setTextViewText(R.id.device_name, deviceName)
                val levelColour = if(batteryLevel == 0)
                        ReceiveData.getAlarmTypeColor(AlarmType.NONE)
                    else if (batteryLevel < 25)
                        ReceiveData.getAlarmTypeColor(AlarmType.VERY_LOW)
                    else if (batteryLevel < 45)
                        ReceiveData.getAlarmTypeColor(AlarmType.LOW)
                    else
                        ReceiveData.getAlarmTypeColor(AlarmType.OK)
                remoteViews.setTextColor(R.id.battery_level, levelColour)

                val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
                remoteViews.setInt(R.id.widget, "setBackgroundColor", Utils.getBackgroundColor(sharedPref.getInt(Constants.SHARED_PREF_WIDGET_TRANSPARENCY, 3)))
                remoteViews.setOnClickPendingIntent(
                    R.id.widget,
                    PackageUtils.getTapActionIntent(
                        context,
                        sharedPref.getString(Constants.SHARED_PREF_WIDGET_TAP_ACTION, null),
                        appWidgetId
                    )
                )
                appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
            } catch (exc: Exception) {
                Log.e(LOG_ID, "updateWidget exception: $exc")
            }
        }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        try {
            Log.i(LOG_ID, "onUpdate called for " + this.toString() + " - ids: " + appWidgetIds.contentToString())

            BatteryLevelWidgetNotifier.addNotifier()

            for (appWidgetId in appWidgetIds) {
                var batteryLevel = 0
                var deviceName = context.getString(CR.string.activity_main_disconnected_label)


                if (WearPhoneConnection.nodesConnected && !WearPhoneConnection.connectionError) {
                    val connection = GlucoDataService.getWearPhoneConnection()
                    if (connection != null) {
                        val nodeId = connection.pickBestNodeId()
                        if (nodeId != null) {
                            WearPhoneConnection.getNodeBatteryLevel(nodeId).firstNotNullOf { (name, level) ->
                                if (level > 0)
                                    batteryLevel = level
                                deviceName = name
                            }
                        }
                    }
                }

                updateWidget(context, appWidgetManager, appWidgetId, batteryLevel, deviceName)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Exception in onUpdate: " + exc.message.toString())
        }
    }

    override fun onDeleted(context: Context?, appWidgetIds: IntArray?) {
        try {
            Log.i(LOG_ID, "onDeleted called for " + this.toString() + " - ids: " + appWidgetIds?.contentToString() )
            super.onDeleted(context, appWidgetIds)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Exception in onDeleted: " + exc.message.toString())
        }
    }

    override fun onRestored(context: Context?, oldWidgetIds: IntArray?, newWidgetIds: IntArray?) {
        try {
            Log.i(LOG_ID, "onRestored called for " + this.toString() + " - old ids: " + oldWidgetIds?.contentToString() + " - new ids: " + newWidgetIds?.contentToString())
            super.onRestored(context, oldWidgetIds, newWidgetIds)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Exception in onRestored: " + exc.message.toString())
        }
    }

    override fun onEnabled(context: Context) {
        try {
            // Enter relevant functionality for when the first widget is created
            Log.d(LOG_ID, "onEnabled called for " + this.toString())
            super.onEnabled(context)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Exception in onEnabled: " + exc.message.toString())
        }
    }

    override fun onDisabled(context: Context) {
        try {
            // Enter relevant functionality for when the last widget is disabled
            Log.d(LOG_ID, "onDisabled called for " + this.toString())
            BatteryLevelWidgetNotifier.removeNotifier()
            super.onDisabled(context)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Exception in onDisabled: " + exc.message.toString())
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context?,
        appWidgetManager: AppWidgetManager?,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        try {
            Log.d(LOG_ID, "onAppWidgetOptionsChanged called for ID " + appWidgetId.toString())
            super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Exception in onAppWidgetOptionsChanged: " + exc.message.toString())
        }
    }




}
