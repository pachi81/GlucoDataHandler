package de.michelinside.glucodatahandler.common.chart

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.util.Log
import android.view.View
import com.github.mikephil.charting.charts.LineChart

class GlucoseChart: LineChart {
    private val LOG_ID = "GDH.Chart.GlucoseChart"

    constructor(context: Context?) : super(context)

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    )

    private var processing = false

    override fun getId(): Int {
        if(super.getId() == View.NO_ID) {
            id = generateViewId()
        }
        return super.getId()
    }

    override fun invalidate() {
        processing = true
        try {
            super.invalidate()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "invalidate exception: ${exc.message}\n${exc.stackTraceToString()}")
        }
        processing = false
    }


    fun waitForInvalidate() {
        while(processing) {
            Thread.sleep(10)
        }
    }

    override fun onDetachedFromWindow() {
        try {
            super.onDetachedFromWindow()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onDetachedFromWindow exception: ${exc.message}\n${exc.stackTraceToString()}")
        }
        Log.d(LOG_ID, "onDetachedFromWindow for ID: $id")
    }

    override fun onDraw(canvas: Canvas) {
        try {
            super.onDraw(canvas)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onDraw exception: ${exc.message}\n${exc.stackTraceToString()}")
        }
    }

}