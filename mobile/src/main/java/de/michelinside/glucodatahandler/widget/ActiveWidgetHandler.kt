package de.michelinside.glucodatahandler.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.chart.ChartBitmap
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.Utils

object ActiveWidgetHandler: NotifierInterface, SharedPreferences.OnSharedPreferenceChangeListener {
    private const val LOG_ID = "GDH.widget.ActiveWidgetHandler"
    private var activeWidgets = mutableSetOf<WidgetType>()
    @SuppressLint("StaticFieldLeak")
    private var chartBitmap: ChartBitmap? = null
    val chart: Bitmap? get() {
        return chartBitmap?.getBitmap()
    }

    init {
        Log.d(LOG_ID, "init called")
    }

    private fun getFilter(): MutableSet<NotifySource> {
        val filter = mutableSetOf(
            NotifySource.BROADCAST,
            NotifySource.MESSAGECLIENT,
            NotifySource.SETTINGS,
            NotifySource.OBSOLETE_VALUE,
        )   // to trigger re-start for the case of stopped by the system
        if(activeWidgets.contains(WidgetType.GLUCOSE_TREND_DELTA_TIME) || activeWidgets.contains(WidgetType.GLUCOSE_TREND_DELTA_TIME_IOB_COB))
            filter.add(NotifySource.TIME_VALUE)
        if(activeWidgets.contains(WidgetType.GLUCOSE_TREND_DELTA_TIME_IOB_COB) || activeWidgets.contains(WidgetType.CHART_GLUCOSE_TREND_DELTA_TIME_IOB_COB))
            filter.add(NotifySource.IOB_COB_CHANGE)
        if(activeWidgets.contains(WidgetType.CHART_GLUCOSE_TREND_DELTA_TIME_IOB_COB))
            filter.add(NotifySource.GRAPH_CHANGED)
        return filter
    }

    fun addWidget(type: WidgetType, context: Context) {
        try {
            Log.d(LOG_ID, "addWidget called for type $type - isServiceRunning ${GlucoDataService.isServiceRunning}")
            if(GlucoDataService.isServiceRunning) {
                if(!activeWidgets.contains(type)) {
                    activeWidgets.add(type)
                    Log.i(LOG_ID, "add widget " + type.toString() + " new size " + activeWidgets.size)
                    if (activeWidgets.size == 1 || type == WidgetType.GLUCOSE_TREND_DELTA_TIME || type == WidgetType.GLUCOSE_TREND_DELTA_TIME_IOB_COB || type == WidgetType.CHART_GLUCOSE_TREND_DELTA_TIME_IOB_COB) {
                        InternalNotifier.addNotifier(context, this, getFilter())
                    }
                    if (activeWidgets.size == 1)
                        context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(this)
                    if(type == WidgetType.CHART_GLUCOSE_TREND_DELTA_TIME_IOB_COB) {
                        createBitmap(context)
                    }
                }
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
                InternalNotifier.remNotifier(context, this)
                context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(this)
            } else if (type == WidgetType.GLUCOSE_TREND_DELTA_TIME || type == WidgetType.GLUCOSE_TREND_DELTA_TIME_IOB_COB || type == WidgetType.CHART_GLUCOSE_TREND_DELTA_TIME_IOB_COB) {
                // re-add filter to remove widget specific source
                InternalNotifier.addNotifier(context, this, getFilter())
            }
            if(!activeWidgets.contains(WidgetType.CHART_GLUCOSE_TREND_DELTA_TIME_IOB_COB)) {
                removeBitmap()
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Exception in remWidget: " + exc.toString())
        }
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        Log.d(LOG_ID, "OnNotifyData for source $dataSource with extras ${Utils.dumpBundle(extras)} - graph-id ${chartBitmap?.chartId}")
        activeWidgets.forEach {
            if(it == WidgetType.CHART_GLUCOSE_TREND_DELTA_TIME_IOB_COB) {
                // do not update for glucose values, wait for graph has changed!
                if(dataSource == NotifySource.TIME_VALUE || dataSource == NotifySource.IOB_COB_CHANGE || dataSource == NotifySource.SETTINGS)
                    GlucoseBaseWidget.updateWidgets(context, it)
                else if(dataSource == NotifySource.GRAPH_CHANGED && chartBitmap != null && extras?.getInt(Constants.GRAPH_ID) == chartBitmap!!.chartId)
                    GlucoseBaseWidget.updateWidgets(context, it)
            } else if (it == WidgetType.GLUCOSE_TREND_DELTA_TIME || it == WidgetType.GLUCOSE_TREND_DELTA_TIME_IOB_COB) {
                if(dataSource != NotifySource.OBSOLETE_VALUE)  // do not update again, as there are already updated by TIME_VALUE
                    GlucoseBaseWidget.updateWidgets(context, it)
            } else if (dataSource != NotifySource.TIME_VALUE) {
                GlucoseBaseWidget.updateWidgets(context, it)
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        try {
            Log.d(LOG_ID, "onSharedPreferenceChanged called for key " + key)
            if (GlucoDataService.context != null) {
                when (key) {
                    Constants.SHARED_PREF_WIDGET_TRANSPARENCY,
                    Constants.SHARED_PREF_WIDGET_TAP_ACTION -> {
                        OnNotifyData(GlucoDataService.context!!, NotifySource.SETTINGS, null)
                    }
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString() )
        }
    }

    private fun createBitmap(context: Context) {
        if(chartBitmap == null && GlucoDataService.isServiceRunning) {
            Log.i(LOG_ID, "Create bitmap")
            chartBitmap = ChartBitmap(context, Constants.SHARED_PREF_GRAPH_DURATION_PHONE_WIDGET, showAxisPref = Constants.SHARED_PREF_GRAPH_SHOW_AXIS_PHONE_WIDGET, labelColor = Color.WHITE)
        }
    }

    private fun removeBitmap() {
        if(chartBitmap != null) {
            Log.i(LOG_ID, "Remove bitmap")
            chartBitmap!!.close()
            chartBitmap = null
        }
    }

}