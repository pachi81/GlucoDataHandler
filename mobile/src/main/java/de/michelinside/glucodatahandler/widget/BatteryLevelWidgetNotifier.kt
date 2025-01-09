package de.michelinside.glucodatahandler.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import de.michelinside.glucodatahandler.common.GlucoDataService.Companion.context
import de.michelinside.glucodatahandler.common.R
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.receiver.BatteryReceiver
import de.michelinside.glucodatahandler.widget.GlucoseBaseWidget.Companion
import de.michelinside.glucodatahandler.widget.GlucoseBaseWidget.Companion.getCurrentWidgetIds

// Single notifier for battery level widgets as context changes in widget objects leading to memory leaks

object BatteryLevelWidgetNotifier: NotifierInterface {

    private const val LOG_ID = "GDH.widget.BatteryLevelWidgetNotifier"


    fun addNotifier() {
        try {
            Log.d(LOG_ID, "AddNotifier called for " +  this.toString())

            context?.let {
                val appWidgetManager = AppWidgetManager.getInstance(it)
                val componentName =
                    ComponentName(it.packageName, BatteryLevelWidget::class.java.name)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

                if (appWidgetIds.isEmpty()) {
                    Log.d(LOG_ID, "AddNotifier - Not adding notifier, no battery widgets")
                    return
                }

                if (!InternalNotifier.hasNotifier(this)) {
                    val filter = mutableSetOf(
                        NotifySource.CAPILITY_INFO,
                        NotifySource.NODE_BATTERY_LEVEL
                    )
                    InternalNotifier.addNotifier(it, this, filter)
                } else {
                    Log.d(LOG_ID, "AddNotifier already have notifier for " + this.toString())
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "RemoveNotifier exception: $exc")
        }
    }


    fun removeNotifier() {
        try {
            Log.d(LOG_ID, "RemoveNotifier called for " +  this.toString())
            context?.let {
                InternalNotifier.remNotifier(it, this)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "RemoveNotifier exception: $exc")
        }
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            Log.d(LOG_ID, "OnNotifyData called for source $dataSource ${extras?.toString()}")

            if (dataSource == NotifySource.NODE_BATTERY_LEVEL || dataSource == NotifySource.CAPILITY_INFO) {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName =
                    ComponentName(context.packageName, BatteryLevelWidget::class.java.name)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

                if (appWidgetIds.isNotEmpty()) {
                    Log.i(
                        LOG_ID,
                        "Trigger update of " + appWidgetIds.size + " widget(s) " + appWidgetIds.contentToString()
                    )
                    val intent = Intent(context, BatteryLevelWidget::class.java)
                    intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                    context.sendBroadcast(intent)
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: $exc")
        }
    }

}