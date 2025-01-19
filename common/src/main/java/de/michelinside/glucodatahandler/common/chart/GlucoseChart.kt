package de.michelinside.glucodatahandler.common.chart

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.View
import com.github.mikephil.charting.charts.LineChart
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifySource

class GlucoseChart: LineChart {
    private val LOG_ID = "GDH.Chart.GlucoseChart"

    constructor(context: Context?) : super(context)

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    )

    override fun invalidate() {
        if(id == View.NO_ID) {
            id = generateViewId()
        }
        Log.v(LOG_ID, "start invalidate - notify ID: $id")
        super.invalidate()
        Log.i(LOG_ID, "invalidate finished - notify ID: $id")
        InternalNotifier.notify(context, NotifySource.GRAPH_CHANGED, Bundle().apply { putInt(Constants.GRAPH_ID, id) })
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        Log.d(LOG_ID, "onDetachedFromWindow for ID: $id")
    }
}