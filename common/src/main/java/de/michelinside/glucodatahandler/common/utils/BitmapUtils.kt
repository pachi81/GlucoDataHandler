package de.michelinside.glucodatahandler.common.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.util.Log
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import kotlin.math.abs

object BitmapUtils {
    private val LOG_ID = "GDH.Utils.Bitmap"

    private fun isShortText(text: String): Boolean = text.length <= (if (text.contains(".")) 3 else 2)

    private fun calcMaxTextSizeForBitmap(bitmap: Bitmap, text: String, roundTarget: Boolean, maxTextSize: Float, top: Boolean, bold: Boolean): Float {
        var result: Float = maxTextSize
        if(roundTarget) {
            if (!top || !isShortText(text) ) {
                if (text.contains("."))
                    result *= 0.7F
                else
                    result *= 0.85F
            }
        } else {
            val fullText: String
            if (text.contains("+") || text.contains("-")) {
                fullText = text // delta value
            } else if (text.contains(".")) {
                if (text.length == 3)
                    fullText = "0.0"
                else
                    fullText = "00.0"
            } else {
                if (text.length == 1)
                    fullText = "0"
                else if (text.length == 2)
                    fullText = "00"
                else
                    fullText = "000"
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.textSize = maxTextSize
            paint.textAlign = Paint.Align.CENTER
            if (bold)
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val boundsText = Rect()
            paint.getTextBounds(fullText, 0, fullText.length, boundsText)
            result = minOf( maxTextSize, (maxTextSize - 1) * bitmap.width / boundsText.width() )
        }
        return result
    }

    private fun textToBitmap(text: String, color: Int, roundTarget: Boolean = false, strikeThrough: Boolean = false, width: Int = 100, height: Int = 100, top: Boolean = false, bold: Boolean = false, resizeFactor: Float = 1F): Bitmap? {
        try {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888 )
            val maxTextSize = calcMaxTextSizeForBitmap(bitmap, text, roundTarget, minOf(width,height).toFloat(), top, bold) * resizeFactor
            val canvas = Canvas(bitmap)
            bitmap.eraseColor(Color.TRANSPARENT)
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = color
            paint.textSize = maxTextSize
            paint.textAlign = Paint.Align.CENTER
            paint.isStrikeThruText = strikeThrough
            if (bold)
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val boundsText = Rect()
            paint.getTextBounds(text, 0, text.length, boundsText)
            paint.textSize = minOf( maxTextSize, (maxTextSize - 1) * bitmap.width / boundsText.width() )
            if(paint.textSize < maxTextSize) {
                // re-calculate size depending on the bound width -> use minOf for preventing oversize signs
                paint.getTextBounds(text, 0, text.length, boundsText)
            }
            Log.d(LOG_ID, "height: " + boundsText.height().toString() + " width:" + boundsText.width().toString() + " text-size:" + paint.textSize.toString() + " maxTextSize:" + maxTextSize.toString())
            val maxTextWidthRoundTarget = Utils.round(width.toFloat() * 0.9F, 0).toInt()
            if(roundTarget && boundsText.width() > maxTextWidthRoundTarget)
                paint.textSize = paint.textSize-(boundsText.width() - maxTextWidthRoundTarget)
            val y =
                if (text == "---" || text == "--")
                    if (top)
                        Utils.round(height.toFloat() * 0.5F, 0).toInt()
                    else
                        Utils.round(height.toFloat() * 0.8F, 0).toInt()
                else if (top)
                    boundsText.height()
                else
                    ((bitmap.height + boundsText.height()) / 2) - 3

            Log.d(LOG_ID, "Create bitmap for " + text + " - y:" + y.toString() + " text-size:" + paint.textSize.toString())
            canvas.drawText(text, width.toFloat()/2, y.toFloat(), paint)
            return bitmap
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Cannot create text icon: " + exc.message.toString())
            return null
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, rotationAngleDegree: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        var newW = w
        var newH = h
        if (rotationAngleDegree == 90 || rotationAngleDegree == 270) {
            newW = h
            newH = w
        }
        val rotatedBitmap = Bitmap.createBitmap(newW, newH, bitmap.config)
        val canvas = Canvas(rotatedBitmap)
        val rect = Rect(0, 0, newW, newH)
        val matrix = Matrix()
        val px = rect.exactCenterX()
        val py = rect.exactCenterY()
        matrix.postTranslate((-bitmap.width / 2).toFloat(), (-bitmap.height / 2).toFloat())
        matrix.postRotate(rotationAngleDegree.toFloat())
        matrix.postTranslate(px, py)
        canvas.drawBitmap(
            bitmap,
            matrix,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG)
        )
        matrix.reset()
        return rotatedBitmap
    }

