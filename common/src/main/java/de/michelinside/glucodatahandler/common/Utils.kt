package de.michelinside.glucodatahandler.common

import java.math.RoundingMode

object Utils {
    fun round(value: Float, scale: Int): Float {
        return value.toBigDecimal().setScale( scale, RoundingMode.HALF_UP).toFloat()
    }

    fun rangeValue(value: Float, min: Float, max: Float): Float {
        if (value > max)
            return max
        if (value < min)
            return min
        return value
    }
}