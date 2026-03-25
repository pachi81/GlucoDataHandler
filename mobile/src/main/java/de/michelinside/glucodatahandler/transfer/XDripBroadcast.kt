package de.michelinside.glucodatahandler.transfer

import android.content.Context
import android.content.Intent
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.Intents
import de.michelinside.glucodatahandler.common.receiver.XDripBroadcastReceiver

class XDripBroadcast: AppBroadcasts() {
    override val LOG_ID = "GDH.transfer.XDripBroadcast"
    override val receiverPrefKey = Constants.SHARED_PREF_XDRIP_BROADCAST_RECEIVERS
    override val enablePref = Constants.SHARED_PREF_SEND_XDRIP_BROADCAST

    override fun getIntent(context: Context): Intent? {
        val xDripExtras = XDripBroadcastReceiver.createExtras(context)
        if (xDripExtras != null) {
            val intent = Intent(Intents.XDRIP_BROADCAST_ACTION)
            intent.putExtras(xDripExtras)
            return intent
        }
        return null
    }
}