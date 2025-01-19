package de.michelinside.glucodatahandler.common.chart

import android.content.Context
import android.util.Log
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import de.michelinside.glucodatahandler.common.ReceiveData

class ChartBitmapCreator(chart: LineChart, context: Context): ChartViewer(chart, context) {
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
        return System.currentTimeMillis() - getDefaultRange()*60*1000L
    }

    override fun updateChart(dataSet: LineDataSet) {
        Log.v(LOG_ID, "Update chart for ${dataSet.values.size} entries and ${dataSet.circleColors.size} colors")
        chart.data = LineData(dataSet)
        chart.notifyDataSetChanged()
        chart.invalidate()
    }

    private fun createBitmap() {
        Log.d(LOG_ID, "createBitmap")
        // reset bitmap and create it
        chart.fitScreen()
        chart.data?.clearValues()
        chart.data?.notifyDataChanged()
        chart.notifyDataSetChanged()
        //chart.clear()
        initData()
    }

    override fun update() {
        // update limit lines
        if(ReceiveData.time > 0) {
            ChartData.addData(ReceiveData.time, ReceiveData.rawValue)
            createBitmap()
        }
    }



}