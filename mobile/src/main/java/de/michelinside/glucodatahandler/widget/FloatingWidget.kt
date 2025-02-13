package de.michelinside.glucodatahandler.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.WINDOW_SERVICE
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.*
import android.view.View.*
import android.widget.ImageView
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.chart.ChartBitmap
import de.michelinside.glucodatahandler.common.utils.Utils
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.PackageUtils
import java.util.*


class FloatingWidget(val context: Context) : NotifierInterface, SharedPreferences.OnSharedPreferenceChangeListener {
    private var windowManager: WindowManager? = null
    private lateinit var floatingView: View
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var imageView: ImageView
    private lateinit var sharedPref: SharedPreferences
    private lateinit var sharedInternalPref: SharedPreferences
    private val LOG_ID = "GDH.FloatingWidget"
    @SuppressLint("StaticFieldLeak")
    private var chartBitmap: ChartBitmap? = null

    @SuppressLint("InflateParams")
    fun create() {
        try {
            Log.d(LOG_ID, "create called")
            sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            sharedPref.registerOnSharedPreferenceChangeListener(this)

            sharedInternalPref = context.getSharedPreferences(Constants.SHARED_PREF_INTERNAL_TAG, Context.MODE_PRIVATE)

            //setting the layout parameters
            initLayout()
            update()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "create exception: " + exc.message.toString() )
        }
    }

    fun destroy() {
        try {
            Log.d(LOG_ID, "destroy called")
            sharedPref.unregisterOnSharedPreferenceChangeListener(this)
            remove()
            removeBitmap()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "destroy exception: " + exc.message.toString() )
        }
    }

    private fun initLayout() {
        //getting the widget layout from xml using layout inflater
        val layout = if(sharedPref.getString(Constants.SHARED_PREF_FLOATING_WIDGET_STYLE, Constants.WIDGET_STYLE_GLUCOSE_TREND_TIME_DELTA) == Constants.WIDGET_STYLE_CHART_GLUCOSE_TREND_TIME_DELTA_IOB_COB)
            R.layout.floating_widget_chart
        else
            R.layout.floating_widget
        Log.d(LOG_ID, "init layout with $layout")
        floatingView = LayoutInflater.from(context).inflate(R.layout.image_view, null)
        imageView = floatingView.findViewById(R.id.imageLayout)
        updateChartCreation()
    }

    private fun remove() {
        Log.d(LOG_ID, "remove called")
        try {
            InternalNotifier.remNotifier(context, this)
            if (windowManager != null) {
                try {
                    with(sharedInternalPref.edit()) {
                        putInt(Constants.SHARED_PREF_FLOATING_WIDGET_X,params.x)
                        putInt(Constants.SHARED_PREF_FLOATING_WIDGET_Y,params.y)
                        apply()
                    }
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "saving pos exception: " + exc.message.toString() )
                }
                windowManager?.removeView(floatingView)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "remove exception: " + exc.message.toString() )
        }
        windowManager = null
    }

    @SuppressLint("SetTextI18n")
    private fun setContent() {
        updateChartCreation()
        val size = sharedPref.getInt(Constants.SHARED_PREF_FLOATING_WIDGET_SIZE, 5)
        val style = getStyle()
        imageView.setImageBitmap(WidgetHelper.createWallpaperView(context, size, style, chartBitmap?.getBitmap()))
    }

    private fun getStyle(): String {
        return sharedPref.getString(Constants.SHARED_PREF_FLOATING_WIDGET_STYLE, Constants.WIDGET_STYLE_GLUCOSE_TREND_TIME_DELTA)?: Constants.WIDGET_STYLE_GLUCOSE_TREND_TIME_DELTA
    }

    private fun update() {
        Log.d(LOG_ID, "update called")
        try {
            if (sharedPref.getBoolean(Constants.SHARED_PREF_FLOATING_WIDGET, false)) {
                if (Settings.canDrawOverlays(context)) {
                    // to trigger re-start for the case of stopped by the system
                    setContent()
                    //getting windows services and adding the floating view to it
                    if (windowManager == null) {
                        val filter = mutableSetOf(
                            NotifySource.SETTINGS
                        )
                        val style = getStyle()
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
                        createWindow()
                    } else {
                        Log.d(LOG_ID, "update window")
                        floatingView.invalidate()
                        windowManager!!.updateViewLayout(floatingView, params)
                        Log.d(LOG_ID, "window size width/height: " + floatingView.width + "/" + floatingView.height)
                    }
                }
            } else {
                remove()
                removeBitmap()
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "update exception: " + exc.message.toString() )
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createWindow() {
        try {
            Log.d(LOG_ID, "create window")
            params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.START
            params.x = maxOf(sharedInternalPref.getInt(Constants.SHARED_PREF_FLOATING_WIDGET_X, 100), 0)
            params.y = maxOf(sharedInternalPref.getInt(Constants.SHARED_PREF_FLOATING_WIDGET_Y, 100), 0)

            windowManager = context.getSystemService(WINDOW_SERVICE) as WindowManager?
            windowManager!!.addView(floatingView, params)

            //val widget = floatingView.findViewById<View>(R.id.widget)
            imageView.setBackgroundColor(Utils.getBackgroundColor(sharedPref.getInt(Constants.SHARED_PREF_FLOATING_WIDGET_TRANSPARENCY, 3)))
            imageView.setOnClickListener {
                Log.d(LOG_ID, "onClick called")
                val action = PackageUtils.getTapAction(context, sharedPref.getString(Constants.SHARED_PREF_FLOATING_WIDGET_TAP_ACTION, null))
                if(action.first != null) {
                    if (action.second) {
                        context.sendBroadcast(action.first!!)
                    } else {
                        action.first!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(action.first)
                    }
                }
            }
            imageView.setOnLongClickListener {
                Log.d(LOG_ID, "onLongClick called")
                with(sharedPref.edit()) {
                    putBoolean(Constants.SHARED_PREF_FLOATING_WIDGET, false)
                    apply()
                }
                remove()
                true
            }
            imageView.setOnTouchListener(object : OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var initialTouchX = 0f
                private var initialTouchY = 0f
                private var startClickTime : Long = 0
                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    try {
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                initialX = params.x
                                initialY = params.y
                                initialTouchX = event.rawX
                                initialTouchY = event.rawY
                                startClickTime = Calendar.getInstance().timeInMillis
                                return true
                            }
                            MotionEvent.ACTION_UP -> {
                                // only check duration, if there was no movement...
                                with(sharedInternalPref.edit()) {
                                    putInt(Constants.SHARED_PREF_FLOATING_WIDGET_X,params.x)
                                    putInt(Constants.SHARED_PREF_FLOATING_WIDGET_Y,params.y)
                                    apply()
                                }
                                if  (Math.abs(params.x - initialX) < 50 && Math.abs(params.y - initialY) < 50 ) {
                                    val duration = Calendar.getInstance().timeInMillis - startClickTime
                                    Log.d(LOG_ID, "Duration: " + duration.toString() + " - x=" + Math.abs(params.x - initialX) + " y=" + Math.abs(params.y - initialY) )
                                    if (duration < 200) {
                                        Log.d(LOG_ID, "Call onClick after " + duration.toString() + "ms")
                                        imageView.performClick()
                                    } else {
                                        val longClickTime = sharedPref.getInt(Constants.SHARED_PREF_FLOATING_WIDGET_TIME_TO_CLOSE, 4) * 1000
                                        if (longClickTime > 0 && duration > longClickTime) {
                                            Log.d(LOG_ID, "Call onLongClick after " + duration.toString() + "ms")
                                            imageView.performLongClick()
                                        }
                                    }
                                }
                                return true
                            }
                            MotionEvent.ACTION_MOVE -> {
                                //this code is helping the widget to move around the screen with fingers
                                params.x = initialX + (event.rawX - initialTouchX).toInt()
                                params.y = initialY + (event.rawY - initialTouchY).toInt()
                                windowManager!!.updateViewLayout(floatingView, params)
                                return true
                            }
                        }
                    } catch (exc: Exception) {
                        Log.e(LOG_ID, "onTouch exception: " + exc.toString() )
                    }
                    return false
                }
            })
        } catch (exc: Exception) {
            Log.e(LOG_ID, "createWindow exception: " + exc.message.toString() )
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        try {
            Log.d(LOG_ID, "onSharedPreferenceChanged called for key " + key)
            when(key) {
                Constants.SHARED_PREF_FLOATING_WIDGET,
                Constants.SHARED_PREF_FLOATING_WIDGET_SIZE,
                Constants.SHARED_PREF_FLOATING_WIDGET_TRANSPARENCY -> {
                    remove()
                    update()
                }
                Constants.SHARED_PREF_FLOATING_WIDGET_STYLE -> {
                    updateChartCreation()
                    remove()
                    initLayout()
                    update()
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

    private fun updateChartCreation() {
        val style = getStyle()
        if(style == Constants.WIDGET_STYLE_CHART_GLUCOSE_TREND_TIME_DELTA_IOB_COB)
            createBitmap()
        else
            removeBitmap()
    }

    private fun createBitmap() {
        if(chartBitmap == null && GlucoDataService.isServiceRunning) {
            Log.i(LOG_ID, "Create bitmap")
            chartBitmap = ChartBitmap(context, labelColor = Color.WHITE)
        }
    }

    private fun removeBitmap() {
        if(chartBitmap != null) {
            Log.i(LOG_ID, "Remove bitmap")
            chartBitmap!!.close()
            chartBitmap = null
        }
    }
}