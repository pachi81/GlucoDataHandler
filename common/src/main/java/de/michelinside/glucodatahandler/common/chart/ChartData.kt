package de.michelinside.glucodatahandler.common.chart

import android.util.Log
import com.github.mikephil.charting.data.Entry
import de.michelinside.glucodatahandler.common.utils.DummyGraphData
import de.michelinside.glucodatahandler.common.utils.Utils

object ChartData {
    private val LOG_ID = "GDH.Chart.Data"
    private var graphData = mutableMapOf<Long, Int>() // DummyGraphData.create(0, min = 40, max = 260, stepMinute = 5).toMutableMap()

    fun getData(minTime: Long = 0L): ArrayList<Entry> {
        Log.d(LOG_ID, "getData - minTime: ${Utils.getUiTimeStamp(minTime)}")
        val entries = ArrayList<Entry>()
        graphData.toSortedMap().forEach { (t, u) ->
            if(t >= minTime) {
                entries.add(Entry(TimeValueFormatter.to_chart_x(t), u.toFloat()))
            }
        }
        if(entries.isNotEmpty())
            Log.v(LOG_ID, "getData - entries: ${entries.size} from ${Utils.getUiTimeStamp(TimeValueFormatter.from_chart_x(entries.first().x).time)} to ${Utils.getUiTimeStamp(TimeValueFormatter.from_chart_x(entries.last().x).time)}")
        return entries
    }

    fun hasData(minTime: Long = 0L): Boolean {
        return graphData.keys.any { it >= minTime }
    }

    fun addData(time: Long, value: Int) {
        try {
            Log.v(LOG_ID, "addData - time: ${Utils.getUiTimeStamp(time)} - value: $value")
            graphData[time] = value
        } catch (exc: Exception) {
            Log.e(LOG_ID, "addData exception: " + exc.toString() )
        }
    }

    fun needsData(): Boolean {
        return graphData.size < 5
    }

}