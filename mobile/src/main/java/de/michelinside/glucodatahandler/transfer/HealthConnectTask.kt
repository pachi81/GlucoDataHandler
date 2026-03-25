package de.michelinside.glucodatahandler.transfer

import android.content.Context
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.utils.Log
import de.michelinside.glucodatahandler.healthconnect.HealthConnectManager

class HealthConnectTask: TransferTask() {
    override val LOG_ID = "GDH.transfer.HealthConnectTask"
    override val enablePref = Constants.SHARED_PREF_SEND_TO_HEALTH_CONNECT
    override val intervalPref = Constants.SHARED_PREF_SEND_TO_HEALTH_CONNECT_INTERVAL

    override fun execute(context: Context): Boolean {
        Log.d(LOG_ID, "execute called")
        return HealthConnectManager.writeLastValues(context)
    }

    override fun enable() {
        Log.d(LOG_ID, "enable called")
        HealthConnectManager.enable()
    }

    override fun disable() {
        Log.d(LOG_ID, "disable called")
        HealthConnectManager.disable()
    }

}