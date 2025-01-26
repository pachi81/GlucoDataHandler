package de.michelinside.glucodatahandler.common.chart

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.database.dbAccess
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifySource

class ChartBitmapCreator(chart: GlucoseChart, context: Context): ChartCreator(chart, context) {
    private val LOG_ID = "GDH.Chart.BitmapCreator"
    private var bitmap: Bitmap? = null
    override val resetChart = true

    override fun initXaxis() {
        Log.v(LOG_ID, "initXaxis")
        chart.xAxis.isEnabled = false
    }

    override fun initYaxis() {
        Log.v(LOG_ID, "initYaxis")
        chart.axisRight.setDrawAxisLine(false)
        chart.axisRight.setDrawLabels(false)
        chart.axisRight.setDrawZeroLine(false)
        chart.axisRight.setDrawGridLines(false)
        chart.axisLeft.isEnabled = false
    }

    override fun initChart(touchEnabled: Boolean) {
        super.initChart(false)
        chart.isDrawingCacheEnabled = false
    }

    override fun getDefaultRange(): Long {
        return 120L
    }

    override fun getMaxRange(): Long {
        return getDefaultRange()
    }

    override fun updateChart(dataSet: LineDataSet) {
        Log.v(LOG_ID, "Update chart for ${dataSet.values.size} entries and ${dataSet.circleColors.size} colors")
        if(dataSet.values.isNotEmpty())
            chart.data = LineData(dataSet)
        else
            chart.data = LineData()
        addEmptyTimeData()
        chart.notifyDataSetChanged()
        chart.invalidate()
        chart.waitForInvalidate()
        bitmap = createBitmap()
        InternalNotifier.notify(context, NotifySource.GRAPH_CHANGED, Bundle().apply { putInt(Constants.GRAPH_ID, chart.id) })
    }

    override fun updateTimeElapsed() {
        update(dbAccess.getGlucoseValues(getMinTime()))
    }

    fun getBitmap(): Bitmap? {
        if(bitmap == null)
            bitmap = createBitmap()
        return bitmap
    }
}