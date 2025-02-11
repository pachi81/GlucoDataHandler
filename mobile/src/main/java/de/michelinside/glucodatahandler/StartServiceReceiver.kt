package de.michelinside.glucodatahandler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import de.michelinside.glucodatahandler.common.GlucoDataService

class StartServiceReceiver: BroadcastReceiver() {

    private val LOG_ID = "GDH.StartServiceReceiver"
    override fun onReceive(context: Context, intent: Intent) {
        try {
            Log.i(LOG_ID, "Start Service after intent action received: ${intent.action} - foreground: ${GlucoDataService.foreground}")
            if(!GlucoDataService.foreground) {
                val serviceIntent = Intent(
                    context,
                    GlucoDataServiceMobile::class.java
                )
                context.startForegroundService(serviceIntent)
            }
        } catch( exc: Exception ) {
            Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
        }
    }
}
