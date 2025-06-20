package de.michelinside.glucodatahandler.common.chart

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.jobs.MoveViewJob

open class GlucoseChart: LineChart {
    private var LOG_ID = "GDH.Chart.GlucoseChart"

    constructor(context: Context?) : super(context)

    constructor(context: Context?, id: Int) : super(context) {
        this.id = id
        LOG_ID = "GDH.Chart.GlucoseChart." + id.toString()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    )

    init {
        if(id != View.NO_ID) {
            LOG_ID = "GDH.Chart.GlucoseChart." + id.toString()
        }
    }

    private var isInvalidating = false
    private var isDrawing = false

    override fun postInvalidate() {
        //Log.v(LOG_ID, "postInvalidate - shown: $isShown")
        isInvalidating = true
        isDrawing = isShown
        super.postInvalidate()
        //Log.v(LOG_ID, "postInvalidate done")
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        try {
            super.onSizeChanged(w, h, oldw, oldh)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSizeChanged exception: ${exc.message}:\n${exc.stackTraceToString()}")
        }
    }

    override fun invalidate() {
        isInvalidating = true
        isDrawing = isShown
        try {
            //Log.v(LOG_ID, "invalidate - shown: $isShown")
            super.invalidate()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "invalidate exception: ${exc.message}\n${exc.stackTraceToString()}")
        }
        isInvalidating = false
    }

    fun waitForInvalidate() {
        if(isInvalidating && Looper.myLooper() != Looper.getMainLooper()) {
            Log.d(LOG_ID, "waitForInvalidate")
            var sleepCount = 0
            while(isInvalidating && sleepCount < 3000) {
                Thread.sleep(10)
                sleepCount += 10
            }
            if(isInvalidating) {
                Log.e(LOG_ID, "waitForInvalidate timeout")
            }
        }
    }

    fun waitForDrawing() {
        if(isDrawing && Looper.myLooper() != Looper.getMainLooper()) {
            Log.d(LOG_ID, "waitForDrawing")
            var sleepCount = 0
            while(isDrawing && sleepCount < 1000) {
                Thread.sleep(10)
                sleepCount += 10
            }
            if(isDrawing) {
                Log.e(LOG_ID, "waitForDrawing timeout")
            }
        }
    }

    fun enableTouch() {
        if(!mTouchEnabled) {
            Log.d(LOG_ID, "enable touch")
            waitForDrawing()
            setTouchEnabled(true)
        }
    }

    public override fun onDetachedFromWindow() {
        try {
            super.onDetachedFromWindow()
            MoveViewJob.getInstance(null, 0F, 0F, null, null)
            isInvalidating = false
            isDrawing = false
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onDetachedFromWindow exception: ${exc.message}\n${exc.stackTraceToString()}")
        }
        Log.d(LOG_ID, "onDetachedFromWindow for ID: $id")
    }

    override fun onDraw(canvas: Canvas) {
        try {
            isDrawing = true
            //Log.v(LOG_ID, "onDraw - min: ${xAxis.axisMinimum} - max: ${xAxis.axisMaximum}")
            super.onDraw(canvas)
            //Log.v(LOG_ID, "drawn")
        } catch (exc: Exception) {
            if(xAxis.axisMinimum > xAxis.axisMaximum) {  // ignore this, as it is caused during reset and draw...
                Log.w(LOG_ID, "onDraw exception: ${exc.message}")
            } else {
                Log.e(LOG_ID, "onDraw exception: ${exc.message}\n${exc.stackTraceToString()}")
            }
        }
        isDrawing = false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        try {
            return super.onTouchEvent(event)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onTouchEvent exception: ${exc.message}:\n${exc.stackTraceToString()}")
        }
        return false
    }

    override fun performClick(): Boolean {
        try {
            return super.performClick()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "performClick exception: ${exc.message}:\n${exc.stackTraceToString()}")
        }
        return false
    }
}