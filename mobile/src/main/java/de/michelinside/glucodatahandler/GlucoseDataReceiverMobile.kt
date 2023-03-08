package de.michelinside.glucodatahandler

import android.util.Log
import de.michelinside.glucodatahandler.common.*

class GlucoseDataReceiverMobile : GlucoseDataReceiver() {
    private val LOG_ID = "GlucoDataHandler.GlucoseDataReceiverMobile"
    init {
        Log.d(LOG_ID, "GlucoseDataReceiverMobile init called")
        ReceiveData.addNotifier(TaskerDataReceiver, mutableSetOf(ReceiveDataSource.BROADCAST,ReceiveDataSource.MESSAGECLIENT))
    }
}
