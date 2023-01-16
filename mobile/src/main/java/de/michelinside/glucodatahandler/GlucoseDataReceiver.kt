package de.michelinside.glucodatahandler

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.joaomgcd.taskerpluginlibrary.extensions.requestQuery
import de.michelinside.glucodatahandler.common.GlucoseDataReceiverBase

class GlucoseDataReceiver : GlucoseDataReceiverBase() {
    private val LOG_ID = "GlucoDataHandler.Receiver.Mobile"
    override fun notify(context: Context, extras: Bundle?)
    {
        Log.d(LOG_ID, "sending new intent to tasker")
        GlucodataEvent::class.java.requestQuery(context, GlucodataValues() )
        super.notify(context, extras)
    }
}
