package de.michelinside.glucodatahandler.transfer

import android.content.Context
import android.content.Intent
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.ReceiveData

class GlucoDataBroadcast: AppBroadcasts() {
    override val LOG_ID = "GDH.transfer.GlucoDataBroadcast"
    override val receiverPrefKey = Constants.SHARED_PREF_GLUCODATA_RECEIVERS
    override val enablePref = Constants.SHARED_PREF_SEND_TO_GLUCODATA_AOD

    override fun getIntent(context: Context): Intent? {
        val extras = ReceiveData.createExtras()
        if (extras != null) {
            val intent = Intent(Constants.GLUCODATA_BROADCAST_ACTION)
            intent.putExtras(extras)
            return intent
        }
        return null
    }
}