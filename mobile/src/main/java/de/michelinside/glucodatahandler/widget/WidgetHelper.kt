package de.michelinside.glucodatahandler.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.ImageView
import android.widget.TextView
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.utils.BitmapUtils
import de.michelinside.glucodatahandler.common.utils.Utils

object WidgetHelper {
    private val LOG_ID = "GDH.widget.WidgetHelper"
    private val MAX_SIZE = 24f

    fun createWallpaperView(context: Context, size: Int, style: String, chart: Bitmap? = null): Bitmap? {
        try {
            Log.d(LOG_ID, "Create wallpaper view for size $size and style $style with chart $chart")
            //getting the widget layout from xml using layout inflater
            val layout = if(style == Constants.WIDGET_STYLE_CHART_GLUCOSE_TREND_TIME_DELTA_IOB_COB)
                R.layout.floating_widget_chart
            else
                R.layout.floating_widget
            val lockscreenView = LayoutInflater.from(context).inflate(layout, null)
            val txtBgValue: TextView = lockscreenView.findViewById(R.id.glucose)
            val viewIcon: ImageView = lockscreenView.findViewById(R.id.trendImage)
            val txtDelta: TextView = lockscreenView.findViewById(R.id.deltaText)
            val txtTime: TextView = lockscreenView.findViewById(R.id.timeText)
            val txtIob: TextView = lockscreenView.findViewById(R.id.iobText)
            val txtCob: TextView = lockscreenView.findViewById(R.id.cobText)
            val graphImage: ImageView? = lockscreenView.findViewById(R.id.graphImage)

            var textSize = 30f
            when(style) {
                Constants.WIDGET_STYLE_GLUCOSE_TREND_DELTA -> {
                    txtTime.visibility = GONE
                    txtDelta.visibility = VISIBLE
                    viewIcon.visibility = VISIBLE
                    txtIob.visibility = GONE
                    txtCob.visibility = GONE
                }
                Constants.WIDGET_STYLE_GLUCOSE_TREND -> {
                    txtTime.visibility = GONE
                    txtDelta.visibility = GONE
                    viewIcon.visibility = VISIBLE
                    txtIob.visibility = GONE
                    txtCob.visibility = GONE
                }
                Constants.WIDGET_STYLE_GLUCOSE -> {
                    txtTime.visibility = GONE
                    txtDelta.visibility = GONE
                    viewIcon.visibility = GONE
                    txtIob.visibility = GONE
                    txtCob.visibility = GONE
                }
                Constants.WIDGET_STYLE_CHART_GLUCOSE_TREND_TIME_DELTA_IOB_COB,
                Constants.WIDGET_STYLE_GLUCOSE_TREND_TIME_DELTA_IOB_COB -> {
                    textSize = 20f
                    txtTime.visibility = VISIBLE
                    txtDelta.visibility = VISIBLE
                    viewIcon.visibility = VISIBLE
                    txtIob.visibility = VISIBLE
                    txtCob.visibility = VISIBLE
                }
                else -> {
                    textSize = 20f
                    txtTime.visibility = VISIBLE
                    txtDelta.visibility = VISIBLE
                    viewIcon.visibility = VISIBLE
                    txtIob.visibility = GONE
                    txtCob.visibility = GONE
                }
            }

            txtBgValue.text = ReceiveData.getGlucoseAsString()
            txtBgValue.setTextColor(ReceiveData.getGlucoseColor())
            if (ReceiveData.isObsoleteShort() && !ReceiveData.isObsoleteLong()) {
                txtBgValue.paintFlags = Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                txtBgValue.paintFlags = 0
            }
            viewIcon.setImageIcon(BitmapUtils.getRateAsIcon())
            txtDelta.text = "Δ ${ReceiveData.getDeltaAsString()}"
            txtTime.text = "🕒 ${ReceiveData.getElapsedTimeMinuteAsString(context)}"
            if(ReceiveData.iob.isNaN())
                txtIob.visibility = GONE
            else
                txtIob.text = "💉 ${ReceiveData.getIobAsString()}"
            if(ReceiveData.cob.isNaN())
                txtCob.visibility = GONE
            else
                txtCob.text = "🍔 ${ReceiveData.getCobAsString()}"

            val usedSize = if(graphImage != null && (txtIob.visibility == VISIBLE || txtCob.visibility == VISIBLE)) size /2 else size

            txtBgValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize+usedSize*4f)
            viewIcon.minimumWidth = Utils.dpToPx(32f+usedSize*4f, context)
            txtDelta.setTextSize(TypedValue.COMPLEX_UNIT_SP, minOf(8f+usedSize*2f, MAX_SIZE))
            txtTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, minOf(8f+usedSize*2f, MAX_SIZE))
            txtIob.setTextSize(TypedValue.COMPLEX_UNIT_SP, minOf(8f+usedSize*2f, MAX_SIZE))
            txtCob.setTextSize(TypedValue.COMPLEX_UNIT_SP, minOf(8f+usedSize*2f, MAX_SIZE))


            if(graphImage != null) {
                if(chart != null) {
                    lockscreenView.setDrawingCacheEnabled(true)
                    lockscreenView.measure(
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                    Log.d(LOG_ID, "Mesasured width ${lockscreenView.measuredWidth} and height ${lockscreenView.measuredHeight} for chart")

                    graphImage.setImageBitmap(chart)
                    graphImage.layoutParams.height = lockscreenView.measuredWidth/3
                    graphImage.requestLayout()
                } else {
                    graphImage.visibility = GONE
                }
            }

            lockscreenView.setDrawingCacheEnabled(true)
            lockscreenView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            lockscreenView.layout(0, 0, lockscreenView.measuredWidth, lockscreenView.measuredHeight)

            Log.d(LOG_ID, "Mesasured width ${lockscreenView.measuredWidth} and height ${lockscreenView.measuredHeight}")

            val bitmap = Bitmap.createBitmap(lockscreenView.width, lockscreenView.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            lockscreenView.draw(canvas)
            return bitmap
        } catch (exc: Exception) {
            Log.e(LOG_ID, "createWallpaperView exception: " + exc.message.toString())
        }
        return null
    }

}