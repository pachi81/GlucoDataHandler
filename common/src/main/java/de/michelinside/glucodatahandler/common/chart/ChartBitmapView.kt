package de.michelinside.glucodatahandler.common.chart

import android.content.Context
import android.os.Bundle
import de.michelinside.glucodatahandler.common.utils.Log
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

class ChartBitmapView(val imageView: ImageView, val context: Context, width: Int = 1000, height: Int = 0, labelColor: Int = 0, forComplication: Boolean = false): NotifierInterface {

    private val LOG_ID = "GDH.Chart.BitmapView"

    private var chartBitmap: ChartBitmap
    init {
        Log.v(LOG_ID, "init")
        InternalNotifier.addNotifier(context, this, mutableSetOf(NotifySource.GRAPH_CHANGED))
        chartBitmap = ChartBitmap(context, width, height, labelColor = labelColor, forComplication = forComplication)
        imageView.setImageBitmap(chartBitmap.getBitmap())
        imageView.visibility = if(chartBitmap.enabled) View.VISIBLE else View.GONE
    }

    fun pause() {
        Log.v(LOG_ID, "pause")
        chartBitmap.pause()
    }

    fun resume() {
        Log.v(LOG_ID, "resume")
        chartBitmap.resume()
    }

    fun close() {
        Log.v(LOG_ID, "close")
        InternalNotifier.remNotifier(context, this)
        chartBitmap.close()
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        Log.d(LOG_ID, "OnNotifyData for $dataSource - id ${chartBitmap.chartId} - extras: ${Utils.dumpBundle(extras)}")
        if(dataSource == NotifySource.GRAPH_CHANGED && extras?.getInt(Constants.GRAPH_ID) == chartBitmap.chartId) {
            GlobalScope.launch(Dispatchers.Main) {
                try {
                    Log.i(LOG_ID, "Update bitmap for id ${chartBitmap.chartId} - enabled: ${chartBitmap.enabled}")
                    imageView.setImageBitmap(chartBitmap.getBitmap())
                    if(chartBitmap.enabled != (imageView.visibility== View.VISIBLE)) {
                        imageView.visibility = if(chartBitmap.enabled) View.VISIBLE else View.GONE
                    }
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "Update bitmap exception: " + exc.toString())
                }
            }
        }
    }
}