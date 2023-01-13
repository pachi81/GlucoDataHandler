package de.michelinside.glucodatahandler

import android.content.Context
import android.util.Log
import com.joaomgcd.taskerpluginlibrary.extensions.requestQuery
import de.michelinside.glucodatahandler.common.GlucoseDataReceiverBase

class GlucoseDataReceiver : GlucoseDataReceiverBase() {
    private val LOG_ID = "GlucoDataHandler.Receiver.Mobile"
    override fun notify(context: Context)
    {
        Log.d(LOG_ID, "sending new intent to tasker")
        GlucodataEvent::class.java.requestQuery(context, GlucodataValues() )
        super.notify(context)
    }
}
