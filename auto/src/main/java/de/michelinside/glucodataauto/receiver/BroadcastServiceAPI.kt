package de.michelinside.glucodataauto.receiver

import android.content.Context
import android.content.Intent
import android.util.Log
import de.michelinside.glucodataauto.GlucoDataServiceAuto
import de.michelinside.glucodatahandler.common.receiver.BroadcastServiceAPI as BaseBroadcastServiceAPI

class BroadcastServiceAPI : BaseBroadcastServiceAPI() {
    private val LOG_ID = "GDH.AA.BroadcastServiceAPI"
    override fun onReceive(context: Context, intent: Intent) {
        try {
            Log.v(LOG_ID, intent.action + " received: " + intent.extras.toString())
            GlucoDataServiceAuto.init(context)
            super.onReceive(context, intent)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Receive exception: " + exc.message.toString())
        }
    }
}