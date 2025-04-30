package de.michelinside.glucodatahandler.common.chart

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import android.view.View
import de.michelinside.glucodatahandler.common.AppSource
import de.michelinside.glucodatahandler.common.GlucoDataService

object ChartBitmapHandler {
    private val LOG_ID = "GDH.chart.BitmapHandler"
    private val activeWidgets = mutableSetOf<String>()
    private val pausedWidgets = mutableSetOf<String>()
    @SuppressLint("StaticFieldLeak")
    private var chartBitmap: ChartBitmap? = null


    private val enabled: Boolean get() {
        return activeWidgets.isNotEmpty() || pausedWidgets.isNotEmpty()
    }

    val active: Boolean get() {
        return enabled && chartBitmap != null
    }

    val chartId: Int get() {
        return chartBitmap?.chartId?: View.NO_ID
    }

    fun isRegistered(widget: String): Boolean {
        return activeWidgets.contains(widget) || pausedWidgets.contains(widget)
    }

    fun register(context: Context, widget: String) {
        Log.i(LOG_ID, "register widget $widget")
        activeWidgets.add(widget)
        createBitmap(context)
    }

    fun unregister(widget: String) {
        Log.i(LOG_ID, "unregister widget $widget")
        activeWidgets.remove(widget)
        if(!enabled)
            removeBitmap()
    }

    private fun createBitmap(context: Context) {
        if(chartBitmap == null && GlucoDataService.isServiceRunning) {
            Log.i(LOG_ID, "Create bitmap")
            if(GlucoDataService.appSource == AppSource.WEAR_APP)
                chartBitmap = ChartBitmap(context, width = 600, forComplication = true, labelColor = Color.WHITE)
            else
                chartBitmap = ChartBitmap(context, labelColor = Color.WHITE)
        }
    }

    private fun removeBitmap() {
        if(chartBitmap != null) {
            Log.i(LOG_ID, "Remove bitmap")
            chartBitmap!!.close()
            chartBitmap = null
        }
    }

    fun hasBitmap(): Boolean {
        return chartBitmap != null
    }

    fun getBitmap(): Bitmap? {
        return chartBitmap?.getBitmap()
    }

    fun isCreating(): Boolean {
        return chartBitmap?.isCreating()?: false
    }

    fun isPaused(widget: String = ""): Boolean {
        if(widget.isNotEmpty())
            return pausedWidgets.contains(widget)
        return chartBitmap?.isPaused?: false
    }

    fun pause(widget: String) {
        Log.d(LOG_ID, "pause widget $widget")
        pausedWidgets.add(widget)
        activeWidgets.remove(widget)
        if(activeWidgets.isEmpty()) {
            Log.i(LOG_ID, "Pause bitmap")
            chartBitmap?.pause()
        }
    }

    fun resume(widget: String, create: Boolean = true) {
        Log.d(LOG_ID, "resume widget $widget - create: $create")
        activeWidgets.add(widget)
        pausedWidgets.remove(widget)
        if(chartBitmap?.isPaused == true)
            chartBitmap?.resume(create)
    }

    fun recreate() {
        Log.d(LOG_ID, "recreate")
        chartBitmap?.recreate()
    }

}