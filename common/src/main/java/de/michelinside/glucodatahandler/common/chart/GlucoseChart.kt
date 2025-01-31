package de.michelinside.glucodatahandler.common.chart

import android.content.Context
import android.graphics.Canvas
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.View
import com.github.mikephil.charting.charts.LineChart

class GlucoseChart: LineChart {
    private var LOG_ID = "GDH.Chart.GlucoseChart"

    constructor(context: Context?) : super(context)

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    )

    private var isInvalidating = false
    init {
        LOG_ID = "GDH.Chart.GlucoseChart." + id.toString()
    }

    override fun getId(): Int {
        if(super.getId() == View.NO_ID) {
            id = generateViewId()
        }
        return super.getId()
    }

    override fun postInvalidate() {
        Log.v(LOG_ID, "postInvalidate")
        isInvalidating = true
        super.postInvalidate()
        Log.v(LOG_ID, "postInvalidate done")
    }

    override fun invalidate() {
        isInvalidating = true
        try {
            Log.v(LOG_ID, "invalidate - shown: $isShown")
            super.invalidate()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "invalidate exception: ${exc.message}\n${exc.stackTraceToString()}")
        }
        isInvalidating = false
    }

    fun waitForInvalidate() {
        if(isInvalidating && Looper.myLooper() != Looper.getMainLooper()) {
            Log.d(LOG_ID, "waitForDrawing")
            while(isInvalidating) {
                Thread.sleep(10)
            }
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
            Log.v(LOG_ID, "onDraw - min: ${xAxis.axisMinimum} - max: ${xAxis.axisMaximum}")
            super.onDraw(canvas)
            Log.v(LOG_ID, "drawn")
        } catch (exc: Exception) {
            if(xAxis.axisMinimum > xAxis.axisMaximum) {  // ignore this, as it is caused during reset and draw...
                Log.w(LOG_ID, "onDraw exception: ${exc.message}")
            } else {
                Log.e(LOG_ID, "onDraw exception: ${exc.message}\n${exc.stackTraceToString()}")
            }
        }
    }

}