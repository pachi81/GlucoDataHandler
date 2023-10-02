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
import de.michelinside.glucodatahandler.GlucoDataServiceMobile
import de.michelinside.glucodatahandler.MainActivity
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.Utils
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifyDataSource


enum class WidgetType(val cls: Class<*>) {
    GLUCOSE(GlucoseWidget::class.java),
    GLUCOSE_TREND(GlucoseTrendWidget::class.java),
    GLUCOSE_TREND_DELTA(GlucoseTrendDeltaWidget::class.java),
    GLUCOSE_TREND_DELTA_TIME(GlucoseTrendDeltaTimeWidget::class.java);
}

abstract class GlucoseBaseWidget(private val type: WidgetType,
                                 private val hasTrend: Boolean = false,
                                 private val hasDelta: Boolean = false,
                                 private val hasTime: Boolean = false): AppWidgetProvider(), NotifierInterface {
    init {
        Log.d(LOG_ID, "init called for "+ this.toString())
    }

    companion object {
        private const val LOG_ID = "GlucoDataHandler.widget.GlucoseBaseWidget"

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
            enumValues<WidgetType>().forEach {
                updateWidgets(context, it)
            }
        }
        fun updateWidgets(context: Context, type: WidgetType) {
            val appWidgetIds = getCurrentWidgetIds(context, type)
            if (appWidgetIds.isNotEmpty()) {
                Log.i(LOG_ID, "Trigger update of " + appWidgetIds.size + " widget(s) with type " + type.toString())
                val intent = Intent(context, type.cls)
                intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                context.sendBroadcast(intent)
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // There may be multiple widgets active, so update all of them
        Log.d(LOG_ID, "onUpdate called for " + this.toString() + " - ids: " + appWidgetIds.contentToString())
        onEnabled(context)
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onDeleted(context: Context?, appWidgetIds: IntArray?) {
        Log.i(LOG_ID, "onDeleted called for " + this.toString() + " - ids: " + appWidgetIds?.contentToString() )
        super.onDeleted(context, appWidgetIds)
    }

    override fun onRestored(context: Context?, oldWidgetIds: IntArray?, newWidgetIds: IntArray?) {
        Log.i(LOG_ID, "onRestored called for " + this.toString() + " - old ids: " + oldWidgetIds?.contentToString() + " - new ids: " + newWidgetIds?.contentToString())
        super.onRestored(context, oldWidgetIds, newWidgetIds)
    }

    override fun onEnabled(context: Context) {
        // Enter relevant functionality for when the first widget is created
        Log.d(LOG_ID, "onEnabled called for " + this.toString())
        GlucoDataServiceMobile.start(context)
        ActiveWidgetHandler.addWidget(type)
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
        Log.d(LOG_ID, "onDisabled calledd for " + this.toString())
        ActiveWidgetHandler.remWidget(type)
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
        val component = ComponentName(context, type.cls)
        with(AppWidgetManager.getInstance(context)) {
            val appWidgetIds = getAppWidgetIds(component)
            onUpdate(context, this, appWidgetIds )
        }
    }

    abstract fun getLayout(): Int

    open fun getShortLayout(): Int {
        return getLayout()
    }

    open fun getLongLayout(): Int {
        return getLayout()
    }

    private fun getRemoteViews(context: Context, width: Int, height: Int): RemoteViews {
        val ratio = width.toFloat() / height.toFloat()
        Log.d(LOG_ID, "Create remote views for " + type.toString() + " with width/height=" + width + "/" + height + " and ratio=" + ratio)
        val shortWidget = (width <= 110 || ratio < 0.5F)
        val longWidget = ratio > 2.8F
        val size = maxOf(width, height)

        val layout = if (shortWidget) getShortLayout() else if (longWidget) getLongLayout() else getLayout()
        val remoteViews = RemoteViews(context.packageName, layout)

        if (!hasTrend || !shortWidget) {
            // short widget with trend, using the glucose+trend image
            remoteViews.setTextViewText(R.id.glucose, ReceiveData.getClucoseAsString())
            remoteViews.setTextColor(R.id.glucose, ReceiveData.getClucoseColor())
            if (ReceiveData.isObsolete(Constants.VALUE_OBSOLETE_SHORT_SEC) && !ReceiveData.isObsolete()) {
                remoteViews.setInt(R.id.glucose, "setPaintFlags", Paint.STRIKE_THRU_TEXT_FLAG)
            } else {
                remoteViews.setInt(R.id.glucose, "setPaintFlags", 0)
            }
        }

        if (hasTrend) {
            if (shortWidget)
                remoteViews.setImageViewBitmap(R.id.glucose_trend, Utils.getGlucoseTrendBitmap(width = width, height = width))
            else
                remoteViews.setImageViewBitmap(R.id.trendImage, Utils.getRateAsBitmap(roundTarget = false, width = size, height = size))
        }

        if (hasTime) {
            remoteViews.setTextViewText(R.id.timeText, ReceiveData.getElapsedTimeMinuteAsString(context))
        }
        if (hasDelta)
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
            remoteViews.setOnClickPendingIntent(R.id.widget, Utils.getAppIntent(context, MainActivity::class.java, 5, true))
        }

        // Instruct the widget manager to update the widget
        appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
    }
}