package de.michelinside.glucodatahandler.common

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.Parcel
import android.util.Log
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.random.Random

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

    fun isMmolValue(value: Float): Boolean = value < Constants.GLUCOSE_MIN_VALUE.toFloat()

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
        if (bundle==null)
            return null
        val parcel = Parcel.obtain()
        parcel.writeBundle(bundle)
        val bytes = parcel.marshall()
        parcel.recycle()
        return bytes
    }

    fun dumpBundle(bundle: Bundle?): String {
        if (bundle == null) {
            return "NULL"
        }
        var string = "Bundle{"
        for (key in bundle.keySet()) {
            string += " " + key + " => " + (if (bundle[key] != null) bundle[key].toString() else "NULL") + ";"
        }
        string += " }Bundle"
        return string
    }

    fun getAppIntent(context: Context, activityClass: Class<*>, requestCode: Int): PendingIntent {
        return PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, activityClass),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

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
            if (text.contains(".")) {
                if (text.length == 3)
                    fullText = "0.0"
                else
                    fullText = "00.0"
            } else {
                if (text.length == 2)
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

    fun textToBitmap(text: String, color: Int, roundTarget: Boolean = false, strikeThrough: Boolean = false, width: Int = 100, height: Int = 100, top: Boolean = false, bold: Boolean = false): Bitmap? {
        try {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888 )
            val maxTextSize = calcMaxTextSizeForBitmap(bitmap, text, roundTarget, minOf(width,height).toFloat(), top, bold)
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
            val maxTextWidthRoundTarget = round(width.toFloat()*0.9F, 0).toInt()
            if(roundTarget && boundsText.width() > maxTextWidthRoundTarget)
                paint.textSize = paint.textSize-(boundsText.width() - maxTextWidthRoundTarget)
            val y =
                if (text == "---")
                    if (top)
                        round(height.toFloat() * 0.5F,0).toInt()
                    else
                        round(height.toFloat() * 0.8F,0).toInt()
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

    fun rotateBitmap(bitmap: Bitmap, rotationAngleDegree: Int): Bitmap? {
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

    fun rateToBitmap(rate: Float, color: Int, width: Int = 100, height: Int = 100, resizeFactor: Float = 1F): Bitmap? {
        try {
            var textSize = minOf(width,height).toFloat()
            val text: String
            val degrees: Int
            if(rate >= 3F) {
                text = "⇈"
                textSize -= textSize*0.05F
                degrees = 0
            } else if ( rate <= -3F ) {
                text = "⇊"
                textSize -= textSize*0.05F
                degrees = 0
            } else if (rate >= 0F) {
                text = "↑"
                degrees = round(abs(minOf(2F, rate)-2F) * 90F/2F, 0).toInt()
            } else {  // < 0
                text = "↓"
                degrees = round((maxOf(-2F, rate) + 2F) * -90F/2F, 0).toInt()
            }
            textSize *= resizeFactor
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888 )
            val canvas = Canvas(bitmap)
            bitmap.eraseColor(Color.TRANSPARENT)
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = color
            paint.textSize = textSize
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
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

    fun textRateToBitmap(text: String, rate: Float, color: Int, obsolete: Boolean = false, strikeThrough: Boolean, width: Int = 100, height: Int = 100): Bitmap? {
        try {
            val padding = if (isShortText(text)) 0F else height.toFloat()*0.05F
            val rateFactor = if (isShortText(text)) 0.5F else 0.45F
            val rateSize = round(height * rateFactor, 0).toInt()
            val textHeight = height - rateSize - round(padding,0).toInt()
            val textBitmap = textToBitmap(text, color, true, strikeThrough, width, textHeight, true, false)
            val rateBitmap = if (obsolete) textToBitmap("?", color, true, false, rateSize, rateSize ) else rateToBitmap(rate, color, rateSize, rateSize)
            val comboBitmap = Bitmap.createBitmap(width,height, Bitmap.Config.ARGB_8888)
            val comboImage = Canvas(comboBitmap)
            comboImage.drawBitmap(rateBitmap!!, ((height-rateSize)/2).toFloat(), padding, null)
            comboImage.drawBitmap(textBitmap!!, 0F, rateBitmap.height.toFloat()+padding, null)
            return comboBitmap
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Cannot create rate icon: " + exc.message.toString())
            return null
        }
    }

    private var rateDelta = 0.5F
    private var rawDelta = 5
    fun getDummyGlucodataIntent(random: Boolean = true) : Intent {
        val useMmol = ReceiveData.isMmol
        val time =  if (!random) System.currentTimeMillis() else if (ReceiveData.time < System.currentTimeMillis()) System.currentTimeMillis() + 1000 else ReceiveData.time + 1000
        val intent = Intent(Constants.GLUCODATA_BROADCAST_ACTION)
        var raw: Int
        var glucose: Float
        val rate: Float
        if (random) {
            raw = Random.nextInt(40, 400)
            glucose = if(useMmol) mgToMmol(raw.toFloat()) else raw.toFloat()
            rate = round(Random.nextFloat() + Random.nextInt(-4, 4).toFloat(), 2)
        } else {
            if ((ReceiveData.rawValue == 200 && rawDelta > 0) || (ReceiveData.rawValue == 40 && rawDelta < 0)) {
                rawDelta *= -1
            }
            raw =
                if (ReceiveData.time == 0L || ReceiveData.rawValue == 400) Constants.GLUCOSE_MIN_VALUE else ReceiveData.rawValue + rawDelta
            glucose = if (useMmol) mgToMmol(raw.toFloat()) else raw.toFloat()
            if (useMmol && glucose == ReceiveData.glucose) {
                raw += 1
                glucose = mgToMmol(raw.toFloat())
            }
            if ((ReceiveData.rate >= 3.5F && rateDelta > 0F) || (ReceiveData.rate <= -3.5F && rateDelta < 0F)) {
                rateDelta *= -1F
            }
            rate = if (ReceiveData.time == 0L) -3.5F else ReceiveData.rate + rateDelta
        }
        intent.putExtra(ReceiveData.SERIAL, "WUSEL_DUSEL")
        intent.putExtra(ReceiveData.MGDL, raw)
        intent.putExtra(ReceiveData.GLUCOSECUSTOM, glucose)
        intent.putExtra(ReceiveData.RATE, rate)
        intent.putExtra(ReceiveData.TIME, time)
        intent.putExtra(ReceiveData.ALARM, if (raw <= 70) 7 else if (raw >= 250) 6 else 0)
        return intent
    }

    fun getGlucoseAsBitmap(color: Int? = null, roundTarget: Boolean = false, width: Int = 100, height: Int = 100): Bitmap? {
        return textToBitmap(ReceiveData.getClucoseAsString(),color ?: ReceiveData.getClucoseColor(), roundTarget, ReceiveData.isObsolete(Constants.VALUE_OBSOLETE_SHORT_SEC) && !ReceiveData.isObsolete(),width, height)
    }

    fun getGlucoseAsIcon(color: Int? = null, roundTarget: Boolean = false, width: Int = 100, height: Int = 100): Icon {
        return Icon.createWithBitmap(getGlucoseAsBitmap(color, roundTarget, width, height))
    }

    fun getDeltaAsBitmap(color: Int? = null, roundTarget: Boolean = false, width: Int = 100, height: Int = 100): Bitmap? {
        return textToBitmap(ReceiveData.getDeltaAsString(),color ?: ReceiveData.getClucoseColor(), roundTarget, false, width, height)
    }

    fun getDeltaAsIcon(color: Int? = null, roundTarget: Boolean = false, width: Int = 100, height: Int = 100): Icon {
        return Icon.createWithBitmap(getDeltaAsBitmap(color, roundTarget, width, height))
    }

    fun getRateAsBitmap(color: Int? = null, roundTarget: Boolean = false, resizeFactor: Float = 1F, width: Int = 100, height: Int = 100): Bitmap? {
        if (ReceiveData.isObsolete(Constants.VALUE_OBSOLETE_SHORT_SEC))
            return textToBitmap("?", Color.GRAY, roundTarget, width = width, height = height)
        return rateToBitmap(ReceiveData.rate, color ?: ReceiveData.getClucoseColor(), resizeFactor = resizeFactor, width = width, height = height)
    }

    fun getRateAsIcon(color: Int? = null, roundTarget: Boolean = false, resizeFactor: Float = 1F, width: Int = 100, height: Int = 100): Icon {
        return Icon.createWithBitmap(getRateAsBitmap(color, roundTarget, resizeFactor, width, height))
    }

    fun getGlucoseTrendBitmap(color: Int? = null, width: Int = 100, height: Int = 100): Bitmap? {
        return textRateToBitmap(ReceiveData.getClucoseAsString(), ReceiveData.rate, color ?: ReceiveData.getClucoseColor(), ReceiveData.isObsolete(Constants.VALUE_OBSOLETE_SHORT_SEC), ReceiveData.isObsolete(Constants.VALUE_OBSOLETE_SHORT_SEC) && !ReceiveData.isObsolete(),width, height)
    }

    fun getGlucoseTrendIcon(color: Int? = null, width: Int = 100, height: Int = 100): Icon {
        return Icon.createWithBitmap(getGlucoseTrendBitmap(color, width, height))
    }

}