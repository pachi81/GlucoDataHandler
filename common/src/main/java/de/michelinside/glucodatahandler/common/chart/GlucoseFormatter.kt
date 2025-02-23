package de.michelinside.glucodatahandler.common.chart

import com.github.mikephil.charting.formatter.ValueFormatter
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.utils.GlucoDataUtils

class GlucoseFormatter(val inverted: Boolean = false): ValueFormatter() {
    companion object {
        fun getValueAsString(value: Float, inverted: Boolean = false): String {
            if((!inverted && ReceiveData.isMmol) || (inverted && !ReceiveData.isMmol))
                return GlucoDataUtils.mgToMmol(value).toString()
            return value.toInt().toString()
        }
    }
    override fun getFormattedValue(value: Float): String {
        return getValueAsString(value, inverted)
    }
}