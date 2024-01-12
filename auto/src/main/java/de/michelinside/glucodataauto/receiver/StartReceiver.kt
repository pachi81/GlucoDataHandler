package de.michelinside.glucodataauto.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class StartReceiver: BroadcastReceiver() {

    private val LOG_ID = "GDH.AA.StartReceiver"
    override fun onReceive(context: Context, intent: Intent) {
        try {
            Log.i(LOG_ID, "Start Service after intent action received: " + intent.action)
        } catch( exc: Exception ) {
            Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
        }
    }
}
