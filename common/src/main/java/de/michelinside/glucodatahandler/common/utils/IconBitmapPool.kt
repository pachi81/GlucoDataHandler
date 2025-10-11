package de.michelinside.glucodatahandler.common.utils

import android.graphics.Bitmap
import android.graphics.drawable.Icon
import de.michelinside.glucodatahandler.common.utils.Log
import java.util.concurrent.ConcurrentHashMap

object IconBitmapPool {
    private val LOG_ID = "GDH.Utils.IconPool"
    private val iconMap = ConcurrentHashMap<String, Bitmap>()

    fun createIcon(key: String, bitmap: Bitmap?): Icon {
        val icon = Icon.createWithBitmap(bitmap)

        replaceBitmap(key, bitmap)

        return icon
    }

    private fun replaceBitmap(key: String, bitmap: Bitmap?) {
        releaseIcon(key)
        if(bitmap != null) {
            Log.v(LOG_ID, "set bitmap: $key")
            iconMap[key] = bitmap
        }
    }

    fun releaseIcon(key: String) {
        Log.v(LOG_ID, "releaseIcon: $key")
        val bitmap = iconMap.remove(key)
        BitmapPool.returnBitmap(bitmap)
    }

    fun releaseAll() {
        iconMap.forEach { (_, bitmap) ->
            BitmapPool.returnBitmap(bitmap)
        }
        iconMap.clear()
    }
}