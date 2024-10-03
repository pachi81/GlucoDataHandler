package de.michelinside.glucodatahandler.preferences

import android.content.Context
import android.util.AttributeSet
import com.takisoft.preferencex.TimePickerPreference

class MyTimeTickerPreference : TimePickerPreference {

    constructor(context: Context?) : super(context!!)
    constructor(context: Context?, attrs: AttributeSet?) : super(context!!, attrs) {
    }
    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(
        context!!, attrs, defStyle
    ) {
    }

    override fun setKey(key: String?) {
        super.setKey(key)
        onSetInitialValue(null)
    }

    fun getTimeString(): String = getPersistedString("")
}