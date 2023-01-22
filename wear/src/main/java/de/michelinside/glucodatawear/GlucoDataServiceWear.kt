package de.michelinside.glucodatahandler

import android.util.Log
import de.michelinside.glucodatahandler.ActiveComplicationHandler
import de.michelinside.glucodatahandler.common.*

class GlucoDataServiceWear: GlucoDataService() {
    private val LOG_ID = "GlucoDataHandler.GlucoDataServiceWear"
    init {
        Log.d(LOG_ID, "init called")
        ReceiveData.addNotifier(ActiveComplicationHandler)
    }
}