package de.michelinside.glucodatahandler.common.chart

import com.github.mikephil.charting.data.Entry

object ChartData {
    fun getData(count: Int): ArrayList<Entry> {
        val entries = ArrayList<Entry>()
        val min = 50F
        val max = 300F
        var value = min
        var delta = 10F
        for (i in 0 until count) {
            entries.add(Entry(i.toFloat(), value))
            value += delta
            if(value >= max)
                delta = -10F
            if(value <= min)
                delta = +10F
        }
        return entries
    }

}