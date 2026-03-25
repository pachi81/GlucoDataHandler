package de.michelinside.glucodatahandler.transfer

import android.content.Context
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.GlucoDataService
import de.michelinside.glucodatahandler.common.utils.Log

class NightscoutUploadTask: TransferTask() {
    override val LOG_ID = "GDH.transfer.NightscoutUploadTask"
    override val enablePref = Constants.SHARED_PREF_NIGHTSCOUT_UPLOAD_ENABLED
    override val intervalPref = Constants.SHARED_PREF_NIGHTSCOUT_UPLOAD_INTERVAL

    override fun execute(context: Context): Boolean {
        Log.d(LOG_ID, "execute called")
        return NightscoutUploader.uploadValues(context)
    }

    override fun enable() {
        Log.d(LOG_ID, "enable called")
        NightscoutUploader.enable(GlucoDataService.context!!)
    }

    override fun disable() {
        Log.d(LOG_ID, "disable called")
        NightscoutUploader.disable(GlucoDataService.context!!)
    }

}