package de.michelinside.glucodatahandler

import android.content.Context
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.receiver.ScreenEventReceiver

class ScreenEventReceiverWear: ScreenEventReceiver() {
    override fun onDisplayOn(context: Context) {
        ReceiveData.forceObsoleteOnScreenOff = false
        super.onDisplayOn(context)
    }
}