package de.michelinside.glucodatahandler.common

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log


open class GlucoseDataReceiver: BroadcastReceiver() {
    private val LOG_ID = "GlucoDataHandler.GlucoseDataReceiver"
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val action = intent.action
            if (action != Constants.GLUCODATA_BROADCAST_ACTION) {
                Log.e(LOG_ID, "action=" + action + " != " + Constants.GLUCODATA_BROADCAST_ACTION)
                return
            }

            ReceiveData.handleIntent(context, ReceiveDataSource.BROADCAST, intent.extras)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Receive exception: " + exc.message.toString() )
        }
    }
}