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

class ChartBitmap(val imageView: ImageView, val context: Context): NotifierInterface {

    private val LOG_ID = "GDH.Chart.Bitmap"

    private var chartViewer: ChartBitmapCreator
    private var chart: GlucoseChart = GlucoseChart(context)
    init {
        Log.v(LOG_ID, "init")
        chart.measure (View.MeasureSpec.makeMeasureSpec (1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec (400, View.MeasureSpec.EXACTLY))
        chart.layout (0, 0, chart.getMeasuredWidth(), chart.getMeasuredHeight())

        InternalNotifier.addNotifier(context, this, mutableSetOf(NotifySource.GRAPH_CHANGED))
        chartViewer = ChartBitmapCreator(chart, context)
        chartViewer.create()
    }

    fun close() {
        Log.v(LOG_ID, "close")
        InternalNotifier.remNotifier(context, this)
        chartViewer.close()
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        Log.d(LOG_ID, "OnNotifyData for $dataSource - id ${chart.id} - extras: ${Utils.dumpBundle(extras)}")
        if(dataSource == NotifySource.GRAPH_CHANGED && extras?.getInt(Constants.GRAPH_ID) == chart.id) {
            Log.i(LOG_ID, "Update bitmap")
            imageView.setImageBitmap(chartViewer.getBitmap())
        }
    }
}