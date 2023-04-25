package de.michelinside.glucodatahandler

import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import android.util.Log
import android.widget.EditText
import androidx.preference.EditTextPreference
import androidx.preference.EditTextPreference.OnBindEditTextListener
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.Utils

class GlucoseEditPreference : EditTextPreference, OnBindEditTextListener {
    private val LOG_ID = "GlucoDataHandler.GlucoseEditPreference"
    constructor(context: Context?) : super(context!!)  {
        initDialog()
    }
    constructor(context: Context?, attrs: AttributeSet?) : super(context!!, attrs) {
        initDialog()
    }
    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(
        context!!, attrs, defStyle
    ) {
        initDialog()
    }

    fun initDialog() {
        setOnBindEditTextListener(this)
    }

    override fun persistFloat(value: Float): Boolean {
        Log.i(LOG_ID, "persistFloat called with value " + value)
        return super.persistFloat(value)
    }

    override fun getPersistedString(defaultReturnValue: String?): String {
        Log.i(LOG_ID, "getPersistedString called")
        return getPersistedFloat(0.0F).toString()
    }

    override fun persistString(value: String?): Boolean {
        Log.i(LOG_ID, "persistString called with value " + value)
        if (Utils.isMmolValue(value!!.toFloat()))
            return persistFloat(Utils.mmolToMg(value.toFloat()))
        return persistFloat(value.toFloat())
    }

    override fun onBindEditText(editText: EditText) {
        Log.i(LOG_ID, "onBindEditText called " + editText.text)
        editText.inputType = if (ReceiveData.isMmol) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL else InputType.TYPE_CLASS_NUMBER
        var value = editText.getText().toString().toFloat()
        if (!ReceiveData.isMmol) {
            if(Utils.isMmolValue(value))
                value = Utils.mmolToMg(value)
            editText.setText(value.toInt().toString())
        } else if (ReceiveData.isMmol && !Utils.isMmolValue(value)) {
            editText.setText(Utils.mgToMmol(value).toString())
        }
        Log.i(LOG_ID, "onBindEditText new text: " + editText.text)
    }
}