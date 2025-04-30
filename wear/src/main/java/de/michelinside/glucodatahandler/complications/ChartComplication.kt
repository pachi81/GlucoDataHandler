package de.michelinside.glucodatahandler

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Icon
import android.os.Bundle
import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PhotoImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.chart.ChartBitmapHandler
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.receiver.ScreenEventReceiver
import de.michelinside.glucodatahandler.common.utils.BitmapPool
import de.michelinside.glucodatahandler.common.utils.IconBitmapPool
import de.michelinside.glucodatahandler.common.utils.PackageUtils
import de.michelinside.glucodatahandler.common.utils.Utils
import de.michelinside.glucodatahandler.common.R as CR


abstract class ChartComplicationBase: SuspendingComplicationDataSourceService() {

    companion object {
        protected val LOG_ID = "GDH.Chart.Complication"
        private var complications = mutableSetOf<Int>()
        private val size = 600

        fun getChartId(): Int {
            return ChartBitmapHandler.chartId
        }

        fun init() {
            complications.clear()
            removeBitmap()
        }

        private fun addComplication(id: Int) {
            if(GlucoDataService.isServiceRunning && id > 0) {
                if(!complications.contains(id)) {
                    Log.d(LOG_ID, "Add complication $id")
                    complications.add(id)
                }
                createBitmap()
            } else {
                Log.w(LOG_ID, "Ignore complication with $id - service running: ${GlucoDataService.isServiceRunning}")
            }
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
            if(GlucoDataService.isServiceRunning && !ChartBitmapHandler.isRegistered(LOG_ID)) {
                Log.i(LOG_ID, "Create bitmap")
                ChartBitmapHandler.register(GlucoDataService.context!!, LOG_ID)
                InternalNotifier.addNotifier(GlucoDataService.context!!, ChartComplicationUpdater, mutableSetOf(NotifySource.GRAPH_CHANGED, NotifySource.DISPLAY_STATE_CHANGED))
            }
        }

        private fun removeBitmap() {
            Log.i(LOG_ID, "Remove bitmap")
            ChartBitmapHandler.unregister(LOG_ID)
            InternalNotifier.remNotifier(GlucoDataService.context!!, ChartComplicationUpdater)
        }

        fun getBitmap(): Bitmap? {
            if(ChartBitmapHandler.isPaused(LOG_ID))
                return null
            return ChartBitmapHandler.getBitmap()
        }

        fun pauseBitmap() {
            Log.d(LOG_ID, "Pause bitmap")
            ChartBitmapHandler.pause(LOG_ID)
        }

        fun resumeBitmap() {
            Log.d(LOG_ID, "Resume bitmap")
            ChartBitmapHandler.resume(LOG_ID, false)  // resume, but do not create, wait for broadcast from phone
        }

        fun hasBitmap(): Boolean {
            return ChartBitmapHandler.hasBitmap()
        }

    }

