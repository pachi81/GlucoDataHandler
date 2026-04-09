package de.michelinside.glucodatahandler

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.PowerManager
import de.michelinside.glucodatahandler.common.utils.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.utils.BitmapUtils
import android.provider.Settings
import android.view.Display
import android.view.ViewGroup
import android.widget.FrameLayout
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.widget.AodWidget
import kotlin.math.abs
import kotlin.math.max


class AODAccessibilityService : AccessibilityService() {
    private var overlayView: View? = null
    private lateinit var windowManager: WindowManager
    private lateinit var powerManager: PowerManager
    private val LOG_ID = "GDH.Aod"
    private var currentState = Display.STATE_UNKNOWN

    private var aodWidget: AodWidget? = null

    private var yPosOffset = 0
    private var yPosOffsetFactor = 1
    private val MAX_Y_POS_OFFSET = 10
    val offset: Int get() {
        if(abs(yPosOffset) >= MAX_Y_POS_OFFSET)
            yPosOffsetFactor *= -1
        yPosOffset += yPosOffsetFactor
        Log.d(LOG_ID, "Offset: $yPosOffset")
        return yPosOffset
    }

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


    private lateinit var displayManager: android.hardware.display.DisplayManager

    private val displayListener = object : android.hardware.display.DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) {
                val display = displayManager.getDisplay(displayId)
                val state = display?.state ?: Display.STATE_UNKNOWN
                Log.d(LOG_ID, "Display state changed: $state")
                when (state) {
                    Display.STATE_OFF, Display.STATE_DOZE, Display.STATE_DOZE_SUSPEND -> {
                        displayStateChanged(Display.STATE_OFF, 1)  // no delay, as this state is already triggered with some delay
                    }
                    Display.STATE_ON -> {
                        displayStateChanged(Display.STATE_ON)
                    }
                    else -> {
                        Log.w(LOG_ID, "Unknown display state: $state")
                    }
                }
            }
        }
    }

    private fun displayStateChanged(state: Int, delayMillis: Long = 1000) {
        try {
            if(state == Display.STATE_UNKNOWN || currentState == state)
                return

            Log.i(LOG_ID, "Display state changed from $currentState to $state")
            when (state) {
                Display.STATE_OFF, Display.STATE_DOZE, Display.STATE_DOZE_SUSPEND -> {
                    Log.d(LOG_ID, "Phone screen is off or in Doze")
                    val sharedPref = getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
                    val enabled = sharedPref.getBoolean(Constants.SHARED_PREF_AOD_WP_ENABLED, false)
                    if (enabled) {
                        checkAndCreateOverlay(delayMillis)
                    } else {
                        if (aodWidget != null) {
                            aodWidget!!.destroy()
                            aodWidget = null
                        }
                    }
                }
                Display.STATE_ON -> {
                    Log.d(LOG_ID, "Phone screen turned on")
                    triggerAodState(GlucoDataService.context!!, false)
                    aodWidget?.pause()
                    removeOverlay()
                }
            }
            currentState = state
        } catch (e: Exception) {
            Log.e(LOG_ID, "Error in displayStateChanged", e)
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
                        Log.d(LOG_ID, "Screen off")
                        displayStateChanged(Display.STATE_OFF)
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        Log.d(LOG_ID, "Screen on")
                        displayStateChanged(Display.STATE_ON)
                    }
                }
            } catch (e: Exception) {
                Log.e(LOG_ID, "Error in screenStateReceiver: $e")
            }
        }
    }


    override fun onCreate() {
        super.onCreate()

        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            powerManager = getSystemService(POWER_SERVICE) as PowerManager
            displayManager = getSystemService(DISPLAY_SERVICE) as android.hardware.display.DisplayManager

            Log.d(LOG_ID, "Service created")

            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            registerReceiver(screenStateReceiver, filter)

            // Registriere den DisplayListener
            displayManager.registerDisplayListener(displayListener, null)

            // Initialer Check
            val defaultDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
            Log.d(LOG_ID, "Default display state: ${defaultDisplay?.state}")
            if (defaultDisplay != null && defaultDisplay.state != Display.STATE_ON) {
                Log.i(LOG_ID, "Initial default display state is ${defaultDisplay.state}")
                displayStateChanged(defaultDisplay.state)
            }

        } catch (e: Exception) {
            Log.e(LOG_ID, "Error in onCreate", e)
        }
    }

    //    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun checkAndCreateOverlay(delayMillis: Long = 1000) {
        Log.d(LOG_ID, "Checking if overlay should be created - delay: $delayMillis")
        val handler = android.os.Handler(android.os.Looper.getMainLooper())

        handler.postDelayed({
            if (!powerManager.isInteractive) {
                try {
                    triggerAodState(GlucoDataService.context!!, true)
                    if(aodWidget == null) {
                        aodWidget = AodWidget(this)
                        aodWidget!!.create()
                    } else {
                        aodWidget!!.resume()
                    }
                    removeAndCreateOverlay()
                } catch (e: Exception) {
                    Log.e(LOG_ID, "Error adding overlay", e)
                }
            }
        }, delayMillis)
    }

    fun removeAndCreateOverlay()
    {
        removeOverlay()
        createOverlay()
    }

    private fun createOverlay() {
        try {
            if (powerManager.isInteractive)
                return
            if(aodWidget == null) {
                aodWidget = AodWidget(this)
                aodWidget!!.create()
            }
            val bitmap = aodWidget!!.getBitmap()

            if (bitmap == null)
                return

            val imageView = ImageView(this)

            imageView.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            imageView.setImageBitmap(bitmap)
            val yOffset = max(0F, ((BitmapUtils.getScreenHeight(this)-bitmap.height)*aodWidget!!.getYPos()/100F)+offset)
            val xOffset = max(0F, ((BitmapUtils.getScreenWidth(this)-bitmap.width)*aodWidget!!.getXPos()/40F)+offset)

            val layoutParams = WindowManager.LayoutParams().apply {
                type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                format = PixelFormat.TRANSLUCENT
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
            }.apply {
                gravity = Gravity.TOP or Gravity.START
                x = xOffset.toInt()
                y = yOffset.toInt()
            }

            Log.d(LOG_ID, "Adding overlay at y-pos ${layoutParams.y}")

            windowManager.addView(imageView, layoutParams)
            overlayView = imageView
        } catch (e: Exception) {
            Log.e(LOG_ID, "Error creating overlay", e)
        }
    }

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
            displayManager.unregisterDisplayListener(displayListener)
            triggerAodState(GlucoDataService.context!!, false)
        } catch (e: Exception) {
            Log.e(LOG_ID, "Error unregistering receiver", e)
        }

        removeOverlay()
        if (aodWidget != null) {
            aodWidget!!.destroy()
            aodWidget = null
        }
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
