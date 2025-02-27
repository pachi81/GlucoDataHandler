package de.michelinside.glucodatahandler.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.WINDOW_SERVICE
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Log
import android.view.*
import android.view.View.*
import android.widget.ImageView
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.utils.Utils
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.utils.PackageUtils
import java.util.*


class FloatingWidget(context: Context): WallpaperBase(context, "GDH.FloatingWidget") {
    private var windowManager: WindowManager? = null
    private lateinit var floatingView: View
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var imageView: ImageView
    private lateinit var sharedInternalPref: SharedPreferences
    override val enabledPref = Constants.SHARED_PREF_FLOATING_WIDGET
    override val stylePref = Constants.SHARED_PREF_FLOATING_WIDGET_STYLE
    override val sizePref = Constants.SHARED_PREF_FLOATING_WIDGET_SIZE
    override val chartDurationPref = Constants.SHARED_PREF_FLOATING_WIDGET_GRAPH_DURATION
    override val MIN_SIZE = 6f
    override val MAX_SIZE = 30f

    override fun enable() {
        try {
            Log.d(LOG_ID, "enable called")
            sharedInternalPref = context.getSharedPreferences(Constants.SHARED_PREF_INTERNAL_TAG, Context.MODE_PRIVATE)
            //setting the layout parameters
            floatingView = LayoutInflater.from(context).inflate(R.layout.image_view, null)
            imageView = floatingView.findViewById(R.id.imageLayout)
            update()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "create exception: " + exc.message.toString() )
        }
    }

    override fun disable() {
        try {
            Log.d(LOG_ID, "disable called")
            remove()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "destroy exception: " + exc.message.toString() )
        }
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
        imageView.setImageBitmap(createWallpaperView(backgroundColor = Utils.getBackgroundColor(sharedPref.getInt(Constants.SHARED_PREF_FLOATING_WIDGET_TRANSPARENCY, 3))))
    }

    override fun update() {
        Log.d(LOG_ID, "update called")
        try {
            if (enabled) {
                if (Settings.canDrawOverlays(context)) {
                    // to trigger re-start for the case of stopped by the system
                    setContent()
                    //getting windows services and adding the floating view to it
                    if (windowManager == null) {
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
                                if(!sharedPref.getBoolean(Constants.SHARED_PREF_FLOATING_WIDGET_LOCK_POSITION, false)) {
                                    //this code is helping the widget to move around the screen with fingers
                                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                                    windowManager!!.updateViewLayout(floatingView, params)
                                }
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

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        try {
            Log.d(LOG_ID, "onSharedPreferenceChanged called for key " + key)
            when(key) {
                Constants.SHARED_PREF_FLOATING_WIDGET,
                Constants.SHARED_PREF_FLOATING_WIDGET_SIZE,
                Constants.SHARED_PREF_FLOATING_WIDGET_STYLE -> {
                    remove()
                }
                Constants.SHARED_PREF_FLOATING_WIDGET_TAP_ACTION,
                Constants.SHARED_PREF_FLOATING_WIDGET_LOCK_POSITION,
                Constants.SHARED_PREF_FLOATING_WIDGET_TRANSPARENCY -> {
                    remove()
                    update()
                }
            }
            super.onSharedPreferenceChanged(sharedPreferences, key)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString() )
        }
    }

}