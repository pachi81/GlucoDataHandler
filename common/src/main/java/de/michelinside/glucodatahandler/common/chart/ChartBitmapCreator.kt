package de.michelinside.glucodatahandler.common.chart

import android.content.Context
import android.util.Log
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import de.michelinside.glucodatahandler.common.database.dbAccess

class ChartBitmapCreator(chart: GlucoseChart, context: Context): ChartCreator(chart, context) {
    private val LOG_ID = "GDH.Chart.BitmapCreator"

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
    }

    override fun updateTimeElapsed() {
        update(dbAccess.getGlucoseValues(getMinTime()))
    }
}