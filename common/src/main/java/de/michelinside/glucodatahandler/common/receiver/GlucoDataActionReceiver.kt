package de.michelinside.glucodatahandler.common.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import de.michelinside.glucodatahandler.common.Constants


class GlucoDataActionReceiver: BroadcastReceiver() {

    private val LOG_ID = "GDH.GlucoDataActionReceiver"
    override fun onReceive(context: Context, intent: Intent) {       
        try {
            Log.d(LOG_ID, "Action received: ${intent.action}")
            when(intent.action) {
                Constants.ACTION_FLOATING_WIDGET_TOGGLE -> {
                    Log.d(LOG_ID, "Action: floating widget toggle")
                    val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
                    with(sharedPref.edit()) {
                        putBoolean(
                            Constants.SHARED_PREF_FLOATING_WIDGET, !sharedPref.getBoolean(
                                Constants.SHARED_PREF_FLOATING_WIDGET, false))
                        apply()
                    }
                }
                else -> {
                    Log.w(LOG_ID, "Unknown action '${intent.action}' received!" )
                }
            }
        } catch( exc: Exception ) {
            Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
        }
    }
}
