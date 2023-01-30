package de.michelinside.glucodatahandler.common

import android.graphics.*
import android.os.Bundle
import android.os.Parcel
import android.util.Log
import java.math.RoundingMode

object Utils {
    private val LOG_ID = "GlucoDataHandler.Utils"
    fun round(value: Float, scale: Int): Float {
        return value.toBigDecimal().setScale( scale, RoundingMode.HALF_UP).toFloat()
    }

    fun rangeValue(value: Float, min: Float, max: Float): Float {
        if (value > max)
            return max
        if (value < min)
            return min
        return value
    }

    fun mgToMmol(value: Float, scale: Int = 1): Float {
        return round(value / Constants.GLUCOSE_CONVERSION_FACTOR, scale)
    }

    fun mmolToMg(value: Float): Float {
        return round(value * Constants.GLUCOSE_CONVERSION_FACTOR, 0)
    }

    fun bytesToBundle(bytes: ByteArray): Bundle? {
        val parcel = Parcel.obtain()
        parcel.unmarshall(bytes, 0, bytes.size)
        parcel.setDataPosition(0)
        val bundle = parcel.readBundle(GlucoDataService::class.java.getClassLoader())
        parcel.recycle()
        return bundle
    }

    fun bundleToBytes(bundle: Bundle?): ByteArray? {
        val parcel = Parcel.obtain()
        parcel.writeBundle(bundle)
        val bytes = parcel.marshall()
        parcel.recycle()
        return bytes
    }

    fun textToBitmap(text: String, color: Int): Bitmap? {
        try {
            val size = 100
            val textSize = 90F
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888 )
            val canvas = Canvas(bitmap)
            bitmap.eraseColor(Color.TRANSPARENT)
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = color
            paint.textSize = textSize
            val boundsText = Rect()
            paint.getTextBounds(text, 0, text.length, boundsText)
            // re-calculate size depending on the bound width -> use minOf for preventing oversize signs
            paint.textSize = minOf( bitmap.height.toFloat(), (textSize - 1) * bitmap.width / boundsText.width() )
            paint.getTextBounds(text, 0, text.length, boundsText)
            if(boundsText.width() > 90)
                paint.textSize = paint.textSize-(boundsText.width() - 90)
            var x = 4
            var y = ((bitmap.height + boundsText.height()) / 2) - 3
            if (paint.textSize == size.toFloat()) {
                x = (bitmap.width - boundsText.width()) / 2
                if (text == "---") {
                    y = 80  // special case
                }
            }

            Log.d(LOG_ID, "Create bitmap for " + text + " - x:" + x.toString() + " y:" + y.toString() + " text-size:" + paint.textSize.toString())
            canvas.drawText(text, x.toFloat(), y.toFloat(), paint)
            return bitmap
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Cannot create text icon: " + exc.message.toString())
            return null
        }
    }
}