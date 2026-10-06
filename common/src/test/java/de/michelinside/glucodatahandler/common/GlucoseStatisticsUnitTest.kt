package de.michelinside.glucodatahandler.common

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
}
