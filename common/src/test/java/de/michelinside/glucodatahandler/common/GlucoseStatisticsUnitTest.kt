package de.michelinside.glucodatahandler.common

import de.michelinside.glucodatahandler.common.database.DailyGlucoseStatistics
import de.michelinside.glucodatahandler.common.database.GlucoseValue
import de.michelinside.glucodatahandler.common.database.getDailyStatisticsRetentionStart
import de.michelinside.glucodatahandler.common.database.getGlucoseDayStart
import de.michelinside.glucodatahandler.common.utils.GlucoseStatistics
import de.michelinside.glucodatahandler.common.utils.calculateGlucoseVariabilityPercent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlucoseStatisticsUnitTest {

    @Test
    fun calculatesCoefficientOfVariationFromAggregates() {
        val average = 154.0
        val standardDeviation = 46.0
        val averageSquared = average * average + standardDeviation * standardDeviation

        assertEquals(standardDeviation / average * 100.0, calculateGlucoseVariabilityPercent(average, averageSquared).toDouble(), 0.001)
    }

    @Test
    fun returnsNaNWhenAverageIsNotPositiveOrAggregatesAreMissing() {
        assertTrue(calculateGlucoseVariabilityPercent(null, 1.0).isNaN())
        assertTrue(calculateGlucoseVariabilityPercent(0.0, 1.0).isNaN())
        assertTrue(calculateGlucoseVariabilityPercent(100.0, null).isNaN())
    }

    @Test
    fun clampsSmallNegativeVarianceToZero() {
        assertEquals(0.0, calculateGlucoseVariabilityPercent(100.0, 9999.0).toDouble(), 0.0)
    }

    @Test
    fun aggregatesDailyValuesIntoStatisticsAndFixedRanges() {
        val stats = DailyGlucoseStatistics.fromValues(
            1L,
            listOf(GlucoseValue(1L, 53), GlucoseValue(2L, 54), GlucoseValue(3L, 70),
                GlucoseValue(4L, 140), GlucoseValue(5L, 141), GlucoseValue(6L, 180),
                GlucoseValue(7L, 181), GlucoseValue(8L, 250), GlucoseValue(9L, 251))
        )

        assertEquals(9L, stats.sampleCount)
        assertEquals(1320L, stats.glucoseSum)
        assertEquals(240_768L, stats.glucoseSquaredSum)
        assertEquals(1L, stats.veryLowCount)
        assertEquals(1L, stats.lowCount)
        assertEquals(2L, stats.titrCount)
        assertEquals(2L, stats.aboveTitrCount)
        assertEquals(2L, stats.highCount)
        assertEquals(1L, stats.veryHighCount)
        assertEquals(3L, stats.customVeryLowCount)
        assertEquals(0L, stats.customLowCount)
        assertEquals(2L, stats.customInRangeCount)
        assertEquals(2L, stats.customHighCount)
        assertEquals(2L, stats.customVeryHighCount)
    }

    @Test
    fun retainsFullNinetyCompletedDaysForNinetyDayStatistics() {
        val now = 1_793_016_000_000L
        val ninetyDaysMinTime = getGlucoseDayStart(now, GlucoseStatistics.statData90d.days)
        val retentionCutoff = getDailyStatisticsRetentionStart(now, Constants.DB_MAX_STATISTICS_DAYS)

        assertEquals(ninetyDaysMinTime, retentionCutoff)
        val completedDaysRetained = (1..91).count { daysAgo ->
            getGlucoseDayStart(now, daysAgo) >= retentionCutoff
        }
        assertEquals(90, completedDaysRetained)
    }

    @Test
    fun resolvesActivePeriodBySteppingDownUntilDataIsAvailable() {
        assertEquals(1, GlucoseStatistics.resolveActivePeriodDays(90) { it == 1 })
        assertEquals(7, GlucoseStatistics.resolveActivePeriodDays(30) { it <= 7 })
        assertEquals(30, GlucoseStatistics.resolveActivePeriodDays(30) { it <= 30 })
        assertEquals(1, GlucoseStatistics.resolveActivePeriodDays(14) { false })
    }

    @Test
    fun stepsBetweenSupportedStatisticsPeriodsWithinBounds() {
        assertEquals(1, GlucoseStatistics.stepPeriodDays(1, -1))
        assertEquals(7, GlucoseStatistics.stepPeriodDays(1, 1))
        assertEquals(30, GlucoseStatistics.stepPeriodDays(7, 1))
        assertEquals(90, GlucoseStatistics.stepPeriodDays(30, 1))
        assertEquals(90, GlucoseStatistics.stepPeriodDays(90, 1))
        assertEquals(30, GlucoseStatistics.stepPeriodDays(90, -1))
    }
}
