package de.michelinside.glucodatahandler.common.tasks

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import de.michelinside.glucodatahandler.common.Constants

class LibreViewSourceTask : DataSourceTask(Constants.SHARED_PREF_LIBRE_ENABLED) {
    private val LOG_ID = "GlucoDataHandler.Task.LibreViewSourceTask"
    override fun executeRequest() {
        Log.e(LOG_ID, "getting data from libre view")
    }


    override fun checkPreferenceChanged(sharedPreferences: SharedPreferences, key: String?, context: Context): Boolean {

        return super.checkPreferenceChanged(sharedPreferences, key, context)
    }
}