package de.michelinside.glucodatahandler.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.chart.ChartBitmap
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.BitmapUtils
import de.michelinside.glucodatahandler.common.utils.Utils
import android.text.Spanned

abstract class WallpaperBase(protected val context: Context, protected val LOG_ID: String): NotifierInterface, SharedPreferences.OnSharedPreferenceChangeListener {
    protected abstract val enabledPref: String
    protected abstract val stylePref: String
    protected abstract val sizePref: String
    protected open val chartDurationPref =  ""
    protected open val MIN_SIZE = 6f
    protected open val MAX_SIZE = 24f
    protected var enabled = false
    protected var style = Constants.WIDGET_STYLE_GLUCOSE_TREND
    protected var size = 10
    protected lateinit var sharedPref: SharedPreferences
    private var chartBitmap: ChartBitmap? = null

    fun create() {
        try {
            Log.d(LOG_ID, "create called")
            sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            sharedPref.registerOnSharedPreferenceChangeListener(this)
            initSettings(sharedPref)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "create exception: " + exc.message.toString() )
        }
    }

    fun destroy() {
        try {
            Log.d(LOG_ID, "destroy called")
            sharedPref.unregisterOnSharedPreferenceChangeListener(this)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "destroy exception: " + exc.message.toString() )
        }
    }

    abstract fun enable()
    abstract fun disable()
    abstract fun update()

    protected open fun initSettings(sharedPreferences: SharedPreferences) {
        style = sharedPreferences.getString(stylePref, style)?: style
        size = sharedPreferences.getInt(sizePref, size)
        enabled = sharedPreferences.getBoolean(enabledPref, false)
        Log.d(LOG_ID, "initSettings called for style $style and size $size and enabled $enabled")
        if(enabled) {
            updateChartCreation()
            updateNotifier()
            enable()
        }
    }


    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        try {
            Log.d(LOG_ID, "onSharedPreferenceChanged called for key " + key)
            when(key) {
                stylePref -> {
                    if (key == stylePref && style != sharedPreferences.getString(stylePref, style)) {
                        style = sharedPreferences.getString(stylePref, style)!!
                        Log.d(LOG_ID, "New style: $style")
                        updateChartCreation()
                        updateNotifier()
                        update()
                    }
                }
                sizePref -> {
                    if (size != sharedPreferences.getInt(sizePref, size)) {
                        size = sharedPreferences.getInt(sizePref, size)
                        Log.d(LOG_ID, "New size: $size")
                        update()
                    }
                }
                enabledPref -> {
                    if (enabled != sharedPreferences.getBoolean(enabledPref, false)) {
                        enabled = sharedPreferences.getBoolean(enabledPref, false)
                        Log.d(LOG_ID, "Enabled changed: $enabled")
                        updateChartCreation()
                        updateNotifier()
                        if (enabled)
                            enable()
                        else
                            disable()
                    }
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString() )
        }
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            Log.d(LOG_ID, "OnNotifyData called for source $dataSource with extras ${Utils.dumpBundle(extras)} - graph-id ${chartBitmap?.chartId}")
            if (dataSource == NotifySource.GRAPH_CHANGED && chartBitmap != null && extras?.getInt(Constants.GRAPH_ID) != chartBitmap!!.chartId) {
                Log.v(LOG_ID, "Ignore graph changed as it is not for this chart")
                return  // ignore as it is not for this graph
            }
            update()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: " + exc.toString() )
        }
    }

    protected open fun getFilters() : MutableSet<NotifySource> {
        return mutableSetOf()
    }

    private fun updateNotifier() {
        Log.d(LOG_ID, "updateNotifier called - enabled=$enabled")
        if (enabled) {
            val filter = mutableSetOf(
                NotifySource.SETTINGS
            )
            filter.addAll(getFilters())
            if(style == Constants.WIDGET_STYLE_CHART_GLUCOSE_TREND_TIME_DELTA_IOB_COB) {
                filter.add(NotifySource.GRAPH_CHANGED)
            } else {
                filter.add(NotifySource.BROADCAST)
                filter.add(NotifySource.MESSAGECLIENT)
            }
            when (style) {
                Constants.WIDGET_STYLE_GLUCOSE_TREND_TIME_DELTA -> {
                    filter.add(NotifySource.TIME_VALUE)
                }
                Constants.WIDGET_STYLE_GLUCOSE_TREND_TIME_DELTA_IOB_COB -> {
                    filter.add(NotifySource.TIME_VALUE)
                    filter.add(NotifySource.IOB_COB_CHANGE)
                }
                else -> {
                    filter.add(NotifySource.OBSOLETE_VALUE)
                }
            }
            InternalNotifier.addNotifier(context, this, filter)
        } else {
            InternalNotifier.remNotifier(context, this)
        }
    }

    private fun getChart(): Bitmap? {
        return chartBitmap?.getBitmap()
    }

    private fun updateChartCreation() {
        if(enabled && style == Constants.WIDGET_STYLE_CHART_GLUCOSE_TREND_TIME_DELTA_IOB_COB)
            createBitmap()
        else
            removeBitmap()
    }

    private fun createBitmap() {
        if(chartBitmap == null && GlucoDataService.isServiceRunning) {
            Log.i(LOG_ID, "Create bitmap")
            chartBitmap = ChartBitmap(context, chartDurationPref, labelColor = Color.WHITE)
        }
    }

    private fun removeBitmap() {
        if(chartBitmap != null) {
            Log.i(LOG_ID, "Remove bitmap")
            chartBitmap!!.close()
            chartBitmap = null
        }
    }

    @SuppressLint("SetTextI18n")
    protected fun createWallpaperView(color: Int? = null, backgroundColor: Int = Color.TRANSPARENT): Bitmap? {
        try {
            Log.d(LOG_ID, "Create wallpaper view for size $size and style $style")
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

            lockscreenView.setBackgroundColor(backgroundColor)

            var textSize = 12f
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
                    textSize = 10f
                    txtTime.visibility = VISIBLE
                    txtDelta.visibility = VISIBLE
                    viewIcon.visibility = VISIBLE
                    txtIob.visibility = VISIBLE
                    txtCob.visibility = VISIBLE
                }
                else -> {
                    textSize = 12f
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

            if(color == null) {
                txtTime.text =  "🕒 ${ReceiveData.getElapsedTimeMinuteAsString(context)}"
                if(graphImage != null)
                    txtDelta.text = "Δ  ${ReceiveData.getDeltaAsString()}"
                else
                    txtDelta.text = " Δ ${ReceiveData.getDeltaAsString()}"
                if(ReceiveData.iob.isNaN())
                    txtIob.visibility = GONE
                else
                    txtIob.text = "💉 ${ReceiveData.getIobAsString()}"
                if(ReceiveData.cob.isNaN())
                    txtCob.visibility = GONE
                else
                    txtCob.text = "🍔 ${ReceiveData.getCobAsString()}"
            } else {
                txtDelta.text = buildImageString(context, R.drawable.icon_delta, "Δ", "   ${ReceiveData.getDeltaAsString()}", color)
                txtTime.text = buildImageString(context, R.drawable.icon_clock, "🕒", "   ${ReceiveData.getElapsedTimeMinuteAsString(context)}", color)
                if(ReceiveData.iob.isNaN())
                    txtIob.visibility = GONE
                else
                    txtIob.text = buildImageString(context, R.drawable.icon_injection, "💉", " ${ReceiveData.getIobAsString()}", color)
                if(ReceiveData.cob.isNaN())
                    txtCob.visibility = GONE
                else
                    txtCob.text = buildImageString(context, R.drawable.icon_burger, "🍔", " ${ReceiveData.getCobAsString()}", color)
            }
            val usedSize = if(graphImage != null && (txtIob.visibility == VISIBLE || txtCob.visibility == VISIBLE)) size /2 else size

            txtBgValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize+MIN_SIZE+usedSize*4f)
            viewIcon.minimumWidth = Utils.dpToPx((MIN_SIZE+usedSize)*4f, context)
            txtDelta.setTextSize(TypedValue.COMPLEX_UNIT_SP, minOf(MIN_SIZE+usedSize*2f, MAX_SIZE))
            txtTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, minOf(MIN_SIZE+usedSize*2f, MAX_SIZE))
            txtIob.setTextSize(TypedValue.COMPLEX_UNIT_SP, minOf(MIN_SIZE+usedSize*2f, MAX_SIZE))
            txtCob.setTextSize(TypedValue.COMPLEX_UNIT_SP, minOf(MIN_SIZE+usedSize*2f, MAX_SIZE))


            if(graphImage != null) {
                val chart = getChart()
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


            color?.let { col ->
                txtBgValue.setTextColor(col)
                viewIcon.setColorFilter(col)
                graphImage?.setColorFilter(col)
            }

            val bitmap = Bitmap.createBitmap(lockscreenView.width, lockscreenView.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            lockscreenView.draw(canvas)
            return bitmap
        } catch (exc: Exception) {
            Log.e(LOG_ID, "createWallpaperView exception: " + exc.message.toString())
        }
        return null
    }

    private fun buildImageString(context: Context, res: Int, emoji: String, text: String, colour: Int? = null): SpannableStringBuilder {
        val spannable = SpannableStringBuilder("")
        if (colour == null) {
            spannable.append(emoji + text)
        }
        else {
            spannable.append(" $text")
            val drawable: Drawable? = ContextCompat.getDrawable(context, res)
            if (drawable != null) {
                DrawableCompat.setTint(drawable, colour)
                drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
                spannable.setSpan(ImageSpan(drawable, ImageSpan.ALIGN_BASELINE), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(ForegroundColorSpan(colour), 1, spannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return spannable
    }

}