    private var forPreview = false
    protected var instanceId = 0

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

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        try {
            Log.d(LOG_ID, "getPreviewData called for " + type.toString())
            forPreview = true
            val result = getComplicationData(ComplicationRequest(0, type, false))!!
            forPreview = false
            return result
        } catch (exc: Exception) {
            Log.e(LOG_ID, "getPreviewData exception: " + exc.message.toString())
        }
        forPreview = false
        return null
    }

    private fun getComplicationData(request: ComplicationRequest): ComplicationData? {
        instanceId = request.complicationInstanceId
        return when (request.complicationType) {
            ComplicationType.SMALL_IMAGE -> getSmallImageComplicationData(request.complicationInstanceId)
            ComplicationType.PHOTO_IMAGE -> getLargeImageComplicationData(request.complicationInstanceId)
            else -> {
                Log.e(
                    LOG_ID,
                    "Unsupported type for char: " + request.complicationType.toString()
                )
                null
            }
        }
    }

    private fun ambientGraphIcon(bitmap: Bitmap): Icon? {
        if (GlucoDataService.sharedPref != null && GlucoDataService.sharedPref!!.getBoolean(Constants.SHARED_PREF_WEAR_COLORED_AOD, false))
            return null  // use colored one!
        return IconBitmapPool.createIcon("ambientGraphIcon_$instanceId", monochromBitmap(bitmap))
    }

    protected abstract fun getPreview(): Int

    private fun getTransparentImage(): SmallImage {
        Log.d(LOG_ID, "using transparent image")
        return SmallImage.Builder(
            image = Icon.createWithResource(this, if(forPreview) getPreview() else CR.drawable.icon_empty),
            type = SmallImageType.PHOTO
        ).build()
    }

    protected fun getImage(resize: Boolean): SmallImage {
        val graph = getBitmap() ?: return getTransparentImage()
        val bitmap = if(resize) resize(graph) else graph
        val image = SmallImage.Builder(
            image = Icon.createWithBitmap(bitmap),
            type = SmallImageType.PHOTO
        ).setAmbientImage(ambientGraphIcon(bitmap))
            .build()
        if(resize) {
            BitmapPool.returnBitmap(bitmap)
        }
        return image
    }

    protected fun getLargeImage(): Icon {
        val graph = getBitmap() ?: return Icon.createWithResource(this, if(forPreview) getPreview() else CR.drawable.icon_transparent)
        return IconBitmapPool.createIcon("LargeResizeGraph_$instanceId", resize(graph, 800))
    }

    private fun resize(originalImage: Bitmap, bitmapSize: Int = size): Bitmap {
        Log.v(LOG_ID, "resize: $bitmapSize")
        val compBitmap = BitmapPool.getBitmap(bitmapSize, bitmapSize, Bitmap.Config.ARGB_8888)

        val originalWidth: Float = originalImage.getWidth().toFloat()
        val originalHeight: Float = originalImage.getHeight().toFloat()

        val canvas = Canvas(compBitmap)

        val xTranslation = if(bitmapSize>originalWidth) ((bitmapSize-originalWidth)/2f) else 0.0f
        val yTranslation = (bitmapSize - originalHeight) / 2f

        Log.d(LOG_ID, "size: $bitmapSize, width: $originalWidth, height: $originalHeight, xTranslation: $xTranslation, yTranslation: $yTranslation")

        val transformation = Matrix()
        transformation.postTranslate(xTranslation, yTranslation)
        transformation.preScale(1f, 1f)

        val paint = Paint()
        paint.isFilterBitmap = true

        canvas.drawBitmap(originalImage, transformation, paint)

        return compBitmap
    }

    private fun monochromBitmap(bitmap: Bitmap): Bitmap {
        Log.v(LOG_ID, "Creating monochromBitmap")
        val compBitmap = BitmapPool.getBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(compBitmap)
        val paint = Paint()
        paint.isFilterBitmap = true
        paint.setColorFilter(PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP))
        canvas.drawBitmap(bitmap, 0F, 0F, paint)
        return compBitmap
    }

    protected abstract fun getSmallImageComplicationData(complicationInstanceId: Int): ComplicationData?
    protected open fun getLargeImageComplicationData(complicationInstanceId: Int): ComplicationData? = null

    protected fun getTapAction(
        complicationInstanceId: Int
    ): PendingIntent {
        return PackageUtils.getAppIntent(
            applicationContext,
            if(ChartBitmapHandler.hasBitmap()) GraphActivity::class.java else WearActivity::class.java,
            complicationInstanceId
        )
    }
}

class ChartComplication: ChartComplicationBase() {
    override fun getPreview(): Int = R.drawable.graph_comp_square_trans
    override fun getSmallImageComplicationData(complicationInstanceId: Int): ComplicationData {
        return SmallImageComplicationData.Builder (
            smallImage = getImage(true),
            contentDescription = PlainComplicationText.Builder(applicationContext.resources.getString(CR.string.graph)).build()
        )
            .setTapAction(getTapAction(complicationInstanceId))
            .build()
    }

    override fun getLargeImageComplicationData(complicationInstanceId: Int): ComplicationData {
        return PhotoImageComplicationData.Builder (
            photoImage = getLargeImage(),
            contentDescription = PlainComplicationText.Builder(applicationContext.resources.getString(CR.string.graph)).build()
        )
        .setTapAction(getTapAction(complicationInstanceId))
        .build()
    }
}

class ChartComplicationRect: ChartComplicationBase() {
    override fun getPreview(): Int = R.drawable.graph_comp_rect_trans
    override fun getSmallImageComplicationData(complicationInstanceId: Int): ComplicationData {
        return SmallImageComplicationData.Builder (
            smallImage = getImage(false),
            contentDescription = PlainComplicationText.Builder(applicationContext.resources.getString(CR.string.graph)).build()
        )
            .setTapAction(getTapAction(complicationInstanceId))
            .build()
    }
}

object ChartComplicationUpdater: NotifierInterface {
    private var complicationClasses = mutableListOf(ChartComplication::class.java, ChartComplicationRect::class.java)
    val LOG_ID = "GDH.Chart.ComplicationUpdater"

    fun init(context: Context) {
        Log.i(LOG_ID, "init chart complications")
        ChartComplicationBase.init()
        OnNotifyData(context, NotifySource.CAPILITY_INFO, null)
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        Log.d(LOG_ID, "OnNotifyData called for source $dataSource with extras ${Utils.dumpBundle(extras)} - graph-id ${ChartComplicationBase.getChartId()}" )
        if(dataSource==NotifySource.DISPLAY_STATE_CHANGED) {
            if(ScreenEventReceiver.isDisplayOff())
                ChartComplicationBase.pauseBitmap()
            else {
                ChartComplicationBase.resumeBitmap()
                return
            }
        }
        if (dataSource == NotifySource.GRAPH_CHANGED && extras?.getInt(Constants.GRAPH_ID) != ChartComplicationBase.getChartId()) {
            Log.v(LOG_ID, "Ignore graph changed as it is not for this chart")
            return  // ignore as it is not for this graph
        }
        if(dataSource == NotifySource.TIME_VALUE && ChartComplicationBase.hasBitmap() && ReceiveData.getElapsedTimeMinute().mod(2) == 0) {
            Log.d(LOG_ID, "Ignore time value and wait for chart update")
            return
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