package de.michelinside.glucodatahandler.common.database
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Calendar

@Entity(tableName = "glucose_values")
class GlucoseValue (
    @PrimaryKey val timestamp: Long,
    var value: Int,
    var rate: Float? = null
)

@Entity(tableName = "daily_glucose_statistics")
data class DailyGlucoseStatistics(
    @PrimaryKey
    val dayStart: Long,
    val sampleCount: Long,
    val glucoseSum: Long,
    val glucoseSquaredSum: Long,
    val veryLowCount: Long,
    val lowCount: Long,
    val titrCount: Long,
    val aboveTitrCount: Long,
    val highCount: Long,
    val veryHighCount: Long,
    val customVeryLowCount: Long,
    val customLowCount: Long,
    val customInRangeCount: Long,
    val customHighCount: Long,
    val customVeryHighCount: Long
) {
    fun plus(other: DailyGlucoseStatistics): DailyGlucoseStatistics {
        return DailyGlucoseStatistics(
            dayStart = dayStart,
            sampleCount = sampleCount + other.sampleCount,
            glucoseSum = glucoseSum + other.glucoseSum,
            glucoseSquaredSum = glucoseSquaredSum + other.glucoseSquaredSum,
            veryLowCount = veryLowCount + other.veryLowCount,
            lowCount = lowCount + other.lowCount,
            titrCount = titrCount + other.titrCount,
            aboveTitrCount = aboveTitrCount + other.aboveTitrCount,
            highCount = highCount + other.highCount,
            veryHighCount = veryHighCount + other.veryHighCount,
            customVeryLowCount = customVeryLowCount + other.customVeryLowCount,
            customLowCount = customLowCount + other.customLowCount,
            customInRangeCount = customInRangeCount + other.customInRangeCount,
            customHighCount = customHighCount + other.customHighCount,
            customVeryHighCount = customVeryHighCount + other.customVeryHighCount
        )
    }

    companion object {
        fun fromValues(
            dayStart: Long,
            values: List<GlucoseValue>,
            lowValue: Int = 70,
            targetMinValue: Int = 90,
            targetMaxValue: Int = 165,
            highValue: Int = 240
        ): DailyGlucoseStatistics {
            return DailyGlucoseStatistics(
                dayStart = dayStart,
                sampleCount = values.size.toLong(),
                glucoseSum = values.sumOf { it.value.toLong() },
                glucoseSquaredSum = values.sumOf { it.value.toLong() * it.value },
                veryLowCount = values.count { it.value < 54 }.toLong(),
                lowCount = values.count { it.value in 54..69 }.toLong(),
                titrCount = values.count { it.value in 70..140 }.toLong(),
                aboveTitrCount = values.count { it.value in 141..180 }.toLong(),
                highCount = values.count { it.value in 181..250 }.toLong(),
                veryHighCount = values.count { it.value > 250 }.toLong(),
                customVeryLowCount = values.count { it.value <= lowValue }.toLong(),
                customLowCount = values.count { it.value > lowValue && it.value < targetMinValue }.toLong(),
                customInRangeCount = values.count { it.value in targetMinValue..targetMaxValue }.toLong(),
                customHighCount = values.count { it.value > targetMaxValue && it.value < highValue }.toLong(),
                customVeryHighCount = values.count { it.value >= highValue }.toLong()
            )
        }
    }
}

fun getGlucoseDayStart(timestamp: Long, daysAgo: Int = 0): Long {
    return Calendar.getInstance().apply {
        timeInMillis = timestamp
        add(Calendar.DAY_OF_YEAR, -daysAgo)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}