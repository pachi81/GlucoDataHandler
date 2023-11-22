package de.michelinside.glucodatahandler.widget

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource

object ActiveWidgetHandler: NotifierInterface, SharedPreferences.OnSharedPreferenceChangeListener {
    private const val LOG_ID = "GDH.widget.ActiveWidgetHandler"
    private var activeWidgets = mutableSetOf<WidgetType>()

    init {
        Log.d(LOG_ID, "init called")
    }

    fun addWidget(type: WidgetType, context: Context) {
        try {
            if(!activeWidgets.contains(type)) {
                activeWidgets.add(type)
                Log.i(LOG_ID, "add widget " + type.toString() + " new size " + activeWidgets.size)
                if (activeWidgets.size == 1 || type == WidgetType.GLUCOSE_TREND_DELTA_TIME) {
                    val filter = mutableSetOf(
                        NotifySource.BROADCAST,
                        NotifySource.MESSAGECLIENT,
                        NotifySource.SETTINGS,
                    )   // to trigger re-start for the case of stopped by the system
                    if(activeWidgets.contains(WidgetType.GLUCOSE_TREND_DELTA_TIME))
                        filter.add(NotifySource.TIME_VALUE)
                    else
                        filter.add(NotifySource.OBSOLETE_VALUE)
                    InternalNotifier.addNotifier(this, filter)
                }
                if (activeWidgets.size == 1)
                    context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(this)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Exception in addWidget: " + exc.toString())
        }
    }

    fun remWidget(type: WidgetType, context: Context) {
        try {
            activeWidgets.remove(type)
            Log.i(LOG_ID, "remove widget " + type.toString() + " new size " + activeWidgets.size)
            if (activeWidgets.isEmpty()) {
                InternalNotifier.remNotifier(this)
                context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(this)
            } else if (type == WidgetType.GLUCOSE_TREND_DELTA_TIME) {
                // re-add filter to remove TIME_VALUE
                val filter = mutableSetOf(
                    NotifySource.BROADCAST,
                    NotifySource.MESSAGECLIENT,
                    NotifySource.SETTINGS,
                    NotifySource.OBSOLETE_VALUE
                )
                InternalNotifier.addNotifier(this, filter)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Exception in remWidget: " + exc.toString())
        }
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        Log.d(LOG_ID, "OnNotifyData for source " + dataSource.toString())
        activeWidgets.forEach {
            if (dataSource != NotifySource.TIME_VALUE || it == WidgetType.GLUCOSE_TREND_DELTA_TIME)
                GlucoseBaseWidget.updateWidgets(context, it)
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        try {
            Log.d(LOG_ID, "onSharedPreferenceChanged called for key " + key)
            if (GlucoDataService.context != null) {
                when (key) {
                    Constants.SHARED_PREF_WIDGET_TRANSPARENCY -> {
                        OnNotifyData(GlucoDataService.context!!, NotifySource.SETTINGS, null)
                    }
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString() )
        }
    }
}