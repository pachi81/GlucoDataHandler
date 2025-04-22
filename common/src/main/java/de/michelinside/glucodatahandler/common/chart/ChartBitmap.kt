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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var createBitmapJob: Job? = null
    private val viewId = generateViewId()

    val enabled: Boolean get() {
        return chartViewer?.enabled?: false
    }

    val chartId: Int get() {
        return viewId
    }

    init {
        LOG_ID += viewId.toString()
        initNotifier()
        create()
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
        createBitmapJob = scope.launch {
            try {
                chartViewer?.waitForCreation()
                bitmap = chartViewer?.createBitmap(bitmap)
            } catch (exc: Exception) {
                Log.e(LOG_ID, "waitForCreation exception: " + exc.message + " - " + exc.stackTraceToString())
                if(bitmap != null) {
                    BitmapPool.returnBitmap(bitmap)
                    bitmap = null
                }
            }
            Handler(context.mainLooper).post {
                Log.d(LOG_ID, "notify graph changed")
                InternalNotifier.notify(context, NotifySource.GRAPH_CHANGED, Bundle().apply { putInt(Constants.GRAPH_ID, chartId) })
            }
        }
    }

    private fun stopCreation() {
        if(createBitmapJob != null && createBitmapJob!!.isActive) {
            createBitmapJob!!.cancel()
            if(createBitmapJob!!.isActive) {
                runBlocking {
                    Log.d(LOG_ID, "stop data sync - wait for current execution")
                    createBitmapJob!!.join()
                }
            }
        }
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
        Log.d(LOG_ID, "recreate")
        destroy()
        create()
    }

    fun close() {
        remNotifier()
        destroy()
        BitmapPool.returnBitmap(bitmap)
        bitmap = null
    }

    fun getBitmap(): Bitmap? {
        return bitmap
    }

    fun pause() {
        Log.v(LOG_ID, "pause")
        //chartViewer?.pause()
    }

    fun resume() {
        Log.v(LOG_ID, "resume")
        //chartViewer?.resume()
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        Log.d(LOG_ID, "OnNotifyData - source: $dataSource")
        if(dataSource == NotifySource.TIME_VALUE) {
            Log.d(LOG_ID, "time elapsed: ${ReceiveData.getElapsedTimeMinute()}")
            if(ReceiveData.getElapsedTimeMinute().mod(2) == 0) {
                Log.d(LOG_ID, "update graph after time elapsed")
                recreate()
            }
        } else {
            recreate()
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        Log.d(LOG_ID, "onSharedPreferenceChanged: $key")
        if(chartViewer != null && key != null && chartViewer!!.isGraphPref(key)) {
            recreate()
        }
    }

}