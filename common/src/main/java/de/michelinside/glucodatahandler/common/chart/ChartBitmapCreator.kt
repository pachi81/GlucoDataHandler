package de.michelinside.glucodatahandler.common.chart

import android.content.Context
import android.util.Log
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet

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

    override fun getMinTime(): Long {
        return System.currentTimeMillis() - (getDefaultRange()*60*1000L)
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

    private fun createBitmap() {
        Log.d(LOG_ID, "createBitmap")
        // reset bitmap and create it
        /*chart.fitScreen()
        chart.data?.clearValues()
        chart.data?.notifyDataChanged()
        chart.notifyDataSetChanged()*/
        resetData()
    }

    override fun update() {
        // update limit lines
        createBitmap()
    }

    override fun updateTimeElapsed() {
        update()
    }


}