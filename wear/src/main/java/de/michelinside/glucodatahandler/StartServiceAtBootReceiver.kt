package de.michelinside.glucodatahandler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class StartServiceAtBootReceiver: BroadcastReceiver() {

    private val LOG_ID = "GlucoDataHandler.StartMyServiceAtBootReceiver"
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            Log.d(LOG_ID, "Start Service after booting")
            GlucoDataServiceWear.start(context)
        }
    }
}