    private fun rateToBitmap(rate: Float, color: Int, width: Int = 100, height: Int = 100, resizeFactor: Float = 1F, strikeThrough: Boolean = false): Bitmap? {
        try {
            if (rate.isNaN()) {
                return textToBitmap("?", color, true, false, width, height )
            }
            var textSize = minOf(width,height).toFloat()
            val text: String
            val degrees: Int
            var shortArrowRate = 0.8F
            if(rate >= 3F) {
                text = "⇈"
                textSize -= textSize*0.05F
                degrees = 0
                shortArrowRate = 0.7F
            } else if ( rate <= -3F ) {
                text = "⇊"
                textSize -= textSize*0.05F
                degrees = 0
                shortArrowRate = 0.7F
            } else {
                text = "↑"
                degrees = Utils.round(abs(maxOf(-2F, minOf(2F, rate)) - 2F) * 90F / 2F, 0).toInt()
            } /*else {  // < 0
                text = "↓"
                degrees = round((maxOf(-2F, rate) + 2F) * -90F/2F, 0).toInt()
            }*/
            textSize *= resizeFactor
            if (GlucoDataService.sharedPref != null && !GlucoDataService.sharedPref!!.getBoolean(
                    Constants.SHARED_PREF_LARGE_ARROW_ICON, true)) {
                textSize *= shortArrowRate
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888 )
            val canvas = Canvas(bitmap)
            bitmap.eraseColor(Color.TRANSPARENT)
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = color
            paint.textSize = textSize
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.isStrikeThruText = strikeThrough
            val boundsText = Rect()
            paint.getTextBounds(text, 0, text.length, boundsText)
            val y = ((bitmap.height + boundsText.height()) / 2) - 3

            Log.d(LOG_ID, "Create bitmap for " + text + "(rate: " + rate + ") - y:" + y.toString() + " - text-size:" + paint.textSize.toString() + " - degrees:" + degrees )
            canvas.drawText(text, width.toFloat()/2, y.toFloat(), paint)
            return rotateBitmap(bitmap, degrees)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Cannot create rate icon: " + exc.message.toString())
            return null
        }
    }

    fun textRateToBitmap(text: String, rate: Float, color: Int, obsolete: Boolean = false, strikeThrough: Boolean, width: Int = 100, height: Int = 100, small: Boolean = false): Bitmap? {
        try {
            val padding = if (isShortText(text)) 0F else height.toFloat()*0.05F
            val rateFactor = if (isShortText(text)) 0.5F else 0.45F
            val resizeFactor = if (small) 0.6F else 1.0F
            val rateSize = Utils.round(height * rateFactor, 0).toInt()
            val textHeight = height - rateSize - Utils.round(padding, 0).toInt()
            val textBitmap = textToBitmap(text, color, true, strikeThrough, width, textHeight, true, false, resizeFactor)
            val rateBitmap = rateToBitmap(rate, color, rateSize, rateSize, resizeFactor, obsolete)
            val comboBitmap = Bitmap.createBitmap(width,height, Bitmap.Config.ARGB_8888)
            val comboImage = Canvas(comboBitmap)
            val rateTopFactor = if (small) 5F else 1F
            comboImage.drawBitmap(rateBitmap!!, ((height-rateSize)/2).toFloat(), padding*rateTopFactor, null)
            comboImage.drawBitmap(textBitmap!!, 0F, rateBitmap.height.toFloat()+padding, null)
            return comboBitmap
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Cannot create rate icon: " + exc.message.toString())
            return null
        }
    }

    private fun getGlucoseAsBitmap(color: Int? = null, roundTarget: Boolean = false, width: Int = 100, height: Int = 100, resizeFactor: Float = 1F): Bitmap? {
        return textToBitmap(
            ReceiveData.getClucoseAsString(),color ?: ReceiveData.getClucoseColor(), roundTarget, ReceiveData.isObsolete(
                Constants.VALUE_OBSOLETE_SHORT_SEC
            ) && !ReceiveData.isObsolete(),width, height, resizeFactor = resizeFactor)
    }

    fun getGlucoseAsIcon(color: Int? = null, roundTarget: Boolean = false, width: Int = 100, height: Int = 100, resizeFactor: Float = 1F): Icon {
        return Icon.createWithBitmap(getGlucoseAsBitmap(color, roundTarget, width, height, resizeFactor))
    }

    fun getDeltaAsBitmap(color: Int? = null, roundTarget: Boolean = false, width: Int = 100, height: Int = 100): Bitmap? {
        return textToBitmap(ReceiveData.getDeltaAsString(),color ?: ReceiveData.getClucoseColor(true), roundTarget, false, width, height)
    }

    fun getDeltaAsIcon(color: Int? = null, roundTarget: Boolean = false, width: Int = 100, height: Int = 100): Icon {
        return Icon.createWithBitmap(getDeltaAsBitmap(color, roundTarget, width, height))
    }

    fun getRateAsBitmap(color: Int? = null, roundTarget: Boolean = false, resizeFactor: Float = 1F, width: Int = 100, height: Int = 100): Bitmap? {
        return rateToBitmap(
            ReceiveData.rate, color ?: ReceiveData.getClucoseColor(), resizeFactor = resizeFactor, width = width, height = height, strikeThrough = ReceiveData.isObsolete(
                Constants.VALUE_OBSOLETE_SHORT_SEC
            )
        )
    }

    fun getRateAsIcon(color: Int? = null, roundTarget: Boolean = false, resizeFactor: Float = 1F, width: Int = 100, height: Int = 100): Icon {
        return Icon.createWithBitmap(getRateAsBitmap(color, roundTarget, resizeFactor, width, height))
    }

    fun getGlucoseTrendBitmap(color: Int? = null, width: Int = 100, height: Int = 100, small: Boolean = false): Bitmap? {
        return textRateToBitmap(
            ReceiveData.getClucoseAsString(), ReceiveData.rate, color ?: ReceiveData.getClucoseColor(), ReceiveData.isObsolete(
                Constants.VALUE_OBSOLETE_SHORT_SEC
            ), ReceiveData.isObsolete(Constants.VALUE_OBSOLETE_SHORT_SEC) && !ReceiveData.isObsolete(),width, height, small)
    }

    fun getGlucoseTrendIcon(color: Int? = null, width: Int = 100, height: Int = 100, small: Boolean = false): Icon {
        return Icon.createWithBitmap(getGlucoseTrendBitmap(color, width, height, small))
    }
}