package de.michelinside.glucodatahandler.common.chart

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.util.Log
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import de.michelinside.glucodatahandler.common.AppSource
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.database.dbAccess
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.BitmapUtils
import de.michelinside.glucodatahandler.common.utils.Utils

class ChartBitmapCreator(chart: GlucoseChart, context: Context, durationPref: String = "", private val forComplication: Boolean = false, private val showAxisPref: String? = null): ChartCreator(chart, context, durationPref) {
    companion object {
        const val defaultDurationHours = 2
    }
    private var LOG_ID = "GDH.Chart.BitmapCreator"
    private var bitmap: Bitmap? = null
    private var bitmapValid = false
    override val resetChart = true
    override val circleRadius: Float get() {
        return if(GlucoDataService.appSource == AppSource.WEAR_APP) 3F else customCircleRadius
    }
    override var durationHours = defaultDurationHours
    override val touchEnabled = false
    private var customCircleRadius = 2.2F
    private var graphCreated = false

    init {
        LOG_ID = "GDH.Chart.BitmapCreator." + chart.id.toString()
    }

    override fun init() {
        readCircleRadius()
        super.init()
    }

    private fun readCircleRadius() {
        customCircleRadius = ((sharedPref.getInt(Constants.SHARED_PREF_GRAPH_BITMAP_CIRCLE_RADIUS, 5)).toFloat()*3/10) + 0.7F  // starts at one!
        Log.i(LOG_ID, "using circle radius: $customCircleRadius")
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

    override fun getDefaultMinValue(): Float {
        if(forComplication) {
            if(ReceiveData.lowRaw > 0F)
                return ReceiveData.lowRaw - 10F
            if(ReceiveData.targetMinRaw > 0F)
                return ReceiveData.targetMinRaw - 10F
        }
        return super.getDefaultMinValue()
    }

    override fun getMaxRange(): Long {
        return getDefaultRange()
    }

    override fun updateChart(dataSet: LineDataSet?) {
        Log.d(LOG_ID, "updateChart for dataSet: ${dataSet?.label}")
        if(dataSet != null) {
            Log.d(LOG_ID, "Update chart for ${dataSet.values.size} entries and ${dataSet.circleColors.size} colors")
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
            graphCreated = true
            bitmapValid = false  // reset
            InternalNotifier.notify(context, NotifySource.GRAPH_CHANGED, Bundle().apply { putInt(Constants.GRAPH_ID, chart.id) })
        }
    }

    override fun updateTimeElapsed() {
        Log.v(LOG_ID, "updateTimeElapsed")
        update(dbAccess.getGlucoseValues(getMinTime()))
    }

    override fun onDataSyncStopped() {
        Log.d(LOG_ID, "onDataSyncStopped")
        super.onDataSyncStopped()
        graphCreated = false
        bitmapValid = false  // reset
        InternalNotifier.notify(context, NotifySource.GRAPH_CHANGED, Bundle().apply { putInt(Constants.GRAPH_ID, chart.id) })
    }

    private fun createBitmap() {
        try {
            Log.d(LOG_ID, "Create bitmap - duration: $durationHours - width: ${chart.width} - height: ${chart.height}")
            if(durationHours > 0 && !paused) {
                if(chart.width == 0 || chart.height == 0)
                    chart.waitForInvalidate()
                if(chart.width > 0 && chart.height > 0) {
                    Log.d(LOG_ID, "Draw bitmap")
                    bitmap = BitmapUtils.loadBitmapFromView(chart, bitmap)
                    bitmapValid = true
                    return
                }
            }
            Log.i(LOG_ID, "No bitmap created!")
        } catch (exc: Exception) {
            Log.e(LOG_ID, "getBitmap exception: " + exc.message.toString() )
        }
    }

    fun getBitmap(): Bitmap? {
        if(durationHours == 0 || paused || !graphCreated) {
            return null
        }
        if(!bitmapValid || bitmap == null) {
            createBitmap()
        }
        return bitmap
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        Log.v(LOG_ID, "onSharedPreferenceChanged: $key")

        try {
            if(key == Constants.SHARED_PREF_GRAPH_BITMAP_CIRCLE_RADIUS) {
                readCircleRadius()
                recreate()
            } else if(key == showAxisPref) {
                recreate()
            } else {
                super.onSharedPreferenceChanged(sharedPreferences, key)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.message.toString() )
        }
    }
}