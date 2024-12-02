package de.michelinside.glucodatahandler.common.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifySource


class ScreenEventReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            Log.i(LOG_ID, "onReceive called for action ${intent.action}")
            update(context)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onReceive exception: " + exc.message.toString() )
        }
    }

    companion object {
        private val LOG_ID = "GDH.ScreenEventReceiver"
        private var displayState = Display.STATE_ON
        private var displayManager: DisplayManager? = null
        fun isDisplayOff() : Boolean {
            if(displayState == Display.STATE_OFF)
                return !ReceiveData.forceAlarm  // no screen of handling for alarms
            return false
        }
        fun isDisplayOn() : Boolean {
            return displayState == Display.STATE_ON
        }
        fun onDisplayOn(context: Context) {
            Log.i(LOG_ID, "onDisplayOn called")
            InternalNotifier.notify(context, NotifySource.DISPLAY_STATE_CHANGED, null)
        }
        fun onDisplayOff(context: Context) {
            Log.i(LOG_ID, "onDisplayOff called")
            InternalNotifier.notify(context, NotifySource.DISPLAY_STATE_CHANGED, null)
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

        fun update(context: Context) {
            Log.v(LOG_ID, "update called")
            if(displayManager == null)
                displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager?
            if(displayManager != null && displayManager!!.displays.isNotEmpty()) {
                val display = displayManager!!.displays[0]
                if(displayState != display.state) {
                    Log.i(LOG_ID, "Change display state from $displayState to ${display.state}")
                    val oldState = displayState
                    displayState = display.state
                    // state doze (2) is also handled as on!
                    if(displayState == Display.STATE_OFF) {
                        onDisplayOff(context)
                    } else if(oldState == Display.STATE_OFF) {
                        onDisplayOn(context)
                    }
                }
            }
        }

    }
}