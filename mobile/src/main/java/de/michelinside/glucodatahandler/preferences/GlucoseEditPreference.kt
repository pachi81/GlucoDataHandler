package de.michelinside.glucodatahandler.preferences

import android.content.Context
import android.content.res.TypedArray
import android.text.InputType
import android.util.AttributeSet
import android.util.Log
import android.widget.EditText
import androidx.preference.EditTextPreference
import androidx.preference.EditTextPreference.OnBindEditTextListener
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.Utils

class GlucoseEditPreference : EditTextPreference, OnBindEditTextListener {
    companion object {
        private val LOG_ID = "GlucoDataHandler.GlucoseEditPreference"
    }
    private var defaultValue = 0F
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
        Log.d(LOG_ID, "initDialog called")
        setOnBindEditTextListener(this)
    }

    override fun persistFloat(value: Float): Boolean {
        Log.i(LOG_ID, "persistFloat called with value " + value)
        return super.persistFloat(value)
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any? {
        Log.d(LOG_ID, "onGetDefaultValue called")
        val value = super.onGetDefaultValue(a, index)
        if (value != null)
            defaultValue = value.toString().toFloat()
        Log.d(LOG_ID, "Using default value: " + defaultValue)
        return value
    }

    override fun getPersistedString(defaultReturnValue: String?): String {
        Log.i(LOG_ID, "getPersistedString called")
        return getPersistedFloat(defaultValue).toString()
    }

    override fun persistString(value: String?): Boolean {
        Log.i(LOG_ID, "persistString called with value " + value)
        try {
            if (Utils.isMmolValue(value!!.toFloat()))
                return persistFloat(Utils.mmolToMg(value.toFloat()))
            return persistFloat(value.toFloat())
        } catch (exc: Exception) {
            Log.e(LOG_ID, "persistString exception: " + exc.toString())
        }
        return false
    }

    override fun onBindEditText(editText: EditText) {
        Log.i(LOG_ID, "onBindEditText called " + editText.text)
        try {
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
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onBindEditText exception: " + exc.toString())
        }
    }
}