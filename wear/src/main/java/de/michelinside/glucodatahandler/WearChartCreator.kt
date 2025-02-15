package de.michelinside.glucodatahandler

import android.content.Context
import android.util.Log
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.chart.ChartCreator
import de.michelinside.glucodatahandler.common.chart.GlucoseChart

class WearChartCreator(chart: GlucoseChart, context: Context, durationPref: String) : ChartCreator(chart, context, durationPref) {
    private val LOG_ID = "GDH.Chart.WearCreator"

    override val yAxisOffset = -100F
    override var durationHours = 2
    override val graphStartTime = (System.currentTimeMillis() - Constants.DB_MAX_DATA_WEAR_TIME_MS)

    override fun initXaxis() {
        Log.v(LOG_ID, "initXaxis")
        super.initXaxis()
        chart.xAxis.setLabelCount(4)
    }

    override fun showOtherUnit(): Boolean = false

    override fun initYaxis() {
        Log.v(LOG_ID, "initYaxis")
        super.initYaxis()
    }
}