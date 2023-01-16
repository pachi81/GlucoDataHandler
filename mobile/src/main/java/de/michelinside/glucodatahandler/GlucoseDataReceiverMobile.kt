package de.michelinside.glucodatahandler

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import de.michelinside.glucodatahandler.common.*

class GlucoseDataReceiverMobile : GlucoseDataReceiver() {
    private val LOG_ID = "GlucoDataHandler.GlucoseDataReceiverMobile"
    init {
        Log.d(LOG_ID, "GlucoseDataReceiverMobile init called")
        ReceiveData.addNotifier(TaskerDataReceiver)
    }
}
