package de.michelinside.glucodataauto.receiver

import android.content.Context
import android.content.Intent
import android.util.Log
import de.michelinside.glucodataauto.android_auto.CarNotification
import de.michelinside.glucodatahandler.common.receiver.AAPSReceiver as BaseAAPSReceiver

class AAPSReceiver : BaseAAPSReceiver() {
    private val LOG_ID = "GDH.AA.AAPSReceiver"
    override fun onReceive(context: Context, intent: Intent) {
        try {
            Log.v(LOG_ID, intent.action + " receveived: " + intent.extras.toString())
            CarNotification.initNotification(context)
            super.onReceive(context, intent)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Receive exception: " + exc.message.toString())
        }
    }
}