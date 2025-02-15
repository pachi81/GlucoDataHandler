package de.michelinside.glucodatahandler

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifierInterface
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import de.michelinside.glucodatahandler.common.utils.BitmapUtils

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.provider.DocumentsContract
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
//import androidx.core.app.ActivityCompat.startActivityForResult
import androidx.core.content.ContextCompat.startActivity
import de.michelinside.glucodatahandler.common.GlucoDataService.Companion.context
import de.michelinside.glucodatahandler.preferences.ExportImportSettingsFragment.Companion.IMPORT_PHONE_SETTINGS


import androidx.appcompat.app.AppCompatActivity


class AODAccessibilityService : AccessibilityService(), NotifierInterface {
    private var overlayView: View? = null
    private lateinit var windowManager: WindowManager
    private lateinit var powerManager: PowerManager
    private val LOG_ID = "GDH.Aod"

    private var style = Constants.WIDGET_STYLE_GLUCOSE_TREND_TIME_DELTA_IOB_COB

    private var laidoutWidth = 0
    private var laidoutHeight = 0

    companion object {
        val LOG_ID = "GDH.Aod"

//        fun checkAndEnableAccessibilityService()
//        {
//            context?.let {
//            }
//
//        }

        fun isAccessibilitySettingsEnabled(context: Context): Boolean {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            return enabledServices.isNotEmpty()
        }

        fun requestAccessibilityService(context: Context) {
//            Log.d(LOG_ID, "Requesting Accessibility Service")
//            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
//            Log.d(LOG_ID, "Requested Accessibility Service " + isAccessibilitySettingsEnabled(context!!))


//            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
//            startActivityForResult(intent, IMPORT_PHONE_SETTINGS)

        }


    }

//    fun isAccessServiceEnabled(context: Context): Boolean {
//        val prefString =
//            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
//        return prefString.contains("${context.packageName}/${context.packageName}.${context.getString(R.string.access_service_name)}")
//    }


    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d(LOG_ID, "Screen turned off")

                    if (context != null) {
                        val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
                        val enabled = sharedPref.getBoolean(Constants.SHARED_PREF_LOCKSCREEN_WP_ENABLED, false)
                        if (enabled) {
                            checkAndCreateOverlay()
                        }
                    }
                }
                Intent.ACTION_SCREEN_ON -> {
                    Log.d(LOG_ID, "Screen turned on")
                    removeNotifier()
                    removeOverlay()
                }
            }
        }
    }


    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

        Log.d(LOG_ID, "Service created")

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenStateReceiver, filter)
    }

    //    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun checkAndCreateOverlay() {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())

        handler.postDelayed({
            if (!powerManager.isInteractive && overlayView == null) {
                try {
                    createOverlay()
                    addNotifier(this)
                } catch (e: Exception) {
                    Log.e(LOG_ID, "Error adding overlay", e)
                }
            }
        }, 1000)
    }


    private fun createOverlay() {
        Log.d(LOG_ID, "Creating overlay")

        if (overlayView != null)
            return

        var layoutParams = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.CENTER
        }

        if (laidoutWidth != 0) {
            val desired = BitmapUtils.getScreenWidth().toFloat() * 0.6f
            val scaleFactor = desired / laidoutWidth.toFloat()
            Log.d(LOG_ID, "scaleFactor: $scaleFactor")
            layoutParams.width = (laidoutWidth * scaleFactor).toInt()
            layoutParams.height = (laidoutHeight * scaleFactor).toInt()
            Log.d(LOG_ID, "Scaled dimensions: $layoutParams.width * $layoutParams.height")
        }

        overlayView = LayoutInflater.from(this).inflate(R.layout.wallpaper, null)


        updateOverlay()

        try {

            windowManager.addView(overlayView, layoutParams)

            overlayView?.post {
                if (laidoutWidth == 0) {
                    laidoutWidth = overlayView!!.measuredWidth
                    laidoutHeight = overlayView!!.measuredHeight
                    Log.d(LOG_ID, "View dimensions: $laidoutWidth * $laidoutHeight")

                    // Now we know the size of the overlay, recreate and apply scaling to desired size
                    removeOverlay()
                    createOverlay()
                }
                else {
                    Log.d(LOG_ID, "View dimensions cached: $laidoutWidth * $laidoutHeight")
                }
            }


            Log.d(LOG_ID, "Overlay added successfully")
        } catch (e: Exception) {
            Log.e(LOG_ID, "Error adding overlay", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
//        Log.d(TAG, "Received event: ${event.eventType}")
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

    private fun updateOverlay() {
        overlayView?.let {
            val txtBgValue: TextView = it.findViewById(R.id.glucose)
            val viewIcon: ImageView = it.findViewById(R.id.trendImage)
            val txtDelta: TextView = it.findViewById(R.id.deltaText)
            val txtTime: TextView = it.findViewById(R.id.timeText)
            val txtIob: TextView = it.findViewById(R.id.iobText)
            val txtCob: TextView = it.findViewById(R.id.cobText)

            viewIcon.setColorFilter(ContextCompat.getColor(this, de.michelinside.glucodatahandler.common.R.color.white), PorterDuff.Mode.SRC_ATOP)

            txtBgValue.text = ReceiveData.getGlucoseAsString()

            viewIcon.setImageIcon(BitmapUtils.getRateAsIcon())
            txtDelta.text = "Δ ${ReceiveData.getDeltaAsString()}"
            txtTime.text = "🕒 ${ReceiveData.getElapsedTimeMinuteAsString(this)}"
            txtIob.text = "💉 ${ReceiveData.getIobAsString()}"
            txtCob.text = "🍔 ${ReceiveData.getCobAsString()}"
        }
    }


    private fun addNotifier(context: Context) {
        Log.d(LOG_ID, "Adding notifier")

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

    private fun removeNotifier()
    {
        Log.d(LOG_ID, "Removing notifier")
        InternalNotifier.remNotifier(this, this);
    }

    override fun OnNotifyData(context: Context, dataSource: NotifySource, extras: Bundle?) {
        Log.d(LOG_ID, "OnNotifyData called for source $dataSource : " + extras)

        removeOverlay()
        createOverlay()

//        TODO("Not yet implemented")
    }

}
