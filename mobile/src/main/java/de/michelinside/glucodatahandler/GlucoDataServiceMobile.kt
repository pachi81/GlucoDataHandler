package de.michelinside.glucodatahandler

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import de.michelinside.glucodatahandler.common.*

class GlucoDataServiceMobile: GlucoDataService(), ReceiveDataInterface {
    private val LOG_ID = "GlucoDataHandler.GlucoDataServiceMobile"
    init {
        Log.d(LOG_ID, "init called")
        ReceiveData.addNotifier(TaskerDataReceiver)
        ReceiveData.addNotifier(this)
    }

    override fun OnReceiveData(context: Context, dataSource: ReceiveDataSource, extras: Bundle?) {
        try {
            super.OnReceiveData(context, dataSource, extras)
            if (dataSource == ReceiveDataSource.MESSAGECLIENT && extras != null)
            {
                val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
                if (sharedPref.getBoolean(Constants.SHARED_PREF_SEND_TO_GLUCODATA_AOD, false)) {
                    Log.d(LOG_ID, "Resend Glucodata Broadcast to glucodata AOD")
                    val intent = Intent()
                    intent.action = Constants.GLUCODATA_BROADCAST_ACTION
                    intent.putExtras(extras)
                    intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    intent.setPackage("de.metalgearsonic.glucodata.aod")
                    context.sendBroadcast(intent)
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnReceiveData exception: " + exc.message.toString() )
        }
    }
}