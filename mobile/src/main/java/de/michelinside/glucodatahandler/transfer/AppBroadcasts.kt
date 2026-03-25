package de.michelinside.glucodatahandler.transfer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.utils.Log
import de.michelinside.glucodatahandler.common.utils.Utils

abstract class AppBroadcasts : TransferTask() {
    override val intervalPref = Constants.SHARED_PREF_SEND_TO_RECEIVER_INTERVAL
    override val defaultInterval = 1
    abstract val receiverPrefKey: String

    abstract fun getIntent(context: Context): Intent?

    override fun execute(context: Context): Boolean {
        val intent = getIntent(context)
        if(intent != null) {
            return sendBroadcast(context, intent)
        }
        return false
    }

    private fun sendBroadcast(context: Context, intent: Intent): Boolean {
        try {
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            var receivers = GlucoDataService.sharedPref!!.getStringSet(receiverPrefKey, HashSet<String>())
            Log.i(LOG_ID, "Forward " + receiverPrefKey + " Broadcast to " + receivers?.size.toString() + " receivers")
            if(Log.isLoggable(LOG_ID, android.util.Log.DEBUG))
                Log.d(LOG_ID, "Forward package: ${Utils.dumpBundle(intent.extras)}")
            if (receivers == null || receivers.size == 0) {
                receivers = setOf("")
            }
            for( receiver in receivers ) {
                val sendIntent = intent.clone() as Intent
                if (!receiver.isEmpty()) {
                    sendIntent.setPackage(receiver)
                    Log.d(LOG_ID, "Send broadcast " + receiverPrefKey + " to " + receiver.toString())
                } else {
                    Log.d(LOG_ID, "Send global broadcast " + receiverPrefKey)
                    sendIntent.putExtra(Constants.EXTRA_SOURCE_PACKAGE, context.packageName)
                }
                context.sendBroadcast(sendIntent)
            }
            return true
        } catch (ex: Exception) {
            Log.e(LOG_ID, "Exception while sending broadcast for " + receiverPrefKey + ": " + ex)
        }
        return false
    }

}