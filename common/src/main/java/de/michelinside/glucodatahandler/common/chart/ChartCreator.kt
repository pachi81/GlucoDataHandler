package de.michelinside.glucodatahandler.common.chart

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.core.view.drawToBitmap
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.github.mikephil.charting.utils.Utils as ChartUtils
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
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.math.RoundingMode
import java.util.concurrent.TimeUnit
import kotlin.math.abs


open class ChartCreator(protected val chart: GlucoseChart, protected val context: Context, protected val durationPref: String = ""): NotifierInterface,
    SharedPreferences.OnSharedPreferenceChangeListener {
    private var LOG_ID = "GDH.Chart.Creator"
    protected var created = false
    protected val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
    protected var init = false
    protected var hasTimeNotifier = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    //private var createJob: Job? = null
    private var dataSyncJob: Job? = null
    protected open val resetChart = false
    protected var durationHours = 4

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

    private fun init() {
        if(!init) {
            LOG_ID = "GDH.Chart.Creator." + chart.id.toString()
            Log.d(LOG_ID, "init")
            ChartUtils.init(context)
            if(durationPref.isNotEmpty()) {
                durationHours = sharedPref.getInt(durationPref, 4)
            }
            sharedPref.registerOnSharedPreferenceChangeListener(this)
            updateNotifier()
            init = true
        }
    }

    protected fun updateNotifier() {
        val hasData = dbAccess.hasGlucoseValues(getMinTime())
        Log.d(LOG_ID, "updateNotifier - has data: $hasData - xAxisEnabled: ${chart.xAxis.isEnabled}")
        if(hasData || chart.xAxis.isEnabled) {
            InternalNotifier.addNotifier(context, this, mutableSetOf(NotifySource.TIME_VALUE))
            hasTimeNotifier = true
        } else {
            InternalNotifier.remNotifier(context, this)
            hasTimeNotifier = false
        }
    }

    private fun waitForCreation() {
        /*if(createJob?.isActive == true) {
            Log.d(LOG_ID, "waitForCreation - wait for current execution")
            runBlocking {
                createJob!!.join()
            }
        }*/
    }

    fun create() {
        waitForCreation()
        Log.d(LOG_ID, "create")
        try {
            init()
            resetChart()
            initXaxis()
            initYaxis()
            initChart()
            initData()
            created = true
        } catch (exc: Exception) {
            Log.e(LOG_ID, "create exception: " + exc.message + " - " + exc.stackTraceToString())
        }
    }

    fun close() {
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
        chart.invalidate()
        chart.waitForInvalidate()
    }

    protected open fun initXaxis() {
        Log.v(LOG_ID, "initXaxis")
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.setDrawGridLines(true)
        chart.xAxis.enableGridDashedLine(10F, 10F, 0F)
        chart.xAxis.valueFormatter = TimeValueFormatter(chart)
        chart.setXAxisRenderer(TimeAxisRenderer(chart))
        chart.xAxis.textColor = context.resources.getColor(R.color.text_color)
    }

    protected open fun initYaxis() {
        Log.v(LOG_ID, "initYaxis")
        chart.axisRight.valueFormatter = GlucoseFormatter()
        chart.axisRight.setDrawZeroLine(false)
        chart.axisRight.setDrawAxisLine(false)
        chart.axisRight.setDrawGridLines(false)
        chart.axisRight.xOffset = -15F
        chart.axisRight.textColor = context.resources.getColor(R.color.text_color)

        chart.axisLeft.isEnabled = showOtherUnit()
        if(chart.axisLeft.isEnabled) {
            chart.axisLeft.valueFormatter = GlucoseFormatter(true)
            chart.axisLeft.setDrawZeroLine(false)
            chart.axisLeft.setDrawAxisLine(false)
            chart.axisLeft.setDrawGridLines(false)
            chart.axisLeft.xOffset = -15F
            chart.axisLeft.textColor = context.resources.getColor(R.color.text_color)
        }
    }

    private fun createLimitLine(limit: Float): LimitLine {
        val line = LimitLine(limit)
        line.lineColor = context.resources.getColor(R.color.gray)
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
            dataSet.circleRadius = 3F
            dataSet.setDrawValues(false)
            dataSet.setDrawCircleHole(false)
            dataSet.axisDependency = YAxis.AxisDependency.RIGHT
            dataSet.enableDashedLine(0F, 1F, 0F)
            chart.data = LineData(dataSet)
            chart.notifyDataSetChanged()
            Log.v(LOG_ID, "Min: ${chart.xAxis.axisMinimum} - visible: ${chart.lowestVisibleX} - Max: ${chart.xAxis.axisMaximum} - visible: ${chart.highestVisibleX}")
        }
    }

    protected open fun getYAxisInterval(): Float {
        return 50F
    }

    protected fun updateYAxisLabelCount() {
        if(chart.axisRight.isDrawLabelsEnabled && getYAxisInterval() > 0F) {
            val count = Utils.round(chart.axisRight.axisMaximum / getYAxisInterval(), 0, RoundingMode.DOWN).toInt()
            if(count != chart.axisRight.labelCount) {
                Log.v(LOG_ID, "update y-axis label count: $count for ${chart.axisRight.axisMaximum}")
                chart.axisRight.setLabelCount(count)
                if(chart.axisLeft.isEnabled)
                    chart.axisLeft.setLabelCount(count)
            }
        }
    }

    protected open fun initChart(touchEnabled: Boolean = true) {
        Log.v(LOG_ID, "initChart - touchEnabled: $touchEnabled")
        chart.setTouchEnabled(touchEnabled)
        initDataSet()
        chart.setBackgroundColor(Color.TRANSPARENT)
        chart.isAutoScaleMinMaxEnabled = false
        chart.legend.isEnabled = false
        chart.description.isEnabled = false
        chart.isScaleYEnabled = false
        chart.minOffset = 0F
        chart.setExtraOffsets(4F, 4F, 4F, 4F)
        if(touchEnabled) {
            val mMarker = CustomBubbleMarker(context, true)
            mMarker.chartView = chart
            chart.marker = mMarker
            chart.setOnChartValueSelectedListener(object: OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
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
        chart.axisRight.axisMinimum = Constants.GLUCOSE_MIN_VALUE.toFloat()-10F
        chart.axisRight.axisMaximum = getDefaultMaxValue()
        chart.axisLeft.axisMinimum = chart.axisRight.axisMinimum
        chart.axisLeft.axisMaximum = chart.axisRight.axisMaximum
        updateYAxisLabelCount()
        chart.isScaleXEnabled = false
        chart.invalidate()
        chart.waitForInvalidate()
    }

    protected open fun getDefaultMaxValue() : Float {
        return ReceiveData.highRaw + 10
    }

    protected fun stopDataSync() {
        if(dataSyncJob != null) {
            runBlocking {
                Log.d(LOG_ID, "stop data sync - wait for current execution")
                dataSyncJob!!.cancelAndJoin()
            }
        }
    }

    private fun initData() {
        initDataSet()
        stopDataSync()
        dataSyncJob = scope.launch {
            dataSync()
        }
    }

    private suspend fun dataSync() {
        Log.d(LOG_ID, "dataSync running")
        try {
            dbAccess.getLiveValuesByTimeSpan(getMaxRange().toInt()/60).collect{ values ->
                update(values)
            }
            Log.d(LOG_ID, "dataSync done")
        } catch (exc: CancellationException) {
            Log.d(LOG_ID, "dataSync cancelled")
        } catch (exc: Exception) {
            Log.e(LOG_ID, "dataSync exception: " + exc.message.toString() )
        }
    }

    protected open fun getMaxRange(): Long {
        return 0L
    }

    protected fun getMinTime(): Long {
        if(getMaxRange() > 0L)
            return System.currentTimeMillis() - (getMaxRange()*60*1000L)
        return 0L
    }

    protected open fun getDefaultRange(): Long {
        return durationHours * 60L
    }

    protected open fun addEntries(values: List<GlucoseValue>) {
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
        } else {
            addEmptyTimeData()
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
        var minTime = getMinTime()
        if (minTime == 0L) // at least one hour should be shown
            minTime = System.currentTimeMillis() - (60 * 60 * 1000L)

        var minValue = 0L
        var maxValue = 0L

        if(chart.data != null && chart.data.dataSetCount > 0) {
            val dataSet = chart.data.getDataSetByIndex(0) as LineDataSet
            if (dataSet.values.isEmpty()) {
                Log.v(LOG_ID, "No values, set min and max")
                minValue = minTime
                maxValue = System.currentTimeMillis()
            } else {
                Log.v(
                    LOG_ID,
                    "Min time: ${Utils.getUiTimeStamp(minTime)} - first value: ${
                        Utils.getUiTimeStamp(TimeValueFormatter.from_chart_x(dataSet.values.first().x).time)
                    } - last value: ${Utils.getUiTimeStamp(TimeValueFormatter.from_chart_x(dataSet.values.last().x).time)}"
                )
                if (TimeValueFormatter.from_chart_x(dataSet.values.first().x).time > minTime) {
                    minValue = minTime
                }
                if (Utils.getElapsedTimeMinute(TimeValueFormatter.from_chart_x(dataSet.values.last().x).time) >= 1) {
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

    protected open fun updateChart(dataSet: LineDataSet) {
        val right = isRight()
        val left = isLeft()
        Log.v(LOG_ID, "Min: ${chart.xAxis.axisMinimum} - visible: ${chart.lowestVisibleX} - Max: ${chart.xAxis.axisMaximum} - visible: ${chart.highestVisibleX} - isLeft: ${left} - isRight: ${right}" )
        val diffTimeMin = TimeUnit.MILLISECONDS.toMinutes(TimeValueFormatter.from_chart_x(chart.highestVisibleX).time - TimeValueFormatter.from_chart_x(chart.lowestVisibleX).time)
        if(!chart.highlighted.isNullOrEmpty() && chart.highlighted[0].dataSetIndex != 0) {
            Log.v(LOG_ID, "Unset current highlighter")
            chart.highlightValue(null)
        }
        chart.data = LineData(dataSet)
        addEmptyTimeData()
        chart.notifyDataSetChanged()
        invalidateChart(diffTimeMin, right, left)
    }

    protected open fun invalidateChart(diffTime: Long, right: Boolean, left: Boolean) {
        val defaultRange = getDefaultRange()
        var diffTimeMin = diffTime
        val newDiffTime = TimeUnit.MILLISECONDS.toMinutes( TimeValueFormatter.from_chart_x(chart.xChartMax).time - TimeValueFormatter.from_chart_x(chart.lowestVisibleX).time)
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
        } else {
            Log.v(LOG_ID, "Invalidate chart")
            chart.setVisibleXRangeMaximum(diffTimeMin.toFloat())
            setXRange = true
            chart.invalidate()
        }

        if(setXRange) {
            chart.setVisibleXRangeMinimum(60F)
            chart.setVisibleXRangeMaximum(60F*24F)
        }
    }

    private fun getFirstTimestamp(): Long {
        if(chart.data != null && chart.data.dataSetCount > 0) {
            val dataSet = chart.data.getDataSetByIndex(0) as LineDataSet
            if (dataSet.values.isNotEmpty()) {
                return TimeValueFormatter.from_chart_x(dataSet.values.first().x).time
            }
        }
        return 0L
    }

    private fun getEntryCount(): Int {
        if(chart.data != null && chart.data.dataSetCount > 0) {
            val dataSet = chart.data.getDataSetByIndex(0) as LineDataSet
            return dataSet.entryCount
        }
        return 0
    }

    private fun getLastTimestamp(): Long {
        if(chart.data != null && chart.data.dataSetCount > 0) {
            val dataSet = chart.data.getDataSetByIndex(0) as LineDataSet
            if (dataSet.values.isNotEmpty()) {
                return TimeValueFormatter.from_chart_x(dataSet.values.last().x).time
            }
        }
        return 0L
    }

    protected fun update(values: List<GlucoseValue>) {
        Log.d(LOG_ID, "update called for ${values.size} value")
        if(values.isNotEmpty()) {
            if(resetChart || values.first().timestamp != getFirstTimestamp() || (abs(values.size-getEntryCount()) > 5)) {
                resetData(values)
                return
            }
            val newValues = values.filter { data -> data.timestamp > getLastTimestamp() }
            addEntries(newValues)
        }
    }

    protected open fun updateTimeElapsed() {
        Log.v(LOG_ID, "update time elapsed")
        updateChart(chart.data.getDataSetByIndex(0) as LineDataSet)
    }

    protected fun resetChartData() {
        if(chart.data != null) {
            Log.d(LOG_ID, "Reset chart data")
            chart.highlightValue(null)
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
            create()
        }
        if(dataSource == NotifySource.TIME_VALUE) {
            Log.d(LOG_ID, "time elapsed: ${ReceiveData.getElapsedTimeMinute()}")
            if(ReceiveData.getElapsedTimeMinute().mod(2) == 0) {
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

    protected fun createBitmap(): Bitmap? {
        try {
            if(chart.width > 0 && chart.height > 0)
                return chart.drawToBitmap()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "getBitmap exception: " + exc.message.toString() )
        }
        return null
    }

    protected open fun showOtherUnit(): Boolean {
        return sharedPref.getBoolean(Constants.SHARED_PREF_SHOW_OTHER_UNIT, false)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        Log.d(LOG_ID, "onSharedPreferenceChanged: $key")
        try {
            if(key == durationPref) {
                durationHours = sharedPref.getInt(durationPref, durationHours)
                resetChartData()
                chart.fitScreen()
                initData()
            } else if (graphPrefList.contains(key)) {
                Log.i(LOG_ID, "re create graph after settings changed for key: $key")
                ReceiveData.updateSettings(sharedPref)
                if(chart.data != null && chart.data.getDataSetByIndex(0).entryCount > 0)
                    create() // recreate chart with new graph data
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.message.toString() )
        }
    }
}
