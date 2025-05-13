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

    private val LOG_ID = "GDH.Chart.ChartBitmapHandlerView"

    init {
        Log.v(LOG_ID, "init")
        InternalNotifier.addNotifier(context, this, mutableSetOf(NotifySource.GRAPH_CHANGED))
        ChartBitmapHandler.register(context, this.toString())
        imageView.setImageBitmap(ChartBitmapHandler.getBitmap())
        imageView.visibility = if(ChartBitmapHandler.hasBitmap()) View.VISIBLE else View.GONE
    }

    fun pause() {
        Log.v(LOG_ID, "pause")
        ChartBitmapHandler.pause(this.toString())
    }

    fun resume() {
        Log.v(LOG_ID, "resume")
        ChartBitmapHandler.resume(this.toString())
    }

    fun close() {
        Log.v(LOG_ID, "close")
        InternalNotifier.remNotifier(context, this)
        ChartBitmapHandler.unregister(this.toString())
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        Log.d(LOG_ID, "OnNotifyData for $dataSource - id ${ChartBitmapHandler.chartId} - extras: ${Utils.dumpBundle(extras)}")
        if(dataSource == NotifySource.GRAPH_CHANGED && extras?.getInt(Constants.GRAPH_ID) == ChartBitmapHandler.chartId) {
            GlobalScope.launch(Dispatchers.Main) {
                try {
                    Log.i(LOG_ID, "Update bitmap for id ${ChartBitmapHandler.chartId} - enabled: ${ChartBitmapHandler.hasBitmap()}")
                    imageView.setImageBitmap(ChartBitmapHandler.getBitmap())
                    if(ChartBitmapHandler.hasBitmap() != (imageView.visibility== View.VISIBLE)) {
                        imageView.visibility = if(ChartBitmapHandler.hasBitmap()) View.VISIBLE else View.GONE
                    }
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Update bitmap exception: " + exc.toString())
                }
            }
        }
    }
}