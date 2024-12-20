package de.michelinside.glucodatahandler.common.chart

import android.util.Log
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.renderer.XAxisRenderer
import com.github.mikephil.charting.utils.Utils
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
class TimeAxisRenderer(chart: LineChart) :
    XAxisRenderer(chart.viewPortHandler, chart.xAxis, chart.rendererXAxis.transformer) {
    private val LOG_ID = "GDH.Chart.TimeAxisRenderer"


    override fun computeAxisValues(min: Float, max: Float) {
        computeNiceAxisValues(min, max)
        super.computeSize()
    }

    // Custom method for calculating a "nice" interval at the
    // desired display time unit
    private fun calculateInterval(min: Float, max: Float, count: Int): Float {
        val range = (max - min).toDouble()

        val unit = TimeValueFormatter.getUnit(min, max)
        val axisScale = unit.factor

        //Log.d(LOG_ID, "Calculate interval: $unit - scale: $axisScale - min: $min - max: $max - range: $range - count: $count")

        // Find out how much spacing (in y value space) between axis values
        val rawInterval = range / count / axisScale
        var interval: Float = Utils.roundToNextSignificant(rawInterval)
        // If granularity is enabled, then do not allow the interval to go below specified granularity.
        // This is used to avoid repeated values when rounding values for display.
        if (mAxis.isGranularityEnabled) {
            val gran = mAxis.granularity * axisScale
            Log.d(LOG_ID, "Calculate interval: $interval - gran: $gran")
            interval = if (interval < gran) gran else interval
        }

        // Normalize interval
        val intervalMagnitude: Float =
            Utils.roundToNextSignificant(10.0.pow(log10(interval).toInt().toDouble()))
        val intervalSigDigit = (interval / intervalMagnitude).toInt()
        if (intervalSigDigit > 5) {
            // Use one order of magnitude higher, to avoid intervals like 0.9 or
            // 90
            interval = floor(10 * intervalMagnitude)
        }

        interval *= axisScale
        //Log.d(LOG_ID, "Calculate interval: $interval - scale: $axisScale - intervalMagnitude: $intervalMagnitude - intervalSigDigit: $intervalSigDigit")
        return (interval/10).toInt()*10F
    }

    private fun computeNiceAxisValues(xMin: Float, xMax: Float) {
        val labelCount = mAxis.labelCount
        val range = (xMax - xMin).toDouble()

        if (labelCount == 0 || range <= 0 || java.lang.Double.isInfinite(range)) {
            mAxis.mEntries = floatArrayOf()
            mAxis.mCenteredEntries = floatArrayOf()
            mAxis.mEntryCount = 0
            return
        }

        // == Use a time-aware interval calculation
        var interval = calculateInterval(xMin, xMax, labelCount)

        // == Below here copied from AxisRenderer::computeAxisValues unchanged ============
        var n = if (mAxis.isCenterAxisLabelsEnabled) 1 else 0

        // force label count
        if (mAxis.isForceLabelsEnabled) {
            interval = (range.toFloat() / (labelCount - 1).toFloat())
            mAxis.mEntryCount = labelCount

            if (mAxis.mEntries.size < labelCount) {
                // Ensure stops contains at least numStops elements.
                mAxis.mEntries = FloatArray(labelCount)
            }

            var v = xMin

            for (i in 0..<labelCount) {
                mAxis.mEntries[i] = v
                v += interval
            }

            n = labelCount

            // no forced count
        } else {
            var first = if (interval == 0F) 0F else ceil(xMin / interval) * interval
            if (mAxis.isCenterAxisLabelsEnabled) {
                first -= interval
            }

            val last = if (interval == 0F) 0F else Utils.nextUp(floor(xMax.toDouble() / interval) * interval).toFloat()
            var f: Float

            if (interval != 0F) {
                f = first
                while (f <= last) {
                    ++n
                    f += interval
                }
            }

            mAxis.mEntryCount = n

            //Log.v(LOG_ID, "Compute nice axis values: $n - $first - $last - $interval")

            if (mAxis.mEntries.size < n) {
                // Ensure stops contains at least numStops elements.
                mAxis.mEntries = FloatArray(n)
            }

            f = first
            var i = 0
            while (i < n) {
                if (f == 0F)  // Fix for negative zero case (Where value == -0F, and 0F == -0F)
                    f = 0F
                mAxis.mEntries[i] = f
                f += interval
                ++i
            }
        }

        // set decimals
        if (interval < 1) {
            mAxis.mDecimals = ceil(-log10(interval)).toInt()
        } else {
            mAxis.mDecimals = 0
        }

        if (mAxis.isCenterAxisLabelsEnabled) {
            if (mAxis.mCenteredEntries.size < n) {
                mAxis.mCenteredEntries = FloatArray(n)
            }

            val offset = interval / 2f

            for (i in 0..<n) {
                mAxis.mCenteredEntries[i] = mAxis.mEntries[i] + offset
            }
        }
    }
}