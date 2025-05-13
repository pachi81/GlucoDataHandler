package de.michelinside.glucodatahandler.common.receiver

import android.content.Context
import android.content.Intent
import android.util.Log
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.SourceState
import de.michelinside.glucodatahandler.common.SourceStateData
import de.michelinside.glucodatahandler.common.notifier.DataSource


open class GlucoseDataReceiver: NamedBroadcastReceiver() {
    private val LOG_ID = "GDH.GlucoseDataReceiver"
    override fun getName(): String {
        return LOG_ID
    }

    override fun onReceiveData(context: Context, intent: Intent) {
        try {
            val action = intent.action
            if (action != Constants.GLUCODATA_BROADCAST_ACTION) {
                Log.e(LOG_ID, "action=" + action + " != " + Constants.GLUCODATA_BROADCAST_ACTION)
                return
            }

            if (intent.extras == null) {
                Log.e(LOG_ID, "No extras in intent!")
                return
            }

            if (intent.extras!!.containsKey(Constants.EXTRA_SOURCE_PACKAGE)) {
                val packageSource = intent.extras!!.getString(Constants.EXTRA_SOURCE_PACKAGE, "")
                Log.d(LOG_ID, "Intent received from " + packageSource)
                if (packageSource == context.packageName) {
                    Log.d(LOG_ID, "Ignore received intent from itself!")
                    return
                }
            }

            ReceiveData.handleIntent(context, DataSource.JUGGLUCO, intent.extras)
            SourceStateData.setState(DataSource.JUGGLUCO, SourceState.NONE)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Receive exception: " + exc.message.toString() )
            SourceStateData.setError(DataSource.JUGGLUCO, exc.message.toString())
        }
    }
}