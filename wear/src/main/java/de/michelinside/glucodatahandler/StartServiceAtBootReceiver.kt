package de.michelinside.glucodatahandler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.michelinside.glucodatahandler.common.utils.Log

class StartServiceAtBootReceiver: BroadcastReceiver() {

    private val LOG_ID = "GDH.StartServiceAtBootReceiver"
    override fun onReceive(context: Context, intent: Intent) {
        try {
            Log.i(LOG_ID, "Start Service after intent action received: " + intent.action)
            GlucoDataServiceWear.start(context)
        } catch( exc: Exception ) {
            Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
        }
    }
}
