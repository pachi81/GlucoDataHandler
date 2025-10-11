package de.michelinside.glucodatahandler

import android.content.Context
import de.michelinside.glucodatahandler.common.utils.Log
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.receiver.ScreenEventReceiver

class ScreenEventReceiverWear: ScreenEventReceiver() {
    override fun onDisplayOn(context: Context) {
        Log.d(LOG_ID, "Wear Screen on - disable off screen handling" )
        ReceiveData.forceObsoleteOnScreenOff = false
        super.onDisplayOn(context)
    }
}