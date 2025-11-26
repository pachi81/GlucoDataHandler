package de.michelinside.glucodatahandler.common

import android.content.Context
import android.os.Handler
import de.michelinside.glucodatahandler.common.utils.Log
import de.michelinside.glucodatahandler.common.notifier.DataSource
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifySource

enum class SourceState(val resId: Int) {
    NONE(R.string.empty_string),
    CONNECTED(R.string.connected_label),
    NO_NEW_VALUE(R.string.source_state_no_new_value),
    NO_CONNECTION(R.string.source_state_no_connection),
    MULTI_PATIENT(R.string.source_select_patient),
    ERROR(R.string.source_state_error);
}
object SourceStateData {
    private val LOG_ID = "GDH.SourceStateData"

    var lastSource: DataSource = DataSource.NONE
    var lastState: SourceState = SourceState.NONE
    var lastError: String = ""
    var lastErrorInfo: String = ""
    var lastStateTime = 0L

    fun setError(source: DataSource, error: String) {
        setState( source, SourceState.ERROR, error)
    }

    fun setState(source: DataSource, state: SourceState, error: String = "", errorInfo: String = "") {
        lastError = error
        lastSource = source
        lastState = state
        lastStateTime = System.currentTimeMillis()
        lastErrorInfo = errorInfo

        if(state == SourceState.ERROR) {
            Log.e(LOG_ID, "Error state for source $source: $error - info: $errorInfo" )
        } else {
            Log.i(LOG_ID, "Set state $state for source $source: $error")
        }

        if (GlucoDataService.context != null) {
            Handler(GlucoDataService.context!!.mainLooper).post {
                try {
                    InternalNotifier.notify(
                        GlucoDataService.context!!,
                        NotifySource.SOURCE_STATE_CHANGE,
                        null
                    )
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "setState exception: " + exc.toString())
                }
            }
        }
    }

    fun getStateMessage(context: Context): String {
        if (lastState != SourceState.NONE && lastError.isNotEmpty()) {
            return lastError
        }
        return context.getString(lastState.resId)
    }

    fun reset() {
        lastSource = DataSource.NONE
        lastState = SourceState.NONE
        lastError = ""
        lastErrorInfo = ""
        lastStateTime = 0
    }
    /*
    fun getState(context: Context): String {
        if (lastState == SourceState.NONE)
            return ""
        return "%s: %s".format(context.getString(lastSource.resId), getStateMessage(context))
    }*/
}