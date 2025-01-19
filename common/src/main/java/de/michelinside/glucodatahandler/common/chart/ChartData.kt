package de.michelinside.glucodatahandler.common.chart

import com.github.mikephil.charting.data.Entry
import de.michelinside.glucodatahandler.common.utils.DummyGraphData

object ChartData {
    private var graphData = DummyGraphData.create(48, min = 40, max = 260, stepMinute = 5).toMutableMap()

    fun getData(minTime: Long = 0L): ArrayList<Entry> {
        val entries = ArrayList<Entry>()
        graphData.forEach { (t, u) ->
            if(t >= minTime) {
                entries.add(Entry(TimeValueFormatter.to_chart_x(t), u.toFloat()))
            }
        }
        return entries
    }

    fun addData(time: Long, value: Int) {
        graphData[time] = value
    }

}