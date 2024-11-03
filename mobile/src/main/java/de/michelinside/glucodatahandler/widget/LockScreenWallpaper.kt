package de.michelinside.glucodatahandler.widget

import android.app.WallpaperManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
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
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.BitmapUtils
import de.michelinside.glucodatahandler.common.utils.Utils
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.math.max


object LockScreenWallpaper : NotifierInterface, SharedPreferences.OnSharedPreferenceChangeListener {
    private val LOG_ID = "GDH.LockScreenWallpaper"
    private var enabled = false
    private var yPos = 75
    private var style = Constants.WIDGET_STYLE_GLUCOSE_TREND
    private var size = 10
    private val MAX_SIZE = 24f

    fun create(context: Context) {
        try {
            Log.d(LOG_ID, "create called")
            val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            sharedPref.registerOnSharedPreferenceChangeListener(this)
            onSharedPreferenceChanged(sharedPref, null)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "create exception: " + exc.message.toString() )
        }
    }

    fun destroy(context: Context) {
        try {
            Log.d(LOG_ID, "destroy called")
            val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            sharedPref.unregisterOnSharedPreferenceChangeListener(this)
            disable(context)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "destroy exception: " + exc.message.toString() )
        }
    }

    private fun enable(context: Context) {
        if (!enabled) {
            Log.d(LOG_ID, "enable called")
            enabled = true
            updateNotifier(context)
            updateLockScreen(context)
        }
    }

    private fun updateNotifier(context: Context) {
        if (enabled) {
            val filter = mutableSetOf(
                NotifySource.BROADCAST,
                NotifySource.MESSAGECLIENT,
                NotifySource.SETTINGS
            )
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
        }
    }

    private fun disable(context: Context) {
        if (enabled) {
            Log.d(LOG_ID, "disable called")
            enabled = false
            InternalNotifier.remNotifier(context, this)
            setWallpaper(null, context)
        }
    }

    fun updateLockScreen(context: Context) {
        try {
            Log.v(LOG_ID, "updateLockScreen called - enabled=$enabled")
            if (enabled) {
                setWallpaper(getBitmapForWallpaper(context), context)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateLockScreen exception: " + exc.message.toString() )
        }
    }

    private fun setWallpaper(bitmap: Bitmap?, context: Context) {
        GlobalScope.launch {
            try {
                Log.v(LOG_ID, "updateLockScreen called for bitmap $bitmap")
                val wallpaperManager = WallpaperManager.getInstance(context)
                val wallpaper = if (bitmap != null) createWallpaper(bitmap, context) else null
                wallpaperManager.setBitmap(wallpaper, null, false, WallpaperManager.FLAG_LOCK)
                //wallpaper?.recycle()
            } catch (exc: Exception) {
                Log.e(LOG_ID, "updateLockScreen exception: " + exc.message.toString())
            }
        }
    }

    private fun createWallpaper(bitmap: Bitmap, context: Context): Bitmap? {
        try {
            Log.v(LOG_ID, "creatWallpaper called")
            val screenWidth = BitmapUtils.getScreenWidth()
            val screenHeigth = BitmapUtils.getScreenHeight()
            val screenDPI = BitmapUtils.getScreenDpi().toFloat()
            val wallpaper = Bitmap.createBitmap(screenWidth, screenHeigth, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(wallpaper)
            val drawable = BitmapDrawable(context.resources, bitmap)
            drawable.setBounds(0, 0, screenWidth, screenHeigth)
            val xOffset = ((screenWidth-bitmap.width)/2F) //*1.2F-(screenDPI*0.3F)
            val yOffset = max(0F, ((screenHeigth-bitmap.height)*yPos/100F)) //-(screenDPI*0.3F))
            Log.d(LOG_ID, "Create wallpaper at x=$xOffset/$screenWidth and y=$yOffset/$screenHeigth DPI=$screenDPI")
            canvas.drawBitmap(bitmap, xOffset, yOffset, Paint(Paint.ANTI_ALIAS_FLAG))
            bitmap.recycle()
            return wallpaper
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateLockScreen exception: " + exc.message.toString() )
        }
        return null
    }

    private fun createWallpaperView(context: Context): Bitmap? {
        try {
            //getting the widget layout from xml using layout inflater
            val lockscreenView = LayoutInflater.from(context).inflate(R.layout.floating_widget, null)
            val txtBgValue: TextView = lockscreenView.findViewById(R.id.glucose)
            val viewIcon: ImageView = lockscreenView.findViewById(R.id.trendImage)
            val txtDelta: TextView = lockscreenView.findViewById(R.id.deltaText)
            val txtTime: TextView = lockscreenView.findViewById(R.id.timeText)
            val txtIob: TextView = lockscreenView.findViewById(R.id.iobText)
            val txtCob: TextView = lockscreenView.findViewById(R.id.cobText)

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
            txtIob.text = "💉 ${ReceiveData.getIobAsString()}"
            txtCob.text = "🍔 ${ReceiveData.getCobAsString()}"

            txtBgValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize+size*4f)
            viewIcon.minimumWidth = Utils.dpToPx(32f+size*4f, context)
            txtDelta.setTextSize(TypedValue.COMPLEX_UNIT_SP, minOf(8f+size*2f, MAX_SIZE))
            txtTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, minOf(8f+size*2f, MAX_SIZE))
            txtIob.setTextSize(TypedValue.COMPLEX_UNIT_SP, minOf(8f+size*2f, MAX_SIZE))
            txtCob.setTextSize(TypedValue.COMPLEX_UNIT_SP, minOf(8f+size*2f, MAX_SIZE))

            lockscreenView.setDrawingCacheEnabled(true)
            lockscreenView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            lockscreenView.layout(0, 0, lockscreenView.measuredWidth, lockscreenView.measuredHeight)

            val bitmap = Bitmap.createBitmap(lockscreenView.width, lockscreenView.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            lockscreenView.draw(canvas)
            return bitmap
        } catch (exc: Exception) {
            Log.e(LOG_ID, "createWallpaperView exception: " + exc.message.toString())
        }
        return null
    }

    private fun getBitmapForWallpaper(context: Context): Bitmap? {
        return createWallpaperView(context)
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        try {
            Log.v(LOG_ID, "OnNotifyData called for source $dataSource")
            updateLockScreen(context)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnNotifyData exception: " + exc.message.toString() )
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        try {
            Log.v(LOG_ID, "onSharedPreferenceChanged called for key $key")
            var changed = false
            if (yPos != sharedPreferences.getInt(Constants.SHARED_PREF_LOCKSCREEN_WP_Y_POS, 75)) {
                yPos = sharedPreferences.getInt(Constants.SHARED_PREF_LOCKSCREEN_WP_Y_POS, 75)
                Log.d(LOG_ID, "New Y pos: $yPos")
                changed = true
            }
            if (style != sharedPreferences.getString(Constants.SHARED_PREF_LOCKSCREEN_WP_STYLE, style)) {
                style = sharedPreferences.getString(Constants.SHARED_PREF_LOCKSCREEN_WP_STYLE, style)!!
                Log.d(LOG_ID, "New style: $style")
                updateNotifier(GlucoDataService.context!!)
                changed = true
            }
            if (size != sharedPreferences.getInt(Constants.SHARED_PREF_LOCKSCREEN_WP_SIZE, size)) {
                size = sharedPreferences.getInt(Constants.SHARED_PREF_LOCKSCREEN_WP_SIZE, size)
                Log.d(LOG_ID, "New size: $size")
                updateNotifier(GlucoDataService.context!!)
                changed = true
            }
            if (enabled != sharedPreferences.getBoolean(Constants.SHARED_PREF_LOCKSCREEN_WP_ENABLED, false)) {
                if (sharedPreferences.getBoolean(Constants.SHARED_PREF_LOCKSCREEN_WP_ENABLED, false))
                    enable(GlucoDataService.context!!)
                else
                    disable(GlucoDataService.context!!)
            } else if (changed) {
                updateLockScreen(GlucoDataService.context!!)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.message.toString() )
        }
    }
}