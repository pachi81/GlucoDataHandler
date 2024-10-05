package de.michelinside.glucodataauto.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import de.michelinside.glucodataauto.GlucoDataServiceAuto
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.notification.AlarmHandler
import de.michelinside.glucodatahandler.common.notifier.DataSource

open class GlucoDataActionReceiver: BroadcastReceiver() {
    private val LOG_ID = "GDH.AA.GlucoDataActionReceiver"
    override fun onReceive(context: Context, intent: Intent) {
        try {
            GlucoDataServiceAuto.init(context)
            val action = intent.action
            Log.v(LOG_ID, intent.action + " receveived: " + intent.extras.toString())
            if (action != Constants.GLUCODATA_ACTION) {
                Log.e(LOG_ID, "action=" + action + " != " + Constants.GLUCODATA_ACTION)
                return
            }
            val extras = intent.extras
            if (extras != null) {
                if (extras.containsKey(Constants.SETTINGS_BUNDLE)) {
                    val bundle = extras.getBundle(Constants.SETTINGS_BUNDLE)
                    Log.d(LOG_ID, "Glucose settings receceived")
                    GlucoDataService.setSettings(context, bundle!!)
                    extras.remove(Constants.SETTINGS_BUNDLE)
                }
                if (extras.containsKey(Constants.ALARM_SETTINGS_BUNDLE)) {
                    val bundle = extras.getBundle(Constants.ALARM_SETTINGS_BUNDLE)
                    Log.d(LOG_ID, "Alarm settings receceived")
                    AlarmHandler.setSettings(context, bundle!!)
                    extras.remove(Constants.ALARM_SETTINGS_BUNDLE)
                }
                ReceiveData.handleIntent(context, DataSource.GDH, extras, true)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Receive exception: " + exc.message.toString() )
        }
    }
}