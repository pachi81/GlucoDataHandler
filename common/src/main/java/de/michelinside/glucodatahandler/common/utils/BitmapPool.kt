package de.michelinside.glucodatahandler.common.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

object BitmapPool {

    private val LOG_ID = "GDH.Utils.BitmapPool"
    private val maxSizePerDimension: Int = 5
    private val pool = ConcurrentHashMap<String, MutableList<Bitmap>>()

    /**
     * Gets a Bitmap from the pool associated with the given dimensions.
     * If no suitable Bitmap is found, a new one is created.
     *
     * @param width The desired width of the Bitmap.
     * @param height The desired height of the Bitmap.
     * @param config The desired Bitmap.Config.
     * @return A Bitmap from the pool or a newly created one.
     */
    fun getBitmap(
        width: Int,
        height: Int,
        config: Bitmap.Config = Bitmap.Config.ARGB_8888
    ): Bitmap {
        val dimensionKey = "$width-$height"
        synchronized(pool) {
            val bitmapList = pool[dimensionKey]
            if (bitmapList != null) {
                val bitmap = bitmapList.find { it.config == config }
                if (bitmap != null) {
                    bitmapList.remove(bitmap)
                    Log.d(LOG_ID, "Bitmap reused for dimensions: $dimensionKey (new size: ${bitmapList.size})")
                    return bitmap
                }
            }
        }
        Log.i(LOG_ID, "New bitmap created for dimensions: $dimensionKey")
        return Bitmap.createBitmap(width, height, config)
    }

    /**
     * Returns a Bitmap to the pool associated with the given dimensions.
     *
     * @param bitmap The Bitmap to return to the pool.
     */
    fun returnBitmap(bitmap: Bitmap?) {
        if(bitmap == null) return
        val dimensionKey = "${bitmap.width}-${bitmap.height}"
        synchronized(pool) {
            if (!bitmap.isRecycled) {
                val bitmapList = pool.getOrPut(dimensionKey) { mutableListOf() }
                if (bitmapList.size < maxSizePerDimension) {
                    // Clear the bitmap before returning it to the pool
                    bitmapList.add(BitmapUtils.clearBitmap(bitmap))
                    Log.d(
                        LOG_ID,
                        "Bitmap returned to pool for dimensions: $dimensionKey (new size: ${bitmapList.size})"
                    )
                } else {
                    bitmap.recycle()
                    Log.i(
                        LOG_ID,
                        "Bitmap recycled for dimensions: $dimensionKey (pool full)"
                    )
                }
            } else {
                Log.w(
                    LOG_ID,
                    "Bitmap already recycled for dimensions: $dimensionKey"
                )
            }
        }
    }

    /**
     * Clears all Bitmaps from the pool.
     */
    fun clear() {
        synchronized(pool) {
            pool.forEach { (_, bitmapList) ->
                bitmapList.forEach { it.recycle() }
                bitmapList.clear()
            }
            pool.clear()
            Log.d(LOG_ID, "Bitmap pool cleared")
        }
    }
}