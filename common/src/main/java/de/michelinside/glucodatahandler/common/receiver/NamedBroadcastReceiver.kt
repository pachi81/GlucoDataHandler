package de.michelinside.glucodatahandler.common.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.michelinside.glucodatahandler.common.service.ReceiverManager

abstract class NamedBroadcastReceiver: BroadcastReceiver(), NamedReceiver {

    final override fun onReceive(context: Context, intent: Intent) {
        if(!ReceiverManager.isRegistered(this))
            return
        onReceiveData(context, intent)
    }

    abstract fun onReceiveData(context: Context, intent: Intent)

}