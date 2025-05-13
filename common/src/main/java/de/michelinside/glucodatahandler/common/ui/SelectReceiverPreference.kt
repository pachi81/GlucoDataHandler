package de.michelinside.glucodatahandler.common.ui

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.util.Log
import androidx.preference.DialogPreference
import de.michelinside.glucodatahandler.common.R


class SelectReceiverPreference : DialogPreference {
    private val LOG_ID = "GDH.SelectReceiverPreference"
    private var receiver = ""
    var isTapAction = false
    var description = ""
    private var defaultSummary = ""
    constructor(context: Context?) : super(context!!)  {
        initPreference(null)
    }
    constructor(context: Context?, attrs: AttributeSet?) : super(context!!, attrs) {
        initPreference(attrs)
    }
    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(
        context!!, attrs, defStyle
    ) {
        initPreference(attrs)
    }

    fun initPreference(attrs: AttributeSet?) {
        Log.d(LOG_ID, "initPreference called")
        defaultSummary = this.summary.toString()
        setReceiver("", false)
        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.SelectReceiverPreference)
            isTapAction = typedArray.getBoolean(R.styleable.SelectReceiverPreference_isTapAction, false)
            description = typedArray.getString(R.styleable.SelectReceiverPreference_description)?: ""
            typedArray.recycle()
            Log.d(LOG_ID, "isTapAction: $isTapAction, description: $description")
        }
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
            this.summary = SelectReceiverPreferenceDialogFragmentCompat.getSummary(context, receiver, defaultSummary, isTapAction)
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