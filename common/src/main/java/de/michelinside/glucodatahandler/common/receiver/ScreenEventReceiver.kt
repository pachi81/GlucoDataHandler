package de.michelinside.glucodatahandler.common.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifySource


open class ScreenEventReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            Log.i(LOG_ID, "onReceive called for action ${intent.action}")
            if(intent.action == Intent.ACTION_SCREEN_ON)
                setDisplayState(context, Display.STATE_ON)
            else if(intent.action == Intent.ACTION_SCREEN_OFF)
                setDisplayState(context, Display.STATE_OFF)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onReceive exception: " + exc.message.toString() )
        }
    }

    companion object {
        private val LOG_ID = "GDH.ScreenEventReceiver"
        private var displayState = Display.STATE_ON
        private var displayManager: DisplayManager? = null
        fun isDisplayOff() : Boolean {
            return displayState == Display.STATE_OFF
        }
        fun isDisplayOn() : Boolean {
            return displayState == Display.STATE_ON
        }
        fun triggerNotify(context: Context) {
            Log.i(LOG_ID, "triggerNotify called")
            InternalNotifier.notify(context, NotifySource.DISPLAY_STATE_CHANGED, null)
        }
        fun switchOn(context: Context) {
            if(isDisplayOff()) {
                Log.i(LOG_ID, "switchOn called")
                displayState = Display.STATE_ON
                triggerNotify(context)
            }
        }

    }
    open fun onDisplayOn(context: Context) {
        Log.i(LOG_ID, "onDisplayOn called")
        triggerNotify(context)
    }
    open fun onDisplayOff(context: Context) {
        Log.i(LOG_ID, "onDisplayOff called")
        triggerNotify(context)
    }

    fun reset(context: Context) {
        Log.d(LOG_ID, "reset called")
        if(displayManager != null) {
            if(displayState == Display.STATE_OFF) {
                displayState = Display.STATE_ON
                onDisplayOn(context)
            }
        }
    }

    private fun setDisplayState(context: Context, state: Int) {
        if(displayState != state) {
            Log.i(LOG_ID, "Change display state from $displayState to ${state}")
            val oldState = displayState
            displayState = state
            // state doze (2) is also handled as on!
            if(displayState == Display.STATE_OFF) {
                onDisplayOff(context)
            } else if(oldState == Display.STATE_OFF) {
                onDisplayOn(context)
            }
        }
    }

    fun update(context: Context) {
        Log.d(LOG_ID, "update called")
        if(displayManager == null)
            displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager?
        if(displayManager != null && displayManager!!.displays.isNotEmpty()) {
            val display = displayManager!!.displays[0]
            setDisplayState(context, display.state)
        } else {
            Log.w(LOG_ID, "No display manager or display found")
            setDisplayState(context, Display.STATE_ON)
        }
    }
}