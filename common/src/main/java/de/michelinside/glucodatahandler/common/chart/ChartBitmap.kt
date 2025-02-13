package de.michelinside.glucodatahandler.common.chart

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.view.View

class ChartBitmap(val context: Context, durationPref: String = "", width: Int = 1000, height: Int = 0, forComplication: Boolean = false, showAxisPref: String? = null, labelColor: Int = 0) {

    private val LOG_ID = "GDH.Chart.Bitmap"

    private var chartViewer: ChartBitmapCreator
    private var chart: GlucoseChart = GlucoseChart(context)
    val enabled: Boolean get() {
        return chartViewer.enabled
    }

    init {
        val viewHeight = if(height > 0) height else width/3
        Log.v(LOG_ID, "init - width: $width - height: $viewHeight - durationPref: $durationPref")
        chart.measure (View.MeasureSpec.makeMeasureSpec (width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec (viewHeight, View.MeasureSpec.EXACTLY))
        chart.layout (0, 0, chart.getMeasuredWidth(), chart.getMeasuredHeight())

        chartViewer = ChartBitmapCreator(chart, context, durationPref, forComplication, showAxisPref)
        chartViewer.labelColor = labelColor
        chartViewer.create(true)
    }

    fun close() {
        Log.v(LOG_ID, "close")
        chartViewer.close()
    }

    fun getBitmap(): Bitmap? {
        return chartViewer.getBitmap()
    }

    val chartId: Int get() = chart.id

}