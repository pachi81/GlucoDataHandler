package de.michelinside.glucodatahandler.transfer

import android.content.Context
import de.michelinside.glucodatahandler.android_auto.CarModeReceiver
import de.michelinside.glucodatahandler.common.Constants

class GlucoDataAuto: TransferTask() {
    override val LOG_ID = "GDH.transfer.GlucoDataAuto"
    override val enablePref = Constants.SHARED_PREF_SEND_TO_GLUCODATAAUTO
    override val intervalPref = ""
    override val defaultInterval = 1

    override fun execute(context: Context): Boolean {
        CarModeReceiver.sendToGlucoDataAuto(context)
        return true
    }
}