package de.michelinside.glucodatahandler

import android.content.Context
import android.util.Log
import de.michelinside.glucodatahandler.common.chart.ChartCreator
import de.michelinside.glucodatahandler.common.chart.GlucoseChart

class WearChartCreator(chart: GlucoseChart, context: Context, durationPref: String) : ChartCreator(chart, context, durationPref) {
    private val LOG_ID = "GDH.Chart.WearCreator"

    override val yAxisOffset = -150F

    override fun initXaxis() {
        Log.v(LOG_ID, "initXaxis")
        super.initXaxis()
        chart.xAxis.setLabelCount(4)
    }

    override fun getYAxisInterval(): Float {
        return 50F
    }

    override fun showOtherUnit(): Boolean = false

    override fun initYaxis() {
        Log.v(LOG_ID, "initYaxis")
        super.initYaxis()
        /*
        chart.axisRight.setDrawAxisLine(false)
        chart.axisRight.setDrawLabels(false)
        chart.axisRight.setDrawZeroLine(false)
        chart.axisRight.setDrawGridLines(false)
        chart.axisLeft.isEnabled = false
        */
    }

}