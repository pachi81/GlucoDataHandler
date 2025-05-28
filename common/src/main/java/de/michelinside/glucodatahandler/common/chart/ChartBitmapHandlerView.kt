package de.michelinside.glucodatahandler.common.chart

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.Utils
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class ChartBitmapHandlerView(val imageView: ImageView, val context: Context): NotifierInterface {

    private val LOG_ID = "GDH.Chart.BitmapHandlerView"
    private val widget: String

    init {
        Log.v(LOG_ID, "init $this")
        widget = this.toString()
        InternalNotifier.addNotifier(context, this, mutableSetOf(NotifySource.GRAPH_CHANGED))
        ChartBitmapHandler.register(context, widget)
        imageView.setImageBitmap(ChartBitmapHandler.getBitmap())
        imageView.visibility = if(ChartBitmapHandler.hasBitmap(widget)) View.VISIBLE else View.GONE
    }

    fun pause() {
        Log.v(LOG_ID, "pause")
        ChartBitmapHandler.pause(widget)
    }

    fun resume() {
        Log.v(LOG_ID, "resume")
        ChartBitmapHandler.resume(widget)
        updateVisibility()
    }

    fun close() {
        Log.v(LOG_ID, "close")
        InternalNotifier.remNotifier(context, this)
        ChartBitmapHandler.unregister(widget)
    }

    private fun updateVisibility() {
        if(ChartBitmapHandler.hasBitmap(widget) != (imageView.visibility== View.VISIBLE)) {
            Log.i(LOG_ID, "Visibility changed for widget $widget - enabled: ${ChartBitmapHandler.hasBitmap(widget)}")
            imageView.visibility = if(ChartBitmapHandler.hasBitmap(widget)) View.VISIBLE else View.GONE
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        Log.d(LOG_ID, "OnNotifyData for $dataSource - id ${ChartBitmapHandler.chartId} - extras: ${Utils.dumpBundle(extras)}")
        if(dataSource == NotifySource.GRAPH_CHANGED && extras?.getInt(Constants.GRAPH_ID) == ChartBitmapHandler.chartId) {
            GlobalScope.launch(Dispatchers.Main) {
                try {
                    Log.i(LOG_ID, "Update bitmap for id ${ChartBitmapHandler.chartId} - enabled: ${ChartBitmapHandler.hasBitmap(widget)}")
                    imageView.setImageBitmap(ChartBitmapHandler.getBitmap())
                    updateVisibility()
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Update bitmap exception: " + exc.toString())
                }
            }
        }
    }
}