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

    fun textToBitmap(text: String, color: Int, roundTargert: Boolean = false): Bitmap? {
        try {
            val size = 100
            val textSize = if(roundTargert) { if (text.contains(".")) 70F else 85F } else 100F
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888 )
            val canvas = Canvas(bitmap)
            bitmap.eraseColor(Color.TRANSPARENT)
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = color
            paint.textSize = textSize
            paint.textAlign = Paint.Align.CENTER
            val boundsText = Rect()
            paint.getTextBounds(text, 0, text.length, boundsText)
            paint.textSize = minOf( textSize, (textSize - 1) * bitmap.width / boundsText.width() )
            if(paint.textSize < textSize) {
                // re-calculate size depending on the bound width -> use minOf for preventing oversize signs
                paint.getTextBounds(text, 0, text.length, boundsText)
            }
            Log.d(LOG_ID, "height: " + boundsText.height().toString() + " width:" + boundsText.width().toString() + " text-size:" + paint.textSize.toString())
            if(roundTargert && boundsText.width() > 90)
                paint.textSize = paint.textSize-(boundsText.width() - 90)
            val y = if (text == "---") 80 else ((bitmap.height + boundsText.height()) / 2) - 3

            Log.d(LOG_ID, "Create bitmap for " + text + " - y:" + y.toString() + " text-size:" + paint.textSize.toString())
            canvas.drawText(text, size.toFloat()/2, y.toFloat(), paint)
            return bitmap
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Cannot create text icon: " + exc.message.toString())
            return null
        }
    }
}