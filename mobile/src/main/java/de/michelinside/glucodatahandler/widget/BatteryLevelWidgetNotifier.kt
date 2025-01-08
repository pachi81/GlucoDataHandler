package de.michelinside.glucodatahandler.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import de.michelinside.glucodatahandler.common.GlucoDataService.Companion.context
import de.michelinside.glucodatahandler.common.R
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.receiver.BatteryReceiver

// Single notifier for battery level widgets as context changes in widget objects leading to memory leaks

object BatteryLevelWidgetNotifier: NotifierInterface {

    private const val LOG_ID = "GDH.widget.BatteryLevelWidgetNotifier"

    fun AddNotifier() {
        try {
            Log.d(LOG_ID, "AddNotifier called for " +  this.toString())
            if (!InternalNotifier.hasNotifier(this)) {
                val filter = mutableSetOf(
                    NotifySource.CAPILITY_INFO,
                    NotifySource.NODE_BATTERY_LEVEL
                )
                InternalNotifier.addNotifier(context!!, this, filter)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "RemoveNotifier exception: $exc")
        }
    }


    fun RemoveNotifier() {
        try {
            Log.d(LOG_ID, "RemoveNotifier called for " +  this.toString())
            InternalNotifier.remNotifier(context!!, this)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "RemoveNotifier exception: $exc")
        }

    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            Log.d(LOG_ID, "OnNotifyData called for source $dataSource ${extras?.toString()}")

            if (dataSource == NotifySource.NODE_BATTERY_LEVEL) {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context.packageName, BatteryLevelWidget::class.java.name)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

                appWidgetIds.forEach { appWidgetId ->
                    BatteryLevelWidget.updateWidget(context, appWidgetManager, appWidgetId,
                        extras?.getInt(BatteryReceiver.LEVEL) ?: 0,
                        extras?.getString(BatteryReceiver.DEVICENAME) ?: context.getString(R.string.activity_main_disconnected_label)
                    )
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: $exc")
        }
    }

}