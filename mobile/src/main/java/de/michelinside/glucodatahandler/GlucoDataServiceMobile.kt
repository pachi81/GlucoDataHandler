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
        //ReceiveData.addNotifier(this)  not working yet...
    }

    override fun OnReceiveData(context: Context, dataSource: ReceiveDataSource, extras: Bundle?) {
        try {
            if (dataSource == ReceiveDataSource.MESSAGECLIENT && extras != null)
            {
                Log.d(LOG_ID, "Resend Glucodata Broadcast")
                val intent = Intent()
                intent.action = Constants.GLUCODATA_BROADCAST_ACTION
                intent.putExtras(extras!!)
                context.sendBroadcast(intent)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "OnReceiveData exception: " + exc.message.toString() )
        }
    }
}