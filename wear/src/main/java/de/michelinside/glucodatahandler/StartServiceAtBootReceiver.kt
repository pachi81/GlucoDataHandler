package de.michelinside.glucodatahandler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import de.michelinside.glucodatahandler.common.Constants

class StartServiceAtBootReceiver: BroadcastReceiver() {

    private val LOG_ID = "GlucoDataHandler.StartMyServiceAtBootReceiver"
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            Log.d(LOG_ID, "Start Service after booting")
            val serviceIntent = Intent(context, GlucoDataServiceWear::class.java)
            val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
            serviceIntent.putExtra(Constants.SHARED_PREF_FOREGROUND_SERVICE, sharedPref.getBoolean(Constants.SHARED_PREF_FOREGROUND_SERVICE, false))
            context.startService(serviceIntent)
        }
    }
}