package de.michelinside.glucodatahandler.preferences

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.util.Log
import androidx.preference.DialogPreference
import de.michelinside.glucodatahandler.R


class SelectReceiverPreference : DialogPreference {
    private val LOG_ID = "GlucoDataHandler.SelectReceiverPreference"
    private var receiverSet = HashSet<String>()
    constructor(context: Context?) : super(context!!)  {
        initPreference()
    }
    constructor(context: Context?, attrs: AttributeSet?) : super(context!!, attrs) {
        initPreference()
    }
    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(
        context!!, attrs, defStyle
    ) {
        initPreference()
    }

    fun initPreference() {
        Log.d(LOG_ID, "initPreference called")
    }

    override fun getDialogLayoutResource(): Int {
        Log.d(LOG_ID, "getDialogLayoutResource called")
        return R.layout.fragment_select_receiver
    }

    fun getReceivers(): HashSet<String> {
        Log.d(LOG_ID, "getReceivers called")
        return receiverSet
    }

    fun setReceivers(receivers: HashSet<String>) {
        Log.d(LOG_ID, "setReceivers called for " + receivers.toString())
        try {
            receiverSet = receivers // Save to Shared Preferences
            persistStringSet(receiverSet)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "setReceivers exception: " + exc.toString())
        }
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any? {
        Log.d(LOG_ID, "onGetDefaultValue called for " + a.toString() + " with index " + index.toString())
        return HashSet<String>()
    }

    override fun onSetInitialValue(
        restorePersistedValue: Boolean,
        defaultValue: Any?
    ) {
        try {
            Log.d(LOG_ID, "onSetInitialValue called with restorePersistedValue " + restorePersistedValue.toString() + " - defaultValue " + defaultValue.toString())
            // Read the value. Use the default value if it is not possible.
            setReceivers(if (restorePersistedValue || defaultValue == null) getPersistedStringSet(receiverSet) as HashSet<String> else defaultValue as HashSet<String>)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSetInitialValue exception: " + exc.toString())
        }
    }
}