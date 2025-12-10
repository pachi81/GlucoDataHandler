package de.michelinside.glucodatahandler.common.ui

import android.content.Context
import android.content.res.TypedArray
import android.text.InputType
import android.util.AttributeSet
import de.michelinside.glucodatahandler.common.utils.Log
import android.widget.EditText
import androidx.preference.EditTextPreference
import androidx.preference.EditTextPreference.OnBindEditTextListener
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.utils.GlucoDataUtils
import kotlin.math.abs
import de.michelinside.glucodatahandler.common.R

@Suppress("unused")
class GlucoseEditPreference : EditTextPreference, OnBindEditTextListener {
    companion object {
        private val LOG_ID = "GDH.GlucoseEditPreference"
    }

    var isNegative = false
    private var defaultValue = 0F
    private var isDelta = false
    private var fromDialog = false
    constructor(context: Context?) : super(context!!)  {
        initDialog(null)
    }
    constructor(context: Context?, attrs: AttributeSet?) : super(context!!, attrs) {
        initDialog(attrs)
    }
    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(
        context!!, attrs, defStyle
    ) {
        initDialog(attrs)
    }

    fun initDialog(attrs: AttributeSet?) {
        try {
            Log.d(LOG_ID, "initDialog called")
            setOnBindEditTextListener(this)
            if (attrs != null) {
                val typedArray = context.obtainStyledAttributes(attrs, R.styleable.GlucoseEditPreference)
                isDelta = typedArray.getBoolean(R.styleable.GlucoseEditPreference_isDelta, false)
                typedArray.recycle()
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "initDialog exception: " + exc.toString())
        }
    }

    private fun getPersistValue(value: String): Float {
        try {
            var result = value.toFloat()
            if (fromDialog) {
                if ((isDelta && ReceiveData.isMmol) || (!isDelta && GlucoDataUtils.isMmolValue(value.toFloat()))) {
                    result = GlucoDataUtils.mmolToMg(result)
                }
            }
            Log.v(LOG_ID, "$value -> persistValue = $result - fromDialog: $fromDialog")
            return result
        } catch (exc: Exception) {
            Log.e(LOG_ID, "getPersistValue exception: " + exc.toString())
        }
        Log.v(LOG_ID, "Returning default value $defaultValue")
        return defaultValue
    }

    private fun getDisplayValue(curText: String): String {
        var curDisplayValue = ""
        try {
            var value = curText.toFloat()
            if (isNegative && value > 0)
                value = -value
            if (!ReceiveData.isMmol && !isDelta) {
                if(GlucoDataUtils.isMmolValue(value))
                    value = GlucoDataUtils.mmolToMg(value)
                curDisplayValue = value.toInt().toString()
            } else if (ReceiveData.isMmol && (!GlucoDataUtils.isMmolValue(value) || isDelta)) {
                curDisplayValue = GlucoDataUtils.mgToMmol(value).toString()
            } else {
                curDisplayValue = value.toString()
            }
            Log.v(LOG_ID, "$curText -> display value = $curDisplayValue")
        } catch (exc: Exception) {
            Log.e(LOG_ID, "getDisplayValue exception: " + exc.toString())
        }
        return curDisplayValue
    }

    override fun persistFloat(value: Float): Boolean {
        Log.d(LOG_ID, "persistFloat called with value " + abs(value))
        return super.persistFloat(abs(value))
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any? {
        Log.d(LOG_ID, "onGetDefaultValue called")
        val value = super.onGetDefaultValue(a, index)
        try {
            if (value != null)
                defaultValue = value.toString().toFloat()
            Log.d(LOG_ID, "Using default value: " + defaultValue)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onGetDefaultValue exception: " + exc.toString())
        }
        return value
    }

    override fun getPersistedString(defaultReturnValue: String?): String {
        Log.d(LOG_ID, "getPersistedString called")
        return getPersistedFloat(defaultValue).toString()
    }

    override fun persistString(value: String?): Boolean {
        Log.d(LOG_ID, "persistString called with value " + value)
        try {
            return persistFloat(value!!.toFloat())
        } catch (exc: Exception) {
            Log.e(LOG_ID, "persistString exception: " + exc.toString())
        }
        return false
    }

    override fun setText(text: String?) {
        Log.d(LOG_ID, "setText called " + text)
        super.setText(getPersistValue(text!!).toString())
    }

    override fun getSummary(): CharSequence? {
        Log.d(LOG_ID, "getSummary called")
        val value = getPersistedFloat(defaultValue)
        if (value.isNaN())
            return super.getSummary()
        val summary = super.getSummary().toString() + "\n"
        var unit = ReceiveData.getUnit()
        if (isDelta) {
            if (ReceiveData.use5minDelta) {
                unit += " " + context.getString(R.string.delta_per_5_minute)
            } else {
                unit += " " + context.getString(R.string.delta_per_minute)
            }
        }

        val sign = if(isDelta && !isNegative) "+" else ""
        return summary + sign + getDisplayValue(value.toString()) + " " + unit
    }

    override fun onBindEditText(editText: EditText) {
        Log.d(LOG_ID, "onBindEditText called " + editText.text)
        try {
            fromDialog = true
            editText.inputType = if (ReceiveData.isMmol || isDelta) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL else InputType.TYPE_CLASS_NUMBER
            if(isNegative)
                editText.inputType = editText.inputType or InputType.TYPE_NUMBER_FLAG_SIGNED
            editText.setText(getDisplayValue(editText.text.toString()))
            Log.i(LOG_ID, "onBindEditText new text: " + editText.text)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onBindEditText exception: " + exc.toString())
        }
    }
}