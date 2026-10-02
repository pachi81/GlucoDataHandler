package de.michelinside.glucodatahandler.common.chart

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import androidx.core.graphics.createBitmap
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.BitmapUtils
import de.michelinside.glucodatahandler.common.utils.Log

// Caches the glucose-value and trend-arrow bitmaps, mirroring ChartBitmapHandler's
// register/unregister lifecycle: consumers register/unregister a widget id, and the cached
// bitmaps are only re-rendered when the underlying glucose data actually changes (via
// OnNotifyData), instead of on every tile request. Bitmaps are cached per requested size since
// different tiles ask for different dimensions.
object ValueBitmapHandler : NotifierInterface {
    private const val LOG_ID = "GDH.ValueBitmapHandler"
    private val activeWidgets = mutableSetOf<String>()
    private val valueCache = mutableMapOf<Pair<Int, Int>, Bitmap>()
    private val arrowCache = mutableMapOf<Pair<Int, Int>, Bitmap>()
    // Value+arrow drawn side by side (inline) into a single bitmap, keyed by (valueW, valueH, arrowW, arrowH).
    private val comboCache = mutableMapOf<ComboKey, Bitmap>()

    private data class ComboKey(val valueWidth: Int, val valueHeight: Int, val arrowWidth: Int, val arrowHeight: Int)

    private val filter = mutableSetOf(
        NotifySource.MESSAGECLIENT,
        NotifySource.BROADCAST,
        NotifySource.SETTINGS,
        NotifySource.OBSOLETE_VALUE,
        NotifySource.TIME_VALUE
    )

    fun isRegistered(widget: String): Boolean = activeWidgets.contains(widget)

    fun register(context: Context, widget: String) {
        if (activeWidgets.isEmpty())
            InternalNotifier.addNotifier(context, this, filter)
        activeWidgets.add(widget)
        Log.i(LOG_ID, "widget $widget registered - active: ${activeWidgets.size}")
    }

    fun unregister(context: Context, widget: String) {
        activeWidgets.remove(widget)
        Log.i(LOG_ID, "widget $widget unregistered - active: ${activeWidgets.size}")
        if (activeWidgets.isEmpty()) {
            InternalNotifier.remNotifier(context, this)
            valueCache.clear()
            arrowCache.clear()
            comboCache.clear()
        }
    }

    fun getValueBitmap(width: Int, height: Int): Bitmap =
        valueCache.getOrPut(width to height) { renderValueBitmap(width, height) }

    fun getArrowBitmap(width: Int, height: Int): Bitmap =
        arrowCache.getOrPut(width to height) { renderArrowBitmap(width, height) }

    // Value drawn at (0,0), arrow drawn to its right, vertically centered against the value's height.
    fun getComboBitmap(valueWidth: Int, valueHeight: Int, arrowWidth: Int, arrowHeight: Int): Bitmap =
        comboCache.getOrPut(ComboKey(valueWidth, valueHeight, arrowWidth, arrowHeight)) {
            renderComboBitmap(valueWidth, valueHeight, arrowWidth, arrowHeight)
        }

    private fun renderValueBitmap(width: Int, height: Int): Bitmap {
        Log.d(LOG_ID, "Render value bitmap ${width}x$height")
        return BitmapUtils.getGlucoseAsBitmap(width = width, height = height) ?: createBitmap(width, height)
    }

    private fun renderArrowBitmap(width: Int, height: Int): Bitmap {
        Log.d(LOG_ID, "Render arrow bitmap ${width}x$height")
        return BitmapUtils.getRateAsBitmap(width = width, height = height) ?: createBitmap(width, height)
    }

    private fun renderComboBitmap(valueWidth: Int, valueHeight: Int, arrowWidth: Int, arrowHeight: Int): Bitmap {
        Log.d(LOG_ID, "Render combo bitmap ${valueWidth + arrowWidth}x$valueHeight")
        val value = getValueBitmap(valueWidth, valueHeight)
        val arrow = getArrowBitmap(arrowWidth, arrowHeight)
        val combo = createBitmap(valueWidth + arrowWidth, valueHeight)
        val canvas = Canvas(combo)
        canvas.drawBitmap(value, 0f, 0f, null)
        canvas.drawBitmap(arrow, valueWidth.toFloat(), ((valueHeight - arrowHeight) / 2).toFloat(), null)
        return combo
    }

    // Re-render every size that's currently cached (rather than just dropping it), so the next
    // getValueBitmap/getArrowBitmap/getComboBitmap call - triggered right after by the tile updater -
    // gets a freshly rendered bitmap already sitting in the cache instead of paying the render cost inline.
    private fun recreate() {
        valueCache.keys.toList().forEach { (w, h) -> valueCache[w to h] = renderValueBitmap(w, h) }
        arrowCache.keys.toList().forEach { (w, h) -> arrowCache[w to h] = renderArrowBitmap(w, h) }
        comboCache.keys.toList().forEach { key ->
            comboCache[key] = renderComboBitmap(key.valueWidth, key.valueHeight, key.arrowWidth, key.arrowHeight)
        }
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        Log.d(LOG_ID, "OnNotifyData - source: $dataSource")
        recreate()
    }
}
