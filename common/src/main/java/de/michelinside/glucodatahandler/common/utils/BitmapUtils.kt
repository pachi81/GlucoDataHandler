package de.michelinside.glucodatahandler.common.utils

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.R
import de.michelinside.glucodatahandler.common.ReceiveData
import kotlin.math.abs


object BitmapUtils {
    private val LOG_ID = "GDH.Utils.Bitmap"
    private var displayManager: DisplayManager? = null
    private var displayMetrics: DisplayMetrics? = null

    fun getDisplayManager(context: Context): DisplayManager? {
        if(displayManager == null)
            displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager?
        return displayManager
    }

    fun getScreenWidth(context: Context, checkOrientation: Boolean = false): Int {
        try {
            if(checkOrientation && isLandscapeOrientation(context))
                return getScreenHeight(context, false)
            if(displayMetrics!=null)
                return displayMetrics!!.widthPixels
            val displayManager = getDisplayManager(context)
            if(displayManager != null && displayManager.displays.isNotEmpty()) {
                displayMetrics = DisplayMetrics()
                displayManager.displays[0].getRealMetrics(displayMetrics)
                return displayMetrics!!.widthPixels
            }
        } catch (e: Exception) {
            Log.e(LOG_ID, "Error in getScreenWidth", e)
        }
        return Resources.getSystem().displayMetrics.widthPixels
    }

    fun getScreenHeight(context: Context, checkOrientation: Boolean = false): Int {
        try {
            if(checkOrientation && isLandscapeOrientation(context))
                return getScreenWidth(context, false)
            if(displayMetrics!=null)
                return displayMetrics!!.heightPixels
            val displayManager = getDisplayManager(context)
            if(displayManager != null && displayManager.displays.isNotEmpty()) {
                displayMetrics = DisplayMetrics()
                displayManager.displays[0].getRealMetrics(displayMetrics)
                return displayMetrics!!.heightPixels
            }
        } catch (e: Exception) {
            Log.e(LOG_ID, "Error in getScreenHeight", e)
        }
        return Resources.getSystem().displayMetrics.heightPixels
    }

