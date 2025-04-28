package de.michelinside.glucodatahandler.common.chart

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import com.github.mikephil.charting.charts.LineChart.generateViewId
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.BitmapPool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean

class ChartBitmap(val context: Context,
                  val width: Int = 1000,
                  val height: Int = 0,
                  val forComplication: Boolean = false,
                  val labelColor: Int = 0) : NotifierInterface, SharedPreferences.OnSharedPreferenceChangeListener {

    private var LOG_ID = "GDH.Chart.Bitmap."

    private var chartViewer: ChartBitmapCreator? = null
    private var chart: GlucoseChart? = null
    private val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
    private var bitmap: Bitmap? = null
    private var createBitmapJob: Thread? = null
    private val viewId = generateViewId()
    private var paused = false
    private var jobCanceled = AtomicBoolean(false)

    val enabled: Boolean get() {
        return chartViewer?.enabled?: false
    }

    val chartId: Int get() {
        return viewId
    }

    val isPaused: Boolean get() {
        return paused
    }

    init {
        try {
            LOG_ID += viewId.toString()
            initNotifier()
            create()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "init exception: " + exc.message + " - " + exc.stackTraceToString())
        }
    }

    private fun initNotifier() {
        InternalNotifier.addNotifier(context, this, mutableSetOf(NotifySource.TIME_VALUE, NotifySource.BROADCAST, NotifySource.MESSAGECLIENT))
        sharedPref.registerOnSharedPreferenceChangeListener(this)
    }

    private fun remNotifier() {
        InternalNotifier.remNotifier(context, this)
        sharedPref.unregisterOnSharedPreferenceChangeListener(this)
    }

    private fun create() {
        if(chart != null)
            destroy()
        val viewHeight = if(height > 0) height else width/3
        Log.d(LOG_ID, "create - width: $width - height: $viewHeight")
        chart = GlucoseChart(context, viewId)
        chart!!.measure (View.MeasureSpec.makeMeasureSpec (width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec (viewHeight, View.MeasureSpec.EXACTLY))
        chart!!.layout (0, 0, chart!!.getMeasuredWidth(), chart!!.getMeasuredHeight())
        chartViewer = ChartBitmapCreator(chart!!, context, forComplication)
        chartViewer!!.labelColor = labelColor
        chartViewer!!.create(true)
        waitForCreation()
    }

    private fun waitForCreation() {
        stopCreation()
        jobCanceled.set(false)
        createBitmapJob = Thread {
            try {
                try {
                    Log.d(LOG_ID, "waitForCreation - wait for creation")
                    chartViewer?.waitForCreation()
                    if(jobCanceled.get()) {
                        Log.d(LOG_ID, "waitForCreation cancelled before bitmap creation")
                        return@Thread
                    }
                    Log.d(LOG_ID, "waitForCreation - create bitmap")
                    bitmap = chartViewer?.createBitmap(bitmap)
                } catch (exc: CancellationException) {
                    Log.d(LOG_ID, "waitForCreation cancelled")
                } catch (exc: InterruptedException) {
                    Log.d(LOG_ID, "waitForCreation interrupted")
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "waitForCreation exception: " + exc.message + " - " + exc.stackTraceToString())
                }
                if(jobCanceled.get()) {
                    Log.d(LOG_ID, "waitForCreation cancelled before notify")
                    return@Thread
                }
                Handler(context.mainLooper).post {
                    if(!jobCanceled.get()) {
                        Log.d(LOG_ID, "notify graph changed")
                        InternalNotifier.notify(context, NotifySource.GRAPH_CHANGED, Bundle().apply { putInt(Constants.GRAPH_ID, chartId) })
                    }
                    createBitmapJob = null
                }
            } catch (exc: CancellationException) {
                Log.d(LOG_ID, "waitForCreation cancelled")
            } catch (exc: InterruptedException) {
                Log.d(LOG_ID, "waitForCreation interrupted")
            } catch (exc: Exception) {
                Log.e(LOG_ID, "waitForCreation exception: " + exc.message + " - " + exc.stackTraceToString())
            }
            Log.d(LOG_ID, "waitForCreation - end")
        }
        createBitmapJob?.start()
    }

    fun isCreating(): Boolean {
        try {
            return createBitmapJob != null && createBitmapJob?.isAlive == true
        } catch (exc: Exception) {
            return false
        }
    }

    private fun stopCreation() {
        try {
            jobCanceled.set(true)
            if(isCreating()) {
                Log.i(LOG_ID, "stop bitmap creation")
                createBitmapJob?.interrupt()
                Log.d(LOG_ID, "stop bitmap creation - wait for current execution")
                runBlocking {
                    if(isCreating()) {
                        Log.d(LOG_ID, "stop bitmap creation - wait for current execution")
                        createBitmapJob?.join()
                    }
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "stopCreation exception: " + exc.message + " - " + exc.stackTraceToString())
        }
        Log.d(LOG_ID, "stopCreation - end")
    }

    private fun destroy() {
        Log.d(LOG_ID, "destroy")
        stopCreation()
        chartViewer?.close()
        if(chart != null) {
            chart!!.onDetachedFromWindow()
            chart = null
        }
    }

    fun recreate() {
        try {
            Log.d(LOG_ID, "recreate")
            destroy()
            create()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "recreate exception: " + exc.message + " - " + exc.stackTraceToString())
        }
    }

    fun close() {
        try {
            remNotifier()
            destroy()
            BitmapPool.returnBitmap(bitmap)
            bitmap = null
        } catch (exc: Exception) {
            Log.e(LOG_ID, "close exception: " + exc.message + " - " + exc.stackTraceToString())
        }
    }

    fun getBitmap(): Bitmap? {
        return bitmap
    }

    fun pause() {
        Log.d(LOG_ID, "pause")
        paused = true
    }

    fun resume() {
        if(paused) {
            Log.d(LOG_ID, "resume")
            paused = false
            recreate()
        }
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            Log.d(LOG_ID, "OnNotifyData - source: $dataSource - paused: $paused")
            if(paused)
                return
            if(dataSource == NotifySource.TIME_VALUE) {
                Log.d(LOG_ID, "time elapsed: ${ReceiveData.getElapsedTimeMinute()}")
                if(ReceiveData.getElapsedTimeMinute().mod(2) == 0) {
                    Log.d(LOG_ID, "update graph after time elapsed")
                    recreate()
                }
            } else {
                recreate()
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: " + exc.message + " - " + exc.stackTraceToString())
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        try {
            Log.d(LOG_ID, "onSharedPreferenceChanged: $key - paused: $paused")
            if(!paused && chartViewer != null && key != null && chartViewer!!.isGraphPref(key)) {
                recreate()
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.message + " - " + exc.stackTraceToString())
        }
    }

}