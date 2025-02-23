package de.michelinside.glucodatahandler

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.drawable.Icon
import android.os.Bundle
import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.chart.ChartBitmap
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.PackageUtils
import de.michelinside.glucodatahandler.common.utils.Utils


open class ChartComplication(): SuspendingComplicationDataSourceService() {

    companion object {
        protected val LOG_ID = "GDH.Chart.Complication"
        @SuppressLint("StaticFieldLeak")
        private var chartBitmap: ChartBitmap? = null
        private var complications = mutableSetOf<Int>()
        private val size = 600

        fun getChartId(): Int {
            if(chartBitmap != null)
                return chartBitmap!!.chartId
            return -1
        }

        fun init() {
            complications.clear()
            removeBitmap()
        }

        private fun addComplication(id: Int) {
            if(GlucoDataService.isServiceRunning && !complications.contains(id)) {
                Log.d(LOG_ID, "Add complication $id")
                complications.add(id)
            }
            createBitmap()
        }

        private fun remComplication(id: Int) {
            if(complications.contains(id)) {
                Log.d(LOG_ID, "Remove complication $id")
                complications.remove(id)
                if(complications.isEmpty())
                    removeBitmap()
            }
        }

        private fun createBitmap() {
            if(chartBitmap == null && GlucoDataService.isServiceRunning) {
                Log.i(LOG_ID, "Create bitmap")
                chartBitmap = ChartBitmap(GlucoDataService.context!!, Constants.SHARED_PREF_GRAPH_DURATION_WEAR_COMPLICATION, size, 250, true)
                InternalNotifier.addNotifier(GlucoDataService.context!!, ChartComplicationUpdater, mutableSetOf(NotifySource.GRAPH_CHANGED))
            }
        }

        private fun removeBitmap() {
            if(chartBitmap != null) {
                Log.i(LOG_ID, "Remove bitmap")
                InternalNotifier.remNotifier(GlucoDataService.context!!, ChartComplicationUpdater)
                chartBitmap!!.close()
                chartBitmap = null
            }
        }

    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        try {
            Log.d(
                LOG_ID,
                "onComplicationRequest called for ID " + request.complicationInstanceId.toString() + " with type " + request.complicationType.toString()
            )
            GlucoDataServiceWear.start(this)
            addComplication(request.complicationInstanceId)
            return getComplicationData(request)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onComplicationRequest exception: " + exc.message.toString())
        }
        return null
    }

    override fun onComplicationDeactivated(complicationInstanceId: Int) {
        try {
            Log.d(LOG_ID, "onComplicationDeactivated called for id " + complicationInstanceId)
            super.onComplicationDeactivated(complicationInstanceId)
            remComplication(complicationInstanceId)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onComplicationDeactivated exception: " + exc.message.toString())
        }
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData {
        Log.d(LOG_ID, "onComplicationRequest called for " + type.toString())
        return getComplicationData(ComplicationRequest(0, type, false))!!
    }

    private fun getComplicationData(request: ComplicationRequest): ComplicationData? {
        return when (request.complicationType) {
            ComplicationType.SMALL_IMAGE -> getSmallImageComplicationData(request.complicationInstanceId)
            else -> {
                Log.e(
                    LOG_ID,
                    "Unsupported type for char: " + request.complicationType.toString()
                )
                null
            }
        }
    }

    private fun getImage(graph: Bitmap): SmallImage {
        val icon = Icon.createWithBitmap(resize(graph))
        return SmallImage.Builder(
            image = icon,
            type = SmallImageType.PHOTO
        ).setAmbientImage(icon)
            .build()
    }

    private fun resize(originalImage: Bitmap): Bitmap {
        val background = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)

        val originalWidth: Float = originalImage.getWidth().toFloat()
        val originalHeight: Float = originalImage.getHeight().toFloat()

        val canvas = Canvas(background)

        val scale = size / originalWidth

        val xTranslation = 0.0f
        val yTranslation = (size - originalHeight * scale) / 2f

        Log.v(LOG_ID, "scale: $scale, xTranslation: $xTranslation, yTranslation: $yTranslation")

        val transformation = Matrix()
        transformation.postTranslate(xTranslation, yTranslation)
        transformation.preScale(scale, scale)

        val paint = Paint()
        paint.isFilterBitmap = true

        canvas.drawBitmap(originalImage, transformation, paint)

        return background
    }

    private fun getSmallImageComplicationData(complicationInstanceId: Int): ComplicationData? {
        val graph = chartBitmap?.getBitmap()
        if(graph == null) {
            return null
        }
        //  else
        return SmallImageComplicationData.Builder (
            smallImage = getImage(graph),
            contentDescription = PlainComplicationText.Builder("glucose graph").build()
        )
            .setTapAction(getTapAction(complicationInstanceId))
            .build()
    }

    private fun getTapAction(
        complicationInstanceId: Int
    ): PendingIntent {
        return PackageUtils.getAppIntent(
            applicationContext,
            if(chartBitmap != null && chartBitmap!!.enabled) GraphActivity::class.java else GraphActivity::class.java,
            complicationInstanceId
        )
    }
}

object ChartComplicationUpdater: NotifierInterface {
    private var complicationClasses = mutableListOf(ChartComplication::class.java)
    val LOG_ID = "GDH.Chart.ComplicationUpdater"

    fun init(context: Context) {
        Log.i(LOG_ID, "init chart complications")
        ChartComplication.init()
        OnNotifyData(context, NotifySource.CAPILITY_INFO, null)
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        Log.d(LOG_ID, "OnNotifyData called for source $dataSource with extras ${Utils.dumpBundle(extras)} - graph-id ${ChartComplication.getChartId()}" )
        if (dataSource == NotifySource.GRAPH_CHANGED && extras?.getInt(Constants.GRAPH_ID) != ChartComplication.getChartId()) {
            Log.v(LOG_ID, "Ignore graph changed as it is not for this chart")
            return  // ignore as it is not for this graph
        }
        complicationClasses.forEach {
            Log.d(LOG_ID, "Trigger complication update for ${it.name}")
            ComplicationDataSourceUpdateRequester
                .create(
                    context = context,
                    complicationDataSourceComponent = ComponentName(
                        context,
                        it
                    )
                )
                .requestUpdateAll()
        }
    }
}