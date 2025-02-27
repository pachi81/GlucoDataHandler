package de.michelinside.glucodatahandler.common.chart

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.util.Log
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.database.dbAccess
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.Utils

class ChartBitmapCreator(chart: GlucoseChart, context: Context, durationPref: String = "", private val forComplication: Boolean = false, private val showAxisPref: String? = null): ChartCreator(chart, context, durationPref) {
    companion object {
        const val defaultDurationHours = 2
    }
    private var LOG_ID = "GDH.Chart.BitmapCreator"
    private var bitmap: Bitmap? = null
    override val resetChart = true
    override val circleRadius = 3F
    override var durationHours = defaultDurationHours
    override val touchEnabled = false

    init {
        LOG_ID = "GDH.Chart.BitmapCreator." + chart.id.toString()
    }

    private val showAxis: Boolean get() {
        if(showAxisPref.isNullOrEmpty())
            return false
        return sharedPref.getBoolean(showAxisPref, false)
    }

    override fun initXaxis() {
        Log.v(LOG_ID, "initXaxis - showAxis: $showAxis")
        chart.xAxis.isEnabled = showAxis
        if(chart.xAxis.isEnabled)
            super.initXaxis()
    }

    override fun initYaxis() {
        val showXAxis = showAxis
        Log.v(LOG_ID, "initYaxis - showAxis: $showXAxis")
        chart.axisRight.setDrawAxisLine(showXAxis)
        chart.axisRight.setDrawLabels(showXAxis)
        chart.axisRight.setDrawZeroLine(showXAxis)
        chart.axisRight.setDrawGridLines(showXAxis)
        chart.axisLeft.isEnabled = false
        if(showXAxis) {
            super.initYaxis()
        }
    }

    override fun initChart() {
        super.initChart()
        updateDescription()
    }

    override fun OnDurationChanged() {
        super.OnDurationChanged()
        updateDescription()
    }

    private fun updateDescription() {
        if(!showAxis && !forComplication) {
            chart.description.isEnabled = true
            chart.description.text = durationHours.toString() + "h"
            chart.description.textSize = Utils.dpToPx(4F, context).toFloat()
            chart.description.textColor = textColor
        } else {
            chart.description.isEnabled = false
        }
    }

    override fun getMaxRange(): Long {
        return getDefaultRange()
    }

    override fun getDefaultMaxValue(): Float {
        if(forComplication)
            return maxOf(super.getDefaultMaxValue(), 310F)
        return super.getDefaultMaxValue()
    }

    override fun updateChart(dataSet: LineDataSet?) {
        Log.v(LOG_ID, "updateChart for dataSet: $dataSet")
        if(dataSet != null) {
            Log.v(LOG_ID, "Update chart for ${dataSet.values.size} entries and ${dataSet.circleColors.size} colors")
            if(dataSet.values.isNotEmpty())
                chart.data = LineData(dataSet)
            else
                chart.data = LineData()
        }
        addEmptyTimeData()
        chart.notifyDataSetChanged()
        chart.postInvalidate()
        Handler(context.mainLooper).post {
            Log.d(LOG_ID, "notify graph changed")
            bitmap = null  // reset
            InternalNotifier.notify(context, NotifySource.GRAPH_CHANGED, Bundle().apply { putInt(Constants.GRAPH_ID, chart.id) })
        }
    }

    override fun updateTimeElapsed() {
        Log.v(LOG_ID, "updateTimeElapsed")
        update(dbAccess.getGlucoseValues(getMinTime()))
    }

    override fun disable(): Boolean {
        if(super.disable()) {
            bitmap = null  // reset
            InternalNotifier.notify(context, NotifySource.GRAPH_CHANGED, Bundle().apply { putInt(Constants.GRAPH_ID, chart.id) })
            return true
        }
        return false
    }

    fun getBitmap(): Bitmap? {
        if(bitmap == null)
            bitmap = createBitmap()
        return bitmap
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        Log.v(LOG_ID, "onSharedPreferenceChanged: $key")

        try {
            if(key == showAxisPref) {
                if(chart.data != null)
                    create(true) // recreate chart with new graph data
            } else {
                super.onSharedPreferenceChanged(sharedPreferences, key)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.message.toString() )
        }
    }
}