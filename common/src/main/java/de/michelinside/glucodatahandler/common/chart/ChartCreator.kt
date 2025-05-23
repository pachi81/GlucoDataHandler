package de.michelinside.glucodatahandler.common.chart

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import de.michelinside.glucodatahandler.common.BuildConfig
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.R
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.database.GlucoseValue
import de.michelinside.glucodatahandler.common.database.dbAccess
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.Utils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.math.RoundingMode
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import com.github.mikephil.charting.utils.Utils as ChartUtils


open class ChartCreator(protected val chart: GlucoseChart, protected val context: Context, protected val durationPref: String = "", protected val transparencyPref: String = ""): NotifierInterface,
    SharedPreferences.OnSharedPreferenceChangeListener {
    companion object {
        const val defaultDurationHours = 4
    }
    private var LOG_ID = "GDH.Chart.Creator"
    protected var created = false
    protected val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
    protected var init = false
    protected var hasTimeNotifier = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    //private var createJob: Job? = null
    private var dataSyncJob: Job? = null
    protected open val resetChart = false
    protected open var durationHours = defaultDurationHours
    protected open val yAxisOffset = 2F
    protected open val yAxisLeftOffset = -17F
    protected open val yAxisInterval = 50F
    protected open val circleRadius = 2F
    protected open val touchEnabled = true
    protected open var graphDays = 0
    private var graphStartTime = 0L
    private var recreateThread: Thread? = null
    protected open var backgroundTransparency = 0
    var labelColor: Int = 0
    protected open val textColor: Int get() {
        if(labelColor != 0)
            return labelColor
        if(backgroundTransparency > 3)
            return Color.WHITE
        return context.resources.getColor(R.color.text_color)
    }
    protected open val limitLineColor: Int get() {
        if(backgroundTransparency > 3)
            return 0xFFCCCCCC.toInt()
        return context.resources.getColor(R.color.chart_limit_line_color)
    }


    val enabled: Boolean get() {
        return chart.visibility == View.VISIBLE
    }

    val paused: Boolean get() {
        return dataSyncJob!!.isActive != true
    }

    private var graphPrefList = mutableSetOf(
        Constants.SHARED_PREF_LOW_GLUCOSE,
        Constants.SHARED_PREF_HIGH_GLUCOSE,
        Constants.SHARED_PREF_TARGET_MIN,
        Constants.SHARED_PREF_TARGET_MAX,
        Constants.SHARED_PREF_COLOR_ALARM,
        Constants.SHARED_PREF_COLOR_OUT_OF_RANGE,
        Constants.SHARED_PREF_COLOR_OK,
        Constants.SHARED_PREF_SHOW_OTHER_UNIT
    )

    open fun isGraphPref(key: String?): Boolean {
        return graphPrefList.contains(key)
    }

    protected open fun init() {
        if(!init) {
            LOG_ID = "GDH.Chart.Creator." + chart.id.toString()
            Log.d(LOG_ID, "init")
            ChartUtils.init(context)
            if(durationPref.isNotEmpty()) {
                durationHours = sharedPref.getInt(durationPref, durationHours)
                Log.d(LOG_ID, "Duration: $durationHours hours")
                if(durationHours == 0)
                    disable()
            }
            if(transparencyPref.isNotEmpty()) {
                backgroundTransparency = sharedPref.getInt(transparencyPref, backgroundTransparency)
            }
            sharedPref.registerOnSharedPreferenceChangeListener(this)
            updateNotifier()
            updateGraphStartTime()
            init = true
        }
    }

    protected open fun updateNotifier() {
        val hasData = dbAccess.hasGlucoseValues(getMinTime())
        Log.d(LOG_ID, "updateNotifier -enabled: $enabled - has data: $hasData - xAxisEnabled: ${chart.xAxis.isEnabled}")
        if(enabled && (hasData || chart.xAxis.isEnabled)) {
            InternalNotifier.addNotifier(context, this, mutableSetOf(NotifySource.TIME_VALUE, NotifySource.DAY_CHANGED))
            hasTimeNotifier = true
        } else {
            remNotifier()
        }
        Log.v(LOG_ID, "updateNotifier - has notifier: $hasTimeNotifier")
    }

    private fun remNotifier() {
        Log.d(LOG_ID, "remNotifier called")
        InternalNotifier.remNotifier(context, this)
        hasTimeNotifier = false
    }

    private fun waitForCreation() {
        /*if(createJob?.isActive == true) {
            Log.d(LOG_ID, "waitForCreation - wait for current execution")
            runBlocking {
                createJob!!.join()
            }
        }*/
    }

    fun create(initData: Boolean = false) {
        waitForCreation()
        try {
            Log.d(LOG_ID, "create")
            stopDataSync()
            init()
            if(enabled) {
                resetChart()
                initXaxis()
                initYaxis()
                initChart()
                if(initData) {
                    initData()
                } else {
                    initDataSet()
                }
                created = true
                Log.d(LOG_ID, "create done")
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "create exception: " + exc.message + " - " + exc.stackTraceToString())
        }
    }

    fun pause() {
        try {
            remNotifier()
            stopDataSync()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "pause exception: " + exc.message.toString() )
        }
    }

    fun resume() {
        try {
            updateNotifier()
            if(enabled && !startDataSync()) {
                if(chart.xAxis.axisMinimum > chart.xAxis.axisMaximum)
                    chart.postInvalidate()   // need to redraw the chart
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "resume exception: " + exc.message.toString() )
        }
    }

    open fun close() {
        Log.d(LOG_ID, "close init: $init")
        try {
            if(init) {
                InternalNotifier.remNotifier(context, this)
                sharedPref.unregisterOnSharedPreferenceChangeListener(this)
                stopDataSync()
                waitForCreation()
                init = false
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "close exception: " + exc.message.toString() )
        }
    }

    fun isRight(): Boolean {
        return chart.highestVisibleX.toInt() == chart.xChartMax.toInt()
    }

    fun isLeft(): Boolean {
        return chart.lowestVisibleX.toInt() == chart.xChartMin.toInt()
    }

    private fun resetChart() {
        Log.v(LOG_ID, "resetChart")
        //chart.highlightValue(null)
        chart.fitScreen()
        chart.data?.clearValues()
        chart.data?.notifyDataChanged()
        chart.axisRight.removeAllLimitLines()
        chart.xAxis.valueFormatter = null
        chart.axisRight.valueFormatter = null
        chart.axisLeft.valueFormatter = null
        chart.marker = null
        chart.notifyDataSetChanged()
        chart.clear()
    }

    protected open fun initXaxis() {
        Log.v(LOG_ID, "initXaxis")
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.setDrawGridLines(true)
        chart.xAxis.enableGridDashedLine(10F, 10F, 0F)
        chart.xAxis.valueFormatter = TimeValueFormatter(chart)
        chart.setXAxisRenderer(TimeAxisRenderer(chart))
        chart.xAxis.textColor = textColor
    }

    protected open fun initYaxis() {
        Log.v(LOG_ID, "initYaxis right-offset: $yAxisOffset - left-offset: $yAxisLeftOffset")
        chart.axisRight.isEnabled = true
        chart.axisRight.valueFormatter = GlucoseFormatter()
        chart.axisRight.setDrawZeroLine(false)
        chart.axisRight.setDrawAxisLine(false)
        chart.axisRight.setDrawGridLines(false)
        chart.axisRight.xOffset = yAxisOffset
        chart.axisRight.textColor = textColor

        chart.axisLeft.isEnabled = showOtherUnit()
        if(chart.axisLeft.isEnabled) {
            chart.axisLeft.valueFormatter = GlucoseFormatter(true)
            chart.axisLeft.setDrawZeroLine(false)
            chart.axisLeft.setDrawAxisLine(false)
            chart.axisLeft.setDrawGridLines(false)
            chart.axisLeft.xOffset = yAxisLeftOffset
            chart.axisLeft.textColor = textColor
        }
    }

    private fun createLimitLine(limit: Float): LimitLine {
        Log.v(LOG_ID, "Create limit line for limit: $limit")
        val line = LimitLine(limit)
        line.lineColor = limitLineColor
        return line
    }

    protected fun initDataSet() {
        if(chart.data == null || chart.data.dataSetCount == 0) {
            Log.v(LOG_ID, "initDataSet")
            val dataSet = LineDataSet(ArrayList(), "Glucose Values")
            //dataSet.valueFormatter = GlucoseFormatter()
            //dataSet.colors = mutableListOf<Int>()
            dataSet.circleColors = mutableListOf<Int>()
            //dataSet.lineWidth = 1F
            dataSet.circleRadius = circleRadius
            dataSet.setDrawValues(false)
            dataSet.setDrawCircleHole(false)
            dataSet.axisDependency = YAxis.AxisDependency.RIGHT
            dataSet.enableDashedLine(0F, 1F, 0F)
            chart.data = LineData(dataSet)
            chart.notifyDataSetChanged()
            if (BuildConfig.DEBUG)
                Log.v(LOG_ID, "Min: ${chart.xAxis.axisMinimum} - visible: ${chart.lowestVisibleX} - Max: ${chart.xAxis.axisMaximum} - visible: ${chart.highestVisibleX}")
        }
    }

    protected fun updateYAxisLabelCount() {
        if(chart.axisRight.isDrawLabelsEnabled && yAxisInterval > 0F) {
            val count = Utils.round(chart.axisRight.axisMaximum / yAxisInterval, 0, RoundingMode.DOWN).toInt()
            if(count != chart.axisRight.labelCount) {
                Log.v(LOG_ID, "update y-axis label count: $count for ${chart.axisRight.axisMaximum}")
                chart.axisRight.setLabelCount(count)
                if(chart.axisLeft.isEnabled)
                    chart.axisLeft.setLabelCount(count)
            }
        }
    }

    private fun createChartBackground() {
        Log.v(LOG_ID, "createChartBackground for transparency: $backgroundTransparency")
        if(backgroundTransparency == 0) {
            chart.setBackgroundColor(Utils.getBackgroundColor(backgroundTransparency))
            chart.minOffset = 0F
        } else {
            chart.setBackgroundResource(R.drawable.layout_bg_graph)
            val shape = chart.background as GradientDrawable
            shape.setColor(Utils.getBackgroundColor(backgroundTransparency))
            chart.minOffset = 15F
        }
    }

    protected open fun initChart() {
        Log.v(LOG_ID, "initChart - touchEnabled: $touchEnabled")
        chart.setTouchEnabled(false)
        initDataSet()
        createChartBackground()
        chart.isAutoScaleMinMaxEnabled = false
        chart.legend.isEnabled = false
        chart.description.isEnabled = false
        chart.isScaleYEnabled = false
        chart.setExtraOffsets(4F, 4F, 4F, if(chart.xAxis.isEnabled) 8F else 4F)
        if(touchEnabled) {
            val mMarker = CustomBubbleMarker(context, true)
            mMarker.chartView = chart
            chart.marker = mMarker
            chart.setOnChartValueSelectedListener(object: OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    if (BuildConfig.DEBUG)
                        Log.v(LOG_ID, "onValueSelected: ${e?.x} - ${e?.y} - index: ${h?.dataSetIndex}")
                    if(h?.dataSetIndex == 1) {
                        // do not select time line
                        chart.highlightValue(null)
                    }
                }
                override fun onNothingSelected() {
                    Log.v(LOG_ID, "onNothingSelected")
                }
            })
            chart.setOnLongClickListener {
                chart.highlightValue(null)
                true
            }
        }
        chart.axisRight.removeAllLimitLines()

        if(ReceiveData.highRaw > 0F)
            chart.axisRight.addLimitLine(createLimitLine(ReceiveData.highRaw))
        if(ReceiveData.lowRaw > 0F)
            chart.axisRight.addLimitLine(createLimitLine(ReceiveData.lowRaw))
        if(ReceiveData.targetMinRaw > 0F)
            chart.axisRight.addLimitLine(createLimitLine(ReceiveData.targetMinRaw))
        if(ReceiveData.targetMaxRaw > 0F)
            chart.axisRight.addLimitLine(createLimitLine(ReceiveData.targetMaxRaw))
        chart.axisRight.axisMinimum = getDefaultMinValue()
        chart.axisRight.axisMaximum = getDefaultMaxValue()
        chart.axisLeft.axisMinimum = chart.axisRight.axisMinimum
        chart.axisLeft.axisMaximum = chart.axisRight.axisMaximum
        updateYAxisLabelCount()
        chart.isScaleXEnabled = false
    }

    protected open fun getDefaultMaxValue() : Float {
        return listOf(200F, ReceiveData.highRaw, ReceiveData.targetMaxRaw, dbAccess.getMaxValue(getMinTime()).toFloat()).max() + 10F
    }

    protected open fun getDefaultMinValue(): Float {
        return Constants.GLUCOSE_MIN_VALUE.toFloat()-10F
    }

    protected open fun onDataSyncStopped() {}

    protected fun stopDataSync() {
        if(dataSyncJob != null && dataSyncJob!!.isActive) {
            dataSyncJob!!.cancel()
            if(dataSyncJob!!.isActive) {
                runBlocking {
                    Log.d(LOG_ID, "stop data sync - wait for current execution")
                    dataSyncJob!!.join()
                }
            }
            onDataSyncStopped()
        }
    }

    private fun initData() {
        initDataSet()
        startDataSync()
    }

    private fun startDataSync(): Boolean {
        if(enabled && dataSyncJob?.isActive != true) {
            Log.v(LOG_ID, "startDataSync")
            dataSyncJob = scope.launch {
                dataSync()
            }
            return true
        }
        return false
    }

    protected open suspend fun dataSync() {
        Log.d(LOG_ID, "dataSync running")
        try {
            if(getMaxRange() > 0L) {
                dbAccess.getLiveValuesByTimeSpan(getMaxRange().toInt()/60).collect{ values ->
                    update(values)
                }
            }
            else if(graphStartTime > 0L) {
                dbAccess.getLiveValuesByStartTime(graphStartTime).collect{ values ->
                    update(values)
                }
            } else {
                dbAccess.getLiveValues().collect{ values ->
                    update(values)
                }
            }
            Log.d(LOG_ID, "dataSync done")
        } catch (exc: CancellationException) {
            Log.d(LOG_ID, "dataSync cancelled")
        } catch (exc: Exception) {
            Log.e(LOG_ID, "dataSync exception: " + exc.message.toString() + "\n" + exc.stackTraceToString())
        }
    }

    protected open fun getMaxRange(): Long {
        return 0L
    }

    protected fun getMinTime(): Long {
        if(getMaxRange() > 0L)
            return System.currentTimeMillis() - (getMaxRange()*60*1000L)
        return graphStartTime
    }

    protected open fun getDefaultRange(): Long {
        return durationHours * 60L
    }

    private fun addEntries(values: List<GlucoseValue>) {
        Log.d(LOG_ID, "Add ${values.size} entries")
        val dataSet = chart.data.getDataSetByIndex(0) as LineDataSet
        var added = false
        if(values.isNotEmpty()) {
            if(dataSet.values.isEmpty()) {
                Log.v(LOG_ID, "Reset colors")
                dataSet.resetCircleColors()
            }
            for (element in values) {
                val entry = Entry(TimeValueFormatter.to_chart_x(element.timestamp), element.value.toFloat())
                if (dataSet.values.isEmpty() || dataSet.values.last().x < entry.x) {
                    added = true
                    dataSet.addEntry(entry)
                    val color = ReceiveData.getValueColor(entry.y.toInt())
                    //dataSet.addColor(color)
                    dataSet.circleColors.add(color)

                    if (chart.axisRight.axisMinimum > (entry.y - 10F)) {
                        chart.axisRight.axisMinimum = entry.y - 10F
                        chart.axisLeft.axisMinimum = chart.axisRight.axisMinimum
                        updateYAxisLabelCount()
                    }
                    if (chart.axisRight.axisMaximum < (entry.y + 10F)) {
                        chart.axisRight.axisMaximum = entry.y + 10F
                        chart.axisLeft.axisMaximum = chart.axisRight.axisMaximum
                        updateYAxisLabelCount()
                    }
                }
            }
        }

        if(added) {
            dataSet.notifyDataSetChanged()
            updateChart(dataSet)
            if(touchEnabled)
                chart.enableTouch()
        } else {
            updateChart(null)   // update chart for time data-change
        }
    }

    protected fun addEmptyTimeData() {
        Log.v(LOG_ID, "addEmptyTimeData - entries: ${chart.data?.entryCount}")
        if(chart.data?.entryCount == 0 && !chart.xAxis.isEnabled) {
            if(hasTimeNotifier)
                updateNotifier()
            return // no data and not xAxis, do not add the time line, as it is not needed
        }
        if(!hasTimeNotifier)
            updateNotifier()
        val minTime = System.currentTimeMillis() - (getDefaultRange() * 60 * 1000L)

        var minValue = 0L
        var maxValue = 0L

        if(chart.data != null && chart.data.dataSetCount > 0) {
            val dataSet = chart.data.getDataSetByIndex(0) as LineDataSet
            val values = dataSet.values.toList()
            if (values.isEmpty()) {
                Log.v(LOG_ID, "No values, set min and max")
                minValue = minTime
                maxValue = System.currentTimeMillis()
            } else {
                if (BuildConfig.DEBUG)
                    Log.v(
                        LOG_ID,
                        "Min time: ${Utils.getUiTimeStamp(minTime)} - first value: ${
                            Utils.getUiTimeStamp(TimeValueFormatter.from_chart_x(values.first().x))
                        } - last value: ${Utils.getUiTimeStamp(TimeValueFormatter.from_chart_x(values.last().x))}"
                    )
                if (TimeValueFormatter.from_chart_x(values.first().x) > minTime) {
                    minValue = minTime
                }
                if (Utils.getElapsedTimeMinute(TimeValueFormatter.from_chart_x(values.last().x)) >= 1) {
                    maxValue = System.currentTimeMillis()
                }
            }
        }
        if(minValue > 0 || maxValue > 0) {
            Log.d(LOG_ID, "Creating extra data set from ${Utils.getUiTimeStamp(minValue)} - ${Utils.getUiTimeStamp(maxValue)}")
            val entries = ArrayList<Entry>()
            if(minValue > 0)
                entries.add(Entry(TimeValueFormatter.to_chart_x(minValue), 120F))
            if(maxValue > 0)
                entries.add(Entry(TimeValueFormatter.to_chart_x(maxValue), 120F))

            val timeSet = LineDataSet(entries, "Time line")
            timeSet.lineWidth = 1F
            timeSet.circleRadius = 1F
            timeSet.setDrawValues(false)
            timeSet.setDrawCircleHole(false)
            timeSet.setCircleColors(0)
            timeSet.setColors(0)
            chart.data.addDataSet(timeSet)
        }
    }

    protected open fun updateChart(dataSet: LineDataSet?) {
        val right = isRight()
        val left = isLeft()
        if (BuildConfig.DEBUG)
            Log.v(LOG_ID, "updateChart - Min: ${chart.xAxis.axisMinimum} - visible: ${chart.lowestVisibleX} - Max: ${chart.xAxis.axisMaximum} - visible: ${chart.highestVisibleX} - isLeft: ${left} - isRight: ${right}" )
        val diffTimeMin = TimeUnit.MILLISECONDS.toMinutes(TimeValueFormatter.from_chart_x(chart.highestVisibleX) - TimeValueFormatter.from_chart_x(chart.lowestVisibleX))
        if(!chart.highlighted.isNullOrEmpty() && chart.highlighted[0].dataSetIndex != 0) {
            Log.v(LOG_ID, "Unset current highlighter")
            chart.highlightValue(null)
        }
        if(dataSet != null)
            chart.data = LineData(dataSet)
        addEmptyTimeData()
        chart.notifyDataSetChanged()
        invalidateChart(diffTimeMin, right, left)
    }

    protected open fun invalidateChart(diffTime: Long, right: Boolean, left: Boolean) {
        val defaultRange = getDefaultRange()
        var diffTimeMin = diffTime
        val newDiffTime = TimeUnit.MILLISECONDS.toMinutes( TimeValueFormatter.from_chart_x(chart.xChartMax) - TimeValueFormatter.from_chart_x(chart.lowestVisibleX))
        Log.d(LOG_ID, "Diff-Time: ${diffTimeMin} minutes - newDiffTime: ${newDiffTime} minutes")
        var setXRange = false
        if(!chart.isScaleXEnabled && newDiffTime >= minOf(defaultRange, 90)) {
            Log.d(LOG_ID, "Enable X scale")
            chart.isScaleXEnabled = true
            setXRange = true
        }

        if((right && left && diffTimeMin < defaultRange && newDiffTime >= defaultRange)) {
            Log.v(LOG_ID, "Set ${defaultRange/60} hours diff time")
            diffTimeMin = defaultRange
        }

        if(right) {
            if(diffTimeMin >= defaultRange || !left) {
                Log.d(LOG_ID, "Fix interval: $diffTimeMin")
                chart.setVisibleXRangeMaximum(diffTimeMin.toFloat())
                setXRange = true
            }
            Log.v(LOG_ID, "moveViewToX ${chart.xChartMax}")
            chart.moveViewToX(chart.xChartMax)
        } /*else {
            Log.v(LOG_ID, "Invalidate chart")
            chart.setVisibleXRangeMaximum(diffTimeMin.toFloat())
            setXRange = true
            chart.postInvalidate()
        }*/

        if(setXRange) {
            chart.setVisibleXRangeMinimum(60F)
            chart.setVisibleXRangeMaximum(60F*24F)
        }
    }

    private fun getFirstTimestamp(): Float {
        try {
            if(chart.data != null && chart.data.dataSetCount > 0) {
                val dataSet = chart.data.getDataSetByIndex(0) as LineDataSet
                if (dataSet.values.isNotEmpty() && dataSet.values.first() != null) {
                    return dataSet.values.first().x
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "getFirstTimestamp exception: " + exc.message.toString() )
        }
        return 0F
    }

    private fun getEntryCount(): Int {
        if(chart.data != null && chart.data.dataSetCount > 0) {
            val dataSet = chart.data.getDataSetByIndex(0) as LineDataSet
            return dataSet.entryCount
        }
        return 0
    }

    private fun getLastTimestamp(): Long {
        try {
            if(chart.data != null && chart.data.dataSetCount > 0) {
                val dataSet = chart.data.getDataSetByIndex(0) as LineDataSet
                if (dataSet.values.isNotEmpty() && dataSet.values.last() != null) {
                    return TimeValueFormatter.from_chart_x(dataSet.values.last().x)
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "getLastTimestamp exception: " + exc.message.toString() )
        }
        return 0L
    }

    private fun checkValues(values: List<GlucoseValue>): Boolean {
        var lastTime = 0L
        val deleteValues = mutableListOf<Long>()
        values.forEach {
            if(Utils.getTimeDiffMinute(it.timestamp, lastTime, RoundingMode.HALF_UP) == 0L) {
                Log.d(LOG_ID, "Delete duplicate value with timestamp ${Utils.getUiTimeStamp(it.timestamp)} (${it.timestamp}) - previous timestamp ${Utils.getUiTimeStamp(lastTime)} (${lastTime}) - diff: ${it.timestamp - lastTime}ms")
                deleteValues.add(it.timestamp)
            } else {
                lastTime = it.timestamp
            }
        }
        if(deleteValues.isNotEmpty()) {
            Log.i(LOG_ID, "Delete ${deleteValues.size} duplicate values")
            dbAccess.deleteValues(deleteValues)
            return false
        }
        return true
    }

    protected fun update(values: List<GlucoseValue>): Boolean {
        try {
            if(!checkValues(values))
                return false
            if(values.isNotEmpty()) {
                Log.d(LOG_ID, "update called for ${values.size} values - resetChart: $resetChart - entries: ${getEntryCount()} - first value: ${TimeValueFormatter.to_chart_x(values.first().timestamp)} - first: ${getFirstTimestamp()}")
                if(resetChart || TimeValueFormatter.to_chart_x(values.first().timestamp) != getFirstTimestamp() || (getEntryCount() > 0 && abs(values.size-getEntryCount()) > 1)) {
                    resetData(values)
                    return true
                }
                val newValues = values.filter { data -> data.timestamp > getLastTimestamp() }
                addEntries(newValues)
            } else if(getEntryCount() > 0) {
                Log.d(LOG_ID, "Reset chart after db clean up")
                create(true)
            } else {
                addEntries(values)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "update exception: " + exc.message.toString() + " - " + exc.stackTraceToString() )
            try {
                Log.w(LOG_ID, "Reset data after exception occurs!")
                resetData(values)
            } catch (exc: Exception) {
                Log.e(LOG_ID, "update-reset exception: " + exc.message.toString() + " - " + exc.stackTraceToString() )
            }
        }
        return true
    }

    protected open fun updateTimeElapsed() {
        Log.v(LOG_ID, "update time elapsed")
        updateChart(null)
    }

    protected fun resetChartData() {
        if(chart.data != null && chart.data.dataSetCount > 0) {
            Log.d(LOG_ID, "Reset chart data")
            //chart.highlightValue(null)
            val dataSet = chart.data.getDataSetByIndex(0) as LineDataSet
            dataSet.clear()
            dataSet.setColors(0)
            dataSet.setCircleColor(0)
            chart.data.clearValues()
            chart.data.notifyDataChanged()
            chart.notifyDataSetChanged()
        }
    }

    protected fun resetData(values: List<GlucoseValue>) {
        Log.d(LOG_ID, "Reset data")
        resetChartData()
        initDataSet()
        addEntries(values)
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        Log.d(LOG_ID, "OnNotifyData: $dataSource")
        if(!init) {
            Log.w(LOG_ID, "Chart still not init - create!")
            create(true)
        } else if(dataSource == NotifySource.DAY_CHANGED) {
            updateGraphStartTime()
        }
        if(dataSource == NotifySource.TIME_VALUE) {
            Log.d(LOG_ID, "time elapsed: ${ReceiveData.getElapsedTimeMinute()}")
            if(ReceiveData.getElapsedTimeMinute().mod(2) == 0) {
                Log.d(LOG_ID, "update graph after time elapsed")
                waitForCreation()
                //createJob = scope.launch {
                    try {
                        updateTimeElapsed()
                    } catch (exc: Exception) {
                        Log.e(LOG_ID, "OnNotifyData exception: " + exc.message.toString() + " - " + exc.stackTraceToString() )
                    }
                //}
            }
        }
    }

    protected open fun showOtherUnit(): Boolean {
        return sharedPref.getBoolean(Constants.SHARED_PREF_SHOW_OTHER_UNIT, false)
    }

    protected open fun disable(): Boolean {
        if(enabled && durationHours == 0) {
            Log.i(LOG_ID, "disable graph")
            stopDataSync()
            resetChart()
            chart.visibility = View.GONE
            chart.setNoDataText(context.resources.getString(R.string.graph_disabled))
            updateNotifier()
            return true
        }
        return false
    }

    private fun enable(): Boolean {
        if(!enabled && durationHours > 0) {
            Log.i(LOG_ID, "enable graph")
            chart.visibility = View.VISIBLE
            chart.setNoDataText(context.resources.getString(R.string.no_graph_data))
            updateNotifier()
            create(true)
        }
        return false
    }

    protected open fun OnDurationChanged() {
        Log.v(LOG_ID, "OnDurationChanged: $durationHours")
        if(durationHours == 0)
            disable()
        else if(!enable()) {
            recreateChart()
        }
    }

    private fun recreateChart() {
        Log.v(LOG_ID, "recreate chart - enabled: $enabled")
        if(init && enabled) {
            stopDataSync()
            resetChartData()
            chart.fitScreen()
            initData()
        }
    }

    protected fun updateGraphStartTime() {
        Log.d(LOG_ID, "updateGraphStartTime - days $graphDays - graph start time: ${Utils.getUiTimeStamp(graphStartTime)}")
        // get start time from graph days
        if(graphDays == 0 || graphDays >= Constants.DB_MAX_DATA_DAYS) {
            Log.i(LOG_ID, "updateGraphStartTime: reset graph start time - current: $graphStartTime")
            if(graphStartTime != 0L) {
                graphStartTime = 0L
                recreateChart()
            }
            return
        }
        graphStartTime = System.currentTimeMillis() - (graphDays * 24 * 60 * 60 * 1000L)
        Log.i(LOG_ID, "updateGraphStartTime: ${Utils.getUiTimeStamp(graphStartTime)}")
        recreateChart()
    }

    private fun recreate() {
        Log.d(LOG_ID, "recreate called")
        if(chart.data != null) {
            close()
            chart.onDetachedFromWindow()
            create(true) // recreate chart with new graph data
        }
    }

    private fun triggerRecreate() {
        if(recreateThread == null || !recreateThread!!.isAlive) {
            Log.d(LOG_ID, "triggerRecreate")
            recreateThread = Thread {
                try {
                    Thread.sleep(1000)
                    Handler(context.mainLooper).post {
                        recreate()
                    }
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "triggerRecreate exception: " + exc.message.toString() + " - " + exc.stackTraceToString() )
                }
                recreateThread = null
            }
            recreateThread!!.start()
        } else {
            Log.d(LOG_ID, "triggerRecreate - recreate thread already running")
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        Log.d(LOG_ID, "onSharedPreferenceChanged: $key")
        try {
            if(key == durationPref) {
                durationHours = sharedPref.getInt(durationPref, durationHours)
                OnDurationChanged()
            } else if(key == transparencyPref) {
                backgroundTransparency = sharedPref.getInt(transparencyPref, backgroundTransparency)
                Log.d(LOG_ID, "re create graph after transparency changed to: $backgroundTransparency")
                triggerRecreate()
            } else if (isGraphPref(key)) {
                Log.i(LOG_ID, "re create graph after settings changed for key: $key")
                ReceiveData.updateSettings(sharedPref)
                triggerRecreate()
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.message.toString() )
        }
    }
}
