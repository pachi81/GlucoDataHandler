package de.michelinside.glucodatahandler.widget

import android.content.Context
import android.os.Bundle
import android.util.Log
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifyDataSource

object ActiveWidgetHandler: NotifierInterface {
    private const val LOG_ID = "GlucoDataHandler.widget.ActiveWidgetHandler"
    private var activeWidgets = mutableSetOf<WidgetType>()

    init {
        Log.d(LOG_ID, "init called")
    }

    fun addWidget(type: WidgetType) {
        if(!activeWidgets.contains(type)) {
            activeWidgets.add(type)
            Log.i(LOG_ID, "add widget " + type.toString() + " new size " + activeWidgets.size)
            if (activeWidgets.size == 1 || type == WidgetType.GLUCOSE_TREND_DELTA_TIME) {
                val filter = mutableSetOf(
                    NotifyDataSource.BROADCAST,
                    NotifyDataSource.MESSAGECLIENT,
                    NotifyDataSource.SETTINGS,
                    NotifyDataSource.OBSOLETE_VALUE
                )   // to trigger re-start for the case of stopped by the system
                if(activeWidgets.contains(WidgetType.GLUCOSE_TREND_DELTA_TIME))
                    filter.add(NotifyDataSource.TIME_VALUE)
                InternalNotifier.addNotifier(this, filter)
            }
        }
    }

    fun remWidget(type: WidgetType) {
        activeWidgets.remove(type)
        Log.i(LOG_ID, "remove widget " + type.toString() + " new size " + activeWidgets.size)
        if (activeWidgets.isEmpty()) {
            InternalNotifier.remNotifier(this)
        } else if (type == WidgetType.GLUCOSE_TREND_DELTA_TIME) {
            // re-add filter to remove TIME_VALUE
            val filter = mutableSetOf(
                NotifyDataSource.BROADCAST,
                NotifyDataSource.MESSAGECLIENT,
                NotifyDataSource.SETTINGS,
                NotifyDataSource.OBSOLETE_VALUE
            )
            InternalNotifier.addNotifier(this, filter)
        }
    }

    override fun OnNotifyData(context: Context, dataSource: NotifyDataSource, extras: Bundle?) {
        Log.d(LOG_ID, "OnNotifyData for source " + dataSource.toString())
        activeWidgets.forEach {
            if (dataSource != NotifyDataSource.TIME_VALUE || it == WidgetType.GLUCOSE_TREND_DELTA_TIME)
                GlucoseBaseWidget.updateWidgets(context, it)
        }
    }
}