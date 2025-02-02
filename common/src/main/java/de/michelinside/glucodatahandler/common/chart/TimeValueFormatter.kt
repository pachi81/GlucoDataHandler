package de.michelinside.glucodatahandler.common.chart

import com.github.mikephil.charting.charts.LineChart
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.github.mikephil.charting.formatter.ValueFormatter

class TimeValueFormatter(private val mChart: LineChart) : ValueFormatter() {
    private val formatMinutes: SimpleDateFormat = SimpleDateFormat("HH:mm", Locale.ENGLISH)
    private val formatDays: SimpleDateFormat = SimpleDateFormat("MMM d", Locale.ENGLISH)

    private val c: Calendar = Calendar.getInstance()

    companion object {
        enum class DisplayedTimeUnit(// Number of minutes in this unit
            val factor: Float
        ) {
            MINUTES(1f),
            HOURS(60f),
            DAYS(60f * 24)
        }
        fun getUnit(min: Float, max: Float): DisplayedTimeUnit {
            // chart unit is minutes since custom epoch
            if (max <= min) {
                return DisplayedTimeUnit.MINUTES // arbitrary fallback
            }

            val range_hours = (max - min) / 60f
            return if (range_hours < 4) {
                // When chart range is less than 4 hours, allow nice
                // increments in minutes (e.g. 4:20, 4:40, 5:00)
                DisplayedTimeUnit.MINUTES
            } else if (range_hours < 24 * 4) {
                // When chart range is less than 4 days, allow nice
                // increments at even hour spacing
                DisplayedTimeUnit.HOURS
            } else {
                // When chart range is more than 4 days, show days
                DisplayedTimeUnit.DAYS
            }
        }

        fun to_chart_x(time: Long): Float {
            val dt: Long = time - baseTime
            return (dt / 1000).toFloat() / 60f
        }

        fun from_chart_x(x: Float): Long {
            val t = (x * 60).toLong() * 1000 + baseTime
            return t
        }

        val baseTime = base_time(System.currentTimeMillis())

        // base time used for calculate x axis time values
        private fun base_time(time: Long): Long {
            val calendar = Calendar.getInstance()
            calendar.time = Date(time)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            //calendar.set(2025, 1, 1, 0, 0, 0)
            return calendar.time.time
        }
    }


    override fun getFormattedValue(value: Float): String {
        val min = mChart.lowestVisibleX
        val max = mChart.highestVisibleX

        if (value < min) {
            return ""
        }

        // Values are formatted in order, lowest to highest.
        // Use this to determine if it's the first value

        val date = Date(from_chart_x(value))
        c.setTime(date)
        val hour: Float = c.get(Calendar.HOUR_OF_DAY).toFloat()
        val minute: Float = c.get(Calendar.MINUTE).toFloat()
        val isFirstHourOfDay = hour == 0F && minute <= 1F

        val unit = getUnit(min, max)

        return if (unit == DisplayedTimeUnit.MINUTES) {
            if (isFirstHourOfDay) {
                formatDays.format(date)
            } else {
                formatMinutes.format(date)
            }
        } else if (unit == DisplayedTimeUnit.HOURS) {
            if (isFirstHourOfDay) {
                formatDays.format(date)
            } else {
                formatMinutes.format(date)
            }
        } else {
            formatDays.format(date)
        }
    }
}