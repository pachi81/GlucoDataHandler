package de.michelinside.glucodatahandler.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import de.michelinside.glucodatahandler.GlucoDataServiceMobile
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.R as CR
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.utils.Utils
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.BitmapUtils
import de.michelinside.glucodatahandler.common.utils.PackageUtils


enum class WidgetType(val cls: Class<*>) {
    GLUCOSE(GlucoseWidget::class.java),
    GLUCOSE_TREND(GlucoseTrendWidget::class.java),
    GLUCOSE_TREND_DELTA(GlucoseTrendDeltaWidget::class.java),
    GLUCOSE_TREND_DELTA_TIME(GlucoseTrendDeltaTimeWidget::class.java),
    GLUCOSE_TREND_DELTA_TIME_IOB_COB(GlucoseTrendDeltaTimeIobCobWidget::class.java),
    CHART_GLUCOSE_TREND_DELTA_TIME_IOB_COB(ChartWidget::class.java),
    OTHER_UNIT(OtherUnitWidget::class.java);
}

abstract class GlucoseBaseWidget(private val type: WidgetType,
                                 private val hasTrend: Boolean = false,
                                 private val hasDelta: Boolean = false,
                                 private val hasTime: Boolean = false,
                                 private val hasIobCob: Boolean = false,
                                 private val hasOtherUnit: Boolean = false,
                                 private val hasGraph: Boolean = false): AppWidgetProvider(), NotifierInterface {
    init {
        Log.d(LOG_ID, "init called for "+ this.toString())
    }

    companion object {
        private const val LOG_ID = "GDH.widget.GlucoseBaseWidget"

        protected fun getCurrentWidgetIds(context: Context, type: WidgetType): IntArray {
            val component = ComponentName(
                context,
                type.cls
            )
            with(AppWidgetManager.getInstance(context)) {
                return getAppWidgetIds(component)
            }
        }

        fun updateWidgets(context: Context) {
            try {
                enumValues<WidgetType>().forEach {
                    updateWidgets(context, it)
                }
            } catch (exc: Exception) {
                Log.e(LOG_ID, "Exception in updateWidgets: " + exc.message.toString())
            }
        }
        fun updateWidgets(context: Context, type: WidgetType) {
            try {
                val appWidgetIds = getCurrentWidgetIds(context, type)
                if (appWidgetIds.isNotEmpty()) {
                    Log.i(LOG_ID, "Trigger update of " + appWidgetIds.size + " widget(s) with type " + type.toString())
                    val intent = Intent(context, type.cls)
                    intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                    context.sendBroadcast(intent)
                }
            } catch (exc: Exception) {
                Log.e(LOG_ID, "Exception in updateWidgets: " + exc.message.toString())
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        try {
            // There may be multiple widgets active, so update all of them
            Log.d(LOG_ID, "onUpdate called for " + this.toString() + " - ids: " + appWidgetIds.contentToString())
            onEnabled(context)
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
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
            GlucoDataServiceMobile.start(context)
            ActiveWidgetHandler.addWidget(type, context)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Exception in onEnabled: " + exc.message.toString())
        }
    }

    override fun onDisabled(context: Context) {
        try {
            // Enter relevant functionality for when the last widget is disabled
            Log.d(LOG_ID, "onDisabled calledd for " + this.toString())
            ActiveWidgetHandler.remWidget(type, context)
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
            updateAppWidget(context!!, appWidgetManager!!, appWidgetId)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Exception in onAppWidgetOptionsChanged: " + exc.message.toString())
        }
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            Log.d(LOG_ID, "OnNotifyData for source " + dataSource.toString())
            val component = ComponentName(context, type.cls)
            with(AppWidgetManager.getInstance(context)) {
                val appWidgetIds = getAppWidgetIds(component)
                onUpdate(context, this, appWidgetIds )
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Exception in OnNotifyData: " + exc.message.toString())
        }
    }

    abstract fun getLayout(): Int

    open fun getShortLayout(): Int {
        return getLayout()
    }

    open fun getLongLayout(): Int {
        return getLayout()
    }

    protected open fun isShortWidget(width: Int, height: Int): Boolean {
        val ratio = width.toFloat() / height.toFloat()
        return (width <= 110 || ratio < 0.5F)
    }

    protected open fun isLongWidget(width: Int, height: Int): Boolean {
        val ratio = width.toFloat() / height.toFloat()
        return ratio > 2.8F
    }

    private fun getRemoteViews(context: Context, width: Int, height: Int): RemoteViews {
        val ratio = width.toFloat() / height.toFloat()
        Log.d(LOG_ID, "Create remote views for " + type.toString() + " with width/height=" + width + "/" + height + " and ratio=" + ratio)
        val shortWidget = isShortWidget(width, height)
        val longWidget = isLongWidget(width, height)

        val layout = if (shortWidget) getShortLayout() else if (longWidget) getLongLayout() else getLayout()
        val remoteViews = RemoteViews(context.packageName, layout)

        val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
        remoteViews.setInt(R.id.widget, "setBackgroundColor", Utils.getBackgroundColor(sharedPref.getInt(Constants.SHARED_PREF_WIDGET_TRANSPARENCY, 3)))

        if (!hasTrend || !shortWidget) {
            // short widget with trend, using the glucose+trend image
            if(hasOtherUnit)
                remoteViews.setTextViewText(R.id.glucose, ReceiveData.getGlucoseAsOtherUnit())
            else
                remoteViews.setTextViewText(R.id.glucose, ReceiveData.getGlucoseAsString())
            remoteViews.setTextColor(R.id.glucose, ReceiveData.getGlucoseColor())
            if (ReceiveData.isObsoleteShort() && !ReceiveData.isObsoleteLong()) {
                remoteViews.setInt(R.id.glucose, "setPaintFlags", Paint.STRIKE_THRU_TEXT_FLAG)
            } else {
                remoteViews.setInt(R.id.glucose, "setPaintFlags", 0)
            }
        }

        if (hasTrend) {
            if (shortWidget) {
                remoteViews.setImageViewBitmap(R.id.glucose_trend, BitmapUtils.getGlucoseTrendBitmap(width = width, height = width))
                remoteViews.setContentDescription(R.id.glucose_trend, ReceiveData.getAsText(context))
            } else {
                val size = minOf(500, maxOf(width, height))
                remoteViews.setImageViewBitmap(
                    R.id.trendImage, BitmapUtils.getRateAsBitmap(
                        width = size,
                        height = size
                    )
                )
                remoteViews.setContentDescription(R.id.trendImage, ReceiveData.getRateAsText(context))
            }
        }

        if (hasTime) {
            remoteViews.setTextViewText(R.id.timeText, "🕒 " + ReceiveData.getElapsedTimeMinuteAsString(context))
            remoteViews.setContentDescription(R.id.timeText, ReceiveData.getElapsedTimeMinuteAsString(context))
        }
        if (hasDelta) {
            if(hasOtherUnit) {
                if(!shortWidget)
                    remoteViews.setTextViewText(R.id.deltaText, "Δ " + ReceiveData.getDeltaAsOtherUnit())
            }
            else
                remoteViews.setTextViewText(R.id.deltaText, "Δ " + ReceiveData.getDeltaAsString())
        }

        if (hasOtherUnit)
            remoteViews.setTextViewText(R.id.unitText,  ReceiveData.getOtherUnit())

        if (hasIobCob) {
            remoteViews.setTextViewText(R.id.iobText, "💉 " + ReceiveData.getIobAsString())
            remoteViews.setContentDescription(R.id.iobText, context.getString(CR.string.info_label_iob) + " " + ReceiveData.getIobAsString())
            remoteViews.setTextViewText(R.id.cobText, "🍔 " + ReceiveData.getCobAsString())
            remoteViews.setContentDescription(R.id.cobText, context.getString(CR.string.info_label_cob) + " " + ReceiveData.getCobAsString())
            if(ReceiveData.cob.isNaN())
                remoteViews.setViewVisibility(R.id.cobText, View.GONE)
            else
                remoteViews.setViewVisibility(R.id.cobText, View.VISIBLE)
            if(hasGraph) {  // special case for graph widget
                if(ReceiveData.iob.isNaN())
                    remoteViews.setViewVisibility(R.id.iobText, View.GONE)
                else
                    remoteViews.setViewVisibility(R.id.iobText, View.VISIBLE)
                if(ReceiveData.cob.isNaN() && ReceiveData.iob.isNaN())
                    remoteViews.setViewVisibility(R.id.layout_iob_cob, View.GONE)
                else
                    remoteViews.setViewVisibility(R.id.layout_iob_cob, View.VISIBLE)
            }
        }
        if(hasGraph) {
            // update graph
            remoteViews.setImageViewBitmap(R.id.graphImage, ActiveWidgetHandler.chart)
        }

        return remoteViews
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        try {
            // Construct the RemoteViews object
            Log.d(LOG_ID, "updateAppWidget called for ID " + appWidgetId.toString())
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            // portrait
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            // landscape
            val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)

            //Log.d(LOG_ID, "Portrait width/height=" + minWidth + "/" + maxHeight + " - landscape: " + maxWidth + "/" + minHeight )

            val isPortrait = true   // todo: check for portrait mode for tablets!?

            val width = if (isPortrait) minWidth else maxWidth
            val height = if (isPortrait) maxHeight else minHeight

            val remoteViews = getRemoteViews(context, width, height)
            val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            remoteViews.setOnClickPendingIntent(R.id.widget, PackageUtils.getTapActionIntent(context, sharedPref.getString(Constants.SHARED_PREF_WIDGET_TAP_ACTION, null), appWidgetId))

            // Instruct the widget manager to update the widget
            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Exception in updateAppWidget: " + exc.message.toString())
        }
    }
}