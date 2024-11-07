package de.michelinside.glucodataauto.receiver

import android.content.Context
import android.content.Intent
import android.util.Log
import de.michelinside.glucodataauto.GlucoDataServiceAuto
import de.michelinside.glucodatahandler.common.receiver.DiaboxReceiver

class DiaboxReceiver : DiaboxReceiver() {
    private val LOG_ID = "GDH.AA.DiaboxReceiver"
    override fun onReceiveData(context: Context, intent: Intent) {
        try {
            Log.v(LOG_ID, intent.action + " received: " + intent.extras.toString())
            GlucoDataServiceAuto.init(context)
            super.onReceiveData(context, intent)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Receive exception: " + exc.message.toString())
        }
    }
}