    fun isLandscapeOrientation(context: Context): Boolean {
        return (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
    }

    fun getScreenDpi(): Int {
        return Resources.getSystem().displayMetrics.densityDpi
    }


    private fun isShortText(text: String): Boolean = text.length <= (if (text.contains(".")) 3 else 2)

    fun calcMaxTextSizeForBitmap(bitmap: Bitmap, text: String, roundTarget: Boolean, maxTextSize: Float, top: Boolean, bold: Boolean, useTallFont: Boolean = false): Float {
        var result: Float = maxTextSize
        if(roundTarget) {
            if (!top || !isShortText(text) ) {
                result *= if (text.contains("."))
                    0.7F
                else
                    0.85F
            }
        } else {
            val fullText = if (text.contains("+") || text.contains("-")) {
                text // delta value
            } else if (text.contains(".")) {
                if (text.length == 3)
                    "0.0"
                else
                    "00.0"
            } else {
                when(text.length) {
                    1 -> "0"
                    2 -> "00"
                    3 -> "000"
                    else -> text
                }
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.textSize = maxTextSize
            paint.textAlign = Paint.Align.CENTER
            if (useTallFont)
                paint.typeface = Typeface.create(GlucoDataService.context!!.resources.getFont(R.font.opensans), Typeface.BOLD)
            else if (bold)
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val boundsText = Rect()
            paint.getTextBounds(fullText, 0, fullText.length, boundsText)
            result = minOf( maxTextSize, (maxTextSize - 1) * bitmap.width / boundsText.width() )
            if(useTallFont && result < maxTextSize) {
                result *= 0.95F  // for values like 118 the 8 was cut...
            }
            Log.d(LOG_ID, "calculated max text size for $text ($fullText): $result")
        }
        return result
    }

    fun textToBitmap(text: String, color: Int, roundTarget: Boolean = false, strikeThrough: Boolean = false, width: Int = 100, height: Int = 100, top: Boolean = false, bold: Boolean = false, resizeFactor: Float = 1F, withShadow: Boolean = false, bottom: Boolean = false, useTallFont: Boolean = false): Bitmap? {
        try {
            val bitmap = BitmapPool.getBitmap(width, height, Bitmap.Config.ARGB_8888 )
            val maxTextSize = calcMaxTextSizeForBitmap(bitmap, text, roundTarget, minOf(width,height).toFloat(), top, bold, useTallFont) * minOf(1F, resizeFactor)
            val canvas = Canvas(bitmap)
            bitmap.eraseColor(Color.TRANSPARENT)
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = color
            paint.textSize = maxTextSize
            paint.textAlign = Paint.Align.CENTER
            paint.isStrikeThruText = strikeThrough
            if (useTallFont)
                paint.typeface = Typeface.create(GlucoDataService.context!!.resources.getFont(R.font.opensans), Typeface.BOLD)
            else if (bold)
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            if(withShadow)
                paint.setShadowLayer(2F, 0F,0F, Color.BLACK)
            val boundsText = Rect()
            paint.getTextBounds(text, 0, text.length, boundsText)
            paint.textSize = minOf( maxTextSize, (maxTextSize - 1) * bitmap.width / boundsText.width() )
            if(paint.textSize < maxTextSize) {
                // re-calculate size depending on the bound width -> use minOf for preventing oversize signs
                paint.getTextBounds(text, 0, text.length, boundsText)
            }
            Log.v(LOG_ID, "height: " + boundsText.height().toString() + " width:" + boundsText.width().toString() + " text-size:" + paint.textSize.toString() + " maxTextSize:" + maxTextSize.toString())
            val maxTextWidthRoundTarget = Utils.round(width.toFloat() * 0.9F, 0).toInt()
            if(roundTarget && boundsText.width() > maxTextWidthRoundTarget)
                paint.textSize = paint.textSize-(boundsText.width() - maxTextWidthRoundTarget)
            val y =
                if(top)
                    if (text == "---" || text == "--")
                        Utils.round(height.toFloat() * 0.5F, 0).toInt()
                    else
                        boundsText.height()
                else if(bottom)
                    canvas.height - (canvas.height*0.1F).toInt()
                else
                    canvas.height/2f + boundsText.height()/2f - boundsText.bottom

            Log.d(LOG_ID, "Create bitmap ($width x $height) for $text - y: $y - text-size: ${paint.textSize} (max: $maxTextSize) - color: color - shadow: $withShadow")
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
        val rotatedBitmap = BitmapPool.getBitmap(newW, newH, bitmap.config)
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
        BitmapPool.returnBitmap(bitmap)
        return rotatedBitmap
    }

    private fun rateToBitmap(rate: Float, color: Int, width: Int = 100, height: Int = 100, resizeFactor: Float = 1F, strikeThrough: Boolean = false, withShadow: Boolean = false): Bitmap? {
        try {
            if (rate.isNaN()) {
                return textToBitmap("?", color, true, false, width, height, withShadow = withShadow )
            }
            var textSize = minOf(width,height).toFloat()
            val text: String
            val degrees: Int
            var shortArrowRate = 0.8F
            if(rate >= 3F || rate <= -3F) {
                text = "⇈"
                textSize -= textSize*0.05F
                degrees = if ( rate <= -3F ) 180 else 0
                shortArrowRate = 0.7F
            /*} else if ( rate <= -3F ) {
                text = "⇊"
                textSize -= textSize*0.05F
                degrees = 0
                shortArrowRate = 0.7F*/
            } else {
                text = "↑"
                degrees = abs(GlucoDataUtils.getRateDegrees(rate)-90)
            } /*else {  // < 0
                text = "↓"
                degrees = round((maxOf(-2F, rate) + 2F) * -90F/2F, 0).toInt()
            }*/
            textSize *= resizeFactor
            if (GlucoDataService.sharedPref != null && !GlucoDataService.sharedPref!!.getBoolean(
                    Constants.SHARED_PREF_LARGE_ARROW_ICON, true)) {
                textSize *= shortArrowRate
            }
            //val key = "$width:$height:$degrees:$textSize:$color:$strikeThrough:$withShadow"

            val bitmap = BitmapPool.getBitmap(width, height)
            val canvas = Canvas(bitmap)
            bitmap.eraseColor(Color.TRANSPARENT)
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = color
            paint.textSize = textSize
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.isStrikeThruText = strikeThrough
            if(withShadow)
                paint.setShadowLayer(2F, 0F,0F, Color.BLACK)
            Log.d(LOG_ID, "Create $width x $height bitmap for $text (rate: $rate) - text-size: ${paint.textSize} - degrees: $degrees - color: $color - shadow: $withShadow" )
            drawCenteredText(canvas, paint, text)
            return rotateBitmap(bitmap, degrees)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Cannot create rate icon: " + exc.message.toString())
            return null
        }
    }

    private fun drawCenteredText(canvas: Canvas, paint: Paint, text: String) {
        paint.textAlign = Paint.Align.LEFT
        val boundsText = Rect()
        paint.getTextBounds(text, 0, text.length, boundsText)
        val x = canvas.width/2f - boundsText.width()/2f -boundsText.left //width.toFloat()/2
        val y = canvas.height/2f + boundsText.height()/2f - boundsText.bottom //((bitmap.height + boundsText.height()) / 2) - 10
        Log.d(LOG_ID, "Center ${canvas.width}x${canvas.height} bitmap for $text - x: $x - y: $y - text-size: ${paint.textSize}")
        canvas.drawText(text, x, y, paint)
    }

    fun textRateToBitmap(text: String, rate: Float, color: Int, obsolete: Boolean = false, strikeThrough: Boolean, width: Int = 100, height: Int = 100, small: Boolean = false, withShadow: Boolean = false): Bitmap? {
        try {
            val padding = if (isShortText(text) || small) 0F else height.toFloat()*0.05F
            val rateFactor = if (isShortText(text)) 0.5F else 0.45F
            val resizeFactor = if (small) 0.6F else 1.0F
            val rateSize = Utils.round(height * rateFactor, 0).toInt()
            var textHeight = Utils.round(height - rateSize - Utils.round(padding, 0).toInt().toFloat()*resizeFactor, 0).toInt()
            val topRateSmall = padding + (height.toFloat()*(1F-resizeFactor)/2)
            if (small) {
                textHeight -= topRateSmall.toInt()
            }
            val resizeWidth = Utils.round(width.toFloat()*resizeFactor, 0).toInt()
            val textBitmap = textToBitmap(text, color, true, strikeThrough, resizeWidth, textHeight, true, false, 1F, withShadow)
            val rateDimension = Utils.round(rateSize.toFloat() * resizeFactor, 0).toInt()
            val rateBitmap = rateToBitmap(rate, color, rateDimension, rateDimension, 1F, obsolete, withShadow)
            val comboBitmap = BitmapPool.getBitmap(width,height, Bitmap.Config.ARGB_8888)
            val comboImage = Canvas(comboBitmap)
            if (small) {
                comboImage.drawBitmap(rateBitmap!!, ((height-rateDimension)/2).toFloat(), topRateSmall, null)
                Log.w(LOG_ID,topRateSmall.toString() + " - " + ((height/2)).toString() )
                comboImage.drawBitmap(textBitmap!!, ((width-resizeWidth)/2).toFloat(), (height.toFloat()/2), null)
            } else {
                comboImage.drawBitmap(rateBitmap!!, ((height-rateDimension)/2).toFloat(), padding, null)
                comboImage.drawBitmap(textBitmap!!, 0F, rateBitmap.height.toFloat()+padding, null)
            }
            BitmapPool.returnBitmap(rateBitmap)
            BitmapPool.returnBitmap(textBitmap)
            return comboBitmap
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Cannot create rate icon: " + exc.message.toString())
            return null
        }
    }

    fun createComboBitmap(bitmapAbove: Bitmap, bitmapBelow: Bitmap, width: Int = 100, height: Int = 100) : Bitmap? {
        try {
            val comboBitmap = BitmapPool.getBitmap(width,height, Bitmap.Config.ARGB_8888)
            val comboImage = Canvas(comboBitmap)
            comboImage.drawBitmap(bitmapAbove, ((width-bitmapAbove.width)/2).toFloat(), 0F, null)
            comboImage.drawBitmap(bitmapBelow, ((width-bitmapBelow.width)/2).toFloat(), height - bitmapBelow.height.toFloat(), null)
            return comboBitmap
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Cannot create combo bitmap: " + exc.message.toString())
            return null
        }
    }

    fun getGlucoseAsBitmap(color: Int? = null, roundTarget: Boolean = false, width: Int = 100, height: Int = 100, resizeFactor: Float = 1F, withShadow: Boolean = false, useTallFont: Boolean = false): Bitmap? {
        return textToBitmap(
            ReceiveData.getGlucoseAsString(),color ?: ReceiveData.getGlucoseColor(), roundTarget, ReceiveData.isObsoleteShort() &&
                    !ReceiveData.isObsoleteLong(),width, height, resizeFactor = resizeFactor, withShadow = withShadow, useTallFont = useTallFont)
    }

    fun getGlucoseAsIcon(key: String, color: Int? = null, roundTarget: Boolean = false, width: Int = 100, height: Int = 100, resizeFactor: Float = 1F, withShadow: Boolean = false, useTallFont: Boolean = false): Icon {
        return IconBitmapPool.createIcon(key, getGlucoseAsBitmap(color, roundTarget, width, height, resizeFactor, withShadow, useTallFont))
    }

    fun getDeltaAsBitmap(color: Int? = null, roundTarget: Boolean = false, width: Int = 100, height: Int = 100, top: Boolean = false, resizeFactor: Float = 1F, useTallFont: Boolean = false): Bitmap? {
        return textToBitmap(ReceiveData.getDeltaAsString(),color ?: ReceiveData.getGlucoseColor(true), roundTarget, false, width, height, top = top, resizeFactor = resizeFactor, useTallFont = useTallFont)
    }

    fun getDeltaAsIcon(key: String, color: Int? = null, roundTarget: Boolean = false, width: Int = 100, height: Int = 100, resizeFactor: Float = 1F, useTallFont: Boolean = false): Icon {
        return IconBitmapPool.createIcon(key, getDeltaAsBitmap(color, roundTarget, width, height, resizeFactor = resizeFactor, useTallFont = useTallFont))
    }

    fun getRateAsBitmap(
        color: Int? = null,
        resizeFactor: Float = 1F,
        width: Int = 100,
        height: Int = 100,
        withShadow: Boolean = false
    ): Bitmap? {
        return rateToBitmap(
            ReceiveData.rate, color ?: ReceiveData.getGlucoseColor(), resizeFactor = resizeFactor, width = width, height = height, strikeThrough = ReceiveData.isObsoleteShort(),
            withShadow = withShadow
        )
    }

    fun getRateAsIcon(
        key: String,
        color: Int? = null,
        resizeFactor: Float = 1F,
        width: Int = 100,
        height: Int = 100,
        withShadow: Boolean = false
    ): Icon {
        return IconBitmapPool.createIcon(key, getRateAsBitmap(color, resizeFactor, width, height, withShadow))
    }

    fun getGlucoseTrendBitmap(color: Int? = null, width: Int = 100, height: Int = 100, small: Boolean = false, withShadow: Boolean = false): Bitmap? {
        return textRateToBitmap(
            ReceiveData.getGlucoseAsString(), ReceiveData.rate, color ?: ReceiveData.getGlucoseColor(), ReceiveData.isObsoleteShort(), ReceiveData.isObsoleteShort() && !ReceiveData.isObsoleteLong(),width, height, small, withShadow)
    }

    fun getGlucoseTrendIcon(key: String, color: Int? = null, width: Int = 100, height: Int = 100, small: Boolean = false, withShadow: Boolean = false): Icon {
        return IconBitmapPool.createIcon(key, getGlucoseTrendBitmap(color, width, height, small, withShadow))
    }

    fun loadBitmapFromView(view: View, targetBitmap: Bitmap? = null): Bitmap {
        val bitmap = targetBitmap?: BitmapPool.getBitmap(
                view.width,
                view.height,
                Bitmap.Config.ARGB_8888
            )
        val canvas = Canvas(bitmap)
        if(targetBitmap != null)  // clear target bitmap
            canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        view.layout(view.left, view.top, view.right, view.bottom)
        view.draw(canvas)
        return bitmap
    }

    fun clearBitmap(bitmap: Bitmap): Bitmap {
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        return bitmap
    }
}