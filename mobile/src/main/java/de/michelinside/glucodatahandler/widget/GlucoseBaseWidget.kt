package de.michelinside.glucodatahandler.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import de.michelinside.glucodatahandler.BuildConfig
import de.michelinside.glucodatahandler.MainActivity
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.Utils
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifyDataSource
import java.text.DateFormat
import java.util.*


enum class WidgetType {
    GLUCOSE,
    GLUCOSE_TREND,
    GLUCOSE_TREND_DELTA,
    GLUCOSE_TREND_DELTA_TIME;
}

open class GlucoseBaseWidget(private val type: WidgetType): AppWidgetProvider(), NotifierInterface {
    val shortTimeformat: DateFormat = DateFormat.getTimeInstance(DateFormat.SHORT)
    protected var init = false

    companion object {
        private val LOG_ID = "GlucoDataHandler.GlucoseBaseWidget"

        private fun getWidgetClass(type: WidgetType): Class<*> {
            return when(type) {
                WidgetType.GLUCOSE -> GlucoseWidget::class.java
                WidgetType.GLUCOSE_TREND -> GlucoseTrendWidget::class.java
                WidgetType.GLUCOSE_TREND_DELTA -> GlucoseTrendDeltaWidget::class.java
                WidgetType.GLUCOSE_TREND_DELTA_TIME -> GlucoseTrendDeltaTimeWidget::class.java
            }
        }

        fun updateWidgets(context: Context) {
            enumValues<WidgetType>().forEach {
                val widgetClass = getWidgetClass(it)
                val component = ComponentName(
                    context,
                    widgetClass
                )
                with(AppWidgetManager.getInstance(context)) {
                    val appWidgetIds = getAppWidgetIds(component)
                    if (appWidgetIds.isNotEmpty()) {
                        Log.i(LOG_ID, "Trigger update of " + appWidgetIds.size + " widget(s)")
                        val intent = Intent(context, widgetClass)
                        intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                        context.sendBroadcast(intent)
                    }
                }
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // There may be multiple widgets active, so update all of them
        Log.d(LOG_ID, "onUpdate called for " + appWidgetIds.size.toString() + " widgets")
        if (!init)
            onEnabled(context)
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        // Enter relevant functionality for when the first widget is created
        if (!init) {
            Log.d(LOG_ID, "onEnabled called")
            val filter = mutableSetOf(
                NotifyDataSource.BROADCAST,
                NotifyDataSource.MESSAGECLIENT,
                NotifyDataSource.SETTINGS,
                NotifyDataSource.OBSOLETE_VALUE
            )   // to trigger re-start for the case of stopped by the system
            InternalNotifier.addNotifier(this, filter)
            init = true
        }
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
        Log.d(LOG_ID, "onDisabled called")
        InternalNotifier.remNotifier(this)
        init = false
    }

    override fun onAppWidgetOptionsChanged(
        context: Context?,
        appWidgetManager: AppWidgetManager?,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        Log.d(LOG_ID, "onAppWidgetOptionsChanged called for ID " + appWidgetId.toString())
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateAppWidget(context!!, appWidgetManager!!, appWidgetId)
    }

    override fun OnNotifyData(context: Context, dataSource: NotifyDataSource, extras: Bundle?) {
        Log.d(LOG_ID, "OnNotifyData for source " + dataSource.toString())
        val component = ComponentName(context,
            getWidgetClass(type))
        with(AppWidgetManager.getInstance(context)) {
            val appWidgetIds = getAppWidgetIds(component)
            onUpdate(context, this, appWidgetIds )
        }
    }

    private fun hasTrend(): Boolean {
        return when(type) {
            WidgetType.GLUCOSE_TREND,
            WidgetType.GLUCOSE_TREND_DELTA,
            WidgetType.GLUCOSE_TREND_DELTA_TIME -> true
            else -> false
        }
    }
    private fun hasDelta(): Boolean {
        return when(type) {
            WidgetType.GLUCOSE_TREND_DELTA,
            WidgetType.GLUCOSE_TREND_DELTA_TIME -> true
            else -> false
        }
    }
    private fun hasTime(): Boolean {
        return when(type) {
            WidgetType.GLUCOSE_TREND_DELTA_TIME -> true
            else -> false
        }
    }

    private fun getRemoteViews(context: Context, width: Int, height: Int): RemoteViews {
        val ratio = width.toFloat() / height.toFloat()
        Log.d(LOG_ID, "Create remote views for " + type.toString() + " with width/height=" + width + "/" + height + " and ratio=" + ratio)
        val shortWidget = (width <= 110 || ratio < 0.5F)
        val longWidget = ratio > 2.8F
        val size = maxOf(width, height)
        val remoteViews = when(type) {
            WidgetType.GLUCOSE -> {
                RemoteViews(context.packageName, R.layout.glucose_widget)
            }
            WidgetType.GLUCOSE_TREND -> {
                if (shortWidget) {
                    RemoteViews(context.packageName, R.layout.glucose_trend_widget_short)
                } else {
                    RemoteViews(context.packageName, R.layout.glucose_trend_widget)
                }
            }
            WidgetType.GLUCOSE_TREND_DELTA,
            WidgetType.GLUCOSE_TREND_DELTA_TIME -> {
                if (shortWidget) {
                    RemoteViews(context.packageName, if (type == WidgetType.GLUCOSE_TREND_DELTA_TIME) R.layout.glucose_trend_delta_time_widget_short else R.layout.glucose_trend_delta_widget_short)
                } else if (longWidget) {
                    RemoteViews(context.packageName, if (type == WidgetType.GLUCOSE_TREND_DELTA_TIME) R.layout.glucose_trend_delta_time_widget_long else R.layout.glucose_trend_delta_widget_long)
                } else {
                    RemoteViews(context.packageName, if (type == WidgetType.GLUCOSE_TREND_DELTA_TIME) R.layout.glucose_trend_delta_time_widget else R.layout.glucose_trend_delta_widget)
                }
            }
        }

        if (!hasTrend() || !shortWidget) {
            remoteViews.setTextViewText(R.id.glucose, ReceiveData.getClucoseAsString())
            remoteViews.setTextColor(R.id.glucose, ReceiveData.getClucoseColor())
            if (ReceiveData.isObsolete(Constants.VALUE_OBSOLETE_SHORT_SEC) && !ReceiveData.isObsolete()) {
                remoteViews.setInt(R.id.glucose, "setPaintFlags", Paint.STRIKE_THRU_TEXT_FLAG)
            } else {
                remoteViews.setInt(R.id.glucose, "setPaintFlags", 0)
            }
        }

        if (hasTrend()) {
            if (shortWidget)
                remoteViews.setImageViewBitmap(R.id.glucose_trend, Utils.getGlucoseTrendBitmap(width = width, height = width))
            else
                remoteViews.setImageViewBitmap(R.id.trendImage, Utils.getRateAsBitmap(roundTarget = false, width = size, height = size))
        }

        if (hasTime()) {
            remoteViews.setTextViewText(R.id.timeText, shortTimeformat.format(Date(ReceiveData.time)))
        }
        if (hasDelta())
            remoteViews.setTextViewText(R.id.deltaText, ReceiveData.getDeltaAsString())
        return remoteViews
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
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
        if (BuildConfig.DEBUG) {
            // for debug create dummy broadcast (to check in emulator)
            val pendingIntent = PendingIntent.getBroadcast(context, 5, Utils.getDummyGlucodataIntent(false), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            remoteViews.setOnClickPendingIntent(R.id.widget, pendingIntent)
        } else {
            remoteViews.setOnClickPendingIntent(R.id.widget, Utils.getAppIntent(context, MainActivity::class.java, 5))
        }

        // Instruct the widget manager to update the widget
        appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
    }
}