package de.michelinside.glucodatahandler.preferences

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.util.Log
import androidx.preference.DialogPreference
import de.michelinside.glucodatahandler.R


class TapActionPreference : DialogPreference {
    private val LOG_ID = "GDH.TapActionPreference"
    private var receiver = ""
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
        setReceiver("", false)
    }

    override fun getDialogLayoutResource(): Int {
        Log.d(LOG_ID, "getDialogLayoutResource called")
        return R.layout.fragment_select_receiver
    }

    fun getReceiver(): String {
        Log.d(LOG_ID, "getReceivers called")
        return receiver
    }

    fun setReceiver(newReceiver: String, save: Boolean = true) {
        Log.d(LOG_ID, "setReceiver called for $newReceiver - save: $save")
        try {
            receiver = newReceiver // Save to Shared Preferences
            if(save)
                persistString(receiver)
            this.summary = TapActionPreferenceDialogFragmentCompat.getSummary(context, receiver)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "setReceiver exception: " + exc.toString())
        }
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any? {
        Log.d(LOG_ID, "onGetDefaultValue called for " + a.toString() + " with index " + index.toString())
        return context.packageName
    }

    override fun onSetInitialValue(
        restorePersistedValue: Boolean,
        defaultValue: Any?
    ) {
        try {
            Log.d(LOG_ID, "onSetInitialValue called with restorePersistedValue " + restorePersistedValue.toString() + " - defaultValue " + defaultValue.toString())
            // Read the value. Use the default value if it is not possible.
            setReceiver(if (restorePersistedValue || defaultValue == null) getPersistedString(receiver) as String else defaultValue as String)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSetInitialValue exception: " + exc.toString())
        }
    }
}