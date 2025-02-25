package de.michelinside.glucodatahandler

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.utils.BitmapUtils
import android.provider.Settings
import android.view.ViewGroup
import android.widget.FrameLayout
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.widget.AodWidget
import kotlin.math.max


class AODAccessibilityService : AccessibilityService() {
    private var overlayView: View? = null
    private lateinit var windowManager: WindowManager
    private lateinit var powerManager: PowerManager
    private val LOG_ID = "GDH.Aod"

    private lateinit var aodWidget: AodWidget

    companion object {
        val LOG_ID = "GDH.Aod"
        fun isAccessibilitySettingsEnabled(context: Context): Boolean {
            try {
                val prefString =
                    Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                Log.d(LOG_ID, "Checking ACCESSIBILITY_SERVICES : ${prefString}")
                if(prefString.isNullOrEmpty())
                    return false
                val enabled = prefString.contains("${context.packageName}/${AODAccessibilityService::class.qualifiedName}")
                Log.d(LOG_ID, "Checking ACCESSIBILITY_SERVICES : ${enabled}")
                return enabled
            } catch (e: Exception) {
                Log.e(LOG_ID, "Error checking ACCESSIBILITY_SERVICES", e)
                return false
            }
        }
    }

    private fun triggerAodState(context: Context, state: Boolean) {
        val extras = Bundle()
        extras.putBoolean("aod_state", state)
        InternalNotifier.notify(context, NotifySource.AOD_STATE_CHANGED, extras)
    }


    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            try {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        Log.d(LOG_ID, "Screen turned off")
                        val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
                        val enabled = sharedPref.getBoolean(Constants.SHARED_PREF_AOD_WP_ENABLED, false)
                        if (enabled) {
                            checkAndCreateOverlay()
                        }
                        else {
                            Log.d(LOG_ID, "Aod disabled in settings")
                        }
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        Log.d(LOG_ID, "Screen turned on")
                        triggerAodState(context, false)
                        removeOverlay()
                        aodWidget.disable()
                        aodWidget.destroy()
                    }
                }
            } catch (e: Exception) {
                Log.e(LOG_ID, "Error in screenStateReceiver")
            }
        }
    }


    override fun onCreate() {
        super.onCreate()

        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

            Log.d(LOG_ID, "Service created")

            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            registerReceiver(screenStateReceiver, filter)
        } catch (e: Exception) {
            Log.e(LOG_ID, "Error in onCreate", e)
        }
    }

    //    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun checkAndCreateOverlay() {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())

        handler.postDelayed({
            if (!powerManager.isInteractive) {
                try {
                    triggerAodState(GlucoDataService.context!!, true)
                    aodWidget = AodWidget(this)
                    aodWidget.create()
                    removeAndCreateOverlay()
                } catch (e: Exception) {
                    Log.e(LOG_ID, "Error adding overlay", e)
                }
            }
        }, 1000)
    }

    fun removeAndCreateOverlay()
    {
        removeOverlay()
        createOverlay()
    }

    private fun createOverlay() {
        try {
            val bitmap = aodWidget.getBitmap()

            if (bitmap == null)
                return

            val imageView = ImageView(this)

            imageView.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            imageView.setImageBitmap(bitmap)
            val yOffset = max(0F, ((BitmapUtils.getScreenHeight()-bitmap.height)*aodWidget.getYPos()/100F))

            val layoutParams = WindowManager.LayoutParams().apply {
                type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                format = PixelFormat.TRANSLUCENT
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
            }.apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                x = 0
                y = yOffset.toInt()
            }

            Log.d(LOG_ID, "Adding overlay")

            windowManager.addView(imageView, layoutParams)
            overlayView = imageView
        } catch (e: Exception) {
            Log.e(LOG_ID, "Error creating overlay", e)
        }
    }

//    private fun createOverlay(bitmap: Bitmap) {
//        Log.d(LOG_ID, "Creating overlay")
//
//        if (overlayView != null)
//            return
//
//        var layoutParams = WindowManager.LayoutParams().apply {
//            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
//            format = PixelFormat.TRANSLUCENT
//            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
//                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
//            width = WindowManager.LayoutParams.WRAP_CONTENT
//            height = WindowManager.LayoutParams.WRAP_CONTENT
//            gravity = Gravity.CENTER
//        }
//
//        if (laidoutWidth != 0) {
//            val desired = BitmapUtils.getScreenWidth().toFloat() * 0.6f
//            val scaleFactor = desired / laidoutWidth.toFloat()
//            Log.d(LOG_ID, "scaleFactor: $scaleFactor")
//            layoutParams.width = (laidoutWidth * scaleFactor).toInt()
//            layoutParams.height = (laidoutHeight * scaleFactor).toInt()
//            Log.d(LOG_ID, "Scaled dimensions: $layoutParams.width * $layoutParams.height")
//        }
//
//        overlayView = LayoutInflater.from(this).inflate(R.layout.wallpaper, null)
//
//
//        updateOverlay()
//
//        try {
//
//            windowManager.addView(overlayView, layoutParams)
//
//            overlayView?.post {
//                if (laidoutWidth == 0) {
//                    laidoutWidth = overlayView!!.measuredWidth
//                    laidoutHeight = overlayView!!.measuredHeight
//                    Log.d(LOG_ID, "View dimensions: $laidoutWidth * $laidoutHeight")
//
//                    // Now we know the size of the overlay, recreate and apply scaling to desired size
//                    removeOverlay()
//                    createOverlay()
//                }
//                else {
//                    Log.d(LOG_ID, "View dimensions cached: $laidoutWidth * $laidoutHeight")
//                }
//            }
//
//
//            Log.d(LOG_ID, "Overlay added successfully")
//        } catch (e: Exception) {
//            Log.e(LOG_ID, "Error adding overlay", e)
//        }
//    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
//        Log.d(LOG_ID, "Received event: ${event.eventType}")
    }

    override fun onInterrupt() {
        Log.d(LOG_ID, "Service interrupted")
        removeOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(LOG_ID, "Service destroyed")

        try {
            unregisterReceiver(screenStateReceiver)
        } catch (e: Exception) {
            Log.e(LOG_ID, "Error unregistering receiver", e)
        }

        removeOverlay()
    }

    private fun removeOverlay() {
        Log.d(LOG_ID, "Removing overlay")

        overlayView?.let {
            try {
                windowManager.removeView(it)
                Log.d(LOG_ID, "Overlay removed successfully")
            } catch (e: Exception) {
                Log.e(LOG_ID, "Error removing overlay", e)
            }
            overlayView = null
        }
    }



}
