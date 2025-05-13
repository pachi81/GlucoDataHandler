package de.michelinside.glucodatahandler

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import de.michelinside.glucodatahandler.common.chart.GlucoseChart

class WearGlucoseChart : GlucoseChart {
    private var LOG_ID = "GDH.Chart.WearGlucoseChart"

    constructor(context: Context?) : super(context)

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    )

    override fun canScrollHorizontally(direction: Int): Boolean {
        Log.d(LOG_ID, "canScrollHorizontally: $direction")
        return true
    